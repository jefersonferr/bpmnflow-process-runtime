package org.bpmnflow.runtime.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.WorkflowEngine;
import org.bpmnflow.WorkflowEngineImpl;
import org.bpmnflow.model.ActivityNode;
import org.bpmnflow.model.Inconsistency;
import org.bpmnflow.model.Stage;
import org.bpmnflow.model.Workflow;
import org.bpmnflow.model.WorkflowRule;
import org.bpmnflow.parser.ConfigLoader;
import org.bpmnflow.parser.ModelParser;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class BpmnDeployService {

    // ---- Namespaces ----
    private static final String NS_BPMN    = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String NS_CAMUNDA = "http://camunda.org/schema/1.0/bpmn";

    // ---- Repositories ----
    private final BpmnConfigRepository            configRepo;
    private final BpmnProcessRepository           processRepo;
    private final BpmnProcessVersionRepository    versionRepo;
    private final BpmnParticipantRepository       participantRepo;
    private final BpmnLaneRepository              laneRepo;
    private final BpmnElementRepository           elementRepo;
    private final BpmnSequenceFlowRepository      sequenceFlowRepo;
    private final BpmnExtensionPropertyRepository extPropRepo;
    private final BpmnActivityRepository          activityRepo;
    private final BpmnRuleRepository              ruleRepo;

    private final AtomicReference<WorkflowEngine> engineRef;

    // =========================================================================
    // Entry point
    // =========================================================================

    @Transactional
    public DeployResult deploy(byte[] bpmnContent, byte[] configContent, String processKey) {

        String bpmnXml    = new String(bpmnContent, StandardCharsets.UTF_8);
        String configYaml = new String(configContent, StandardCharsets.UTF_8);

        // 1. Parse BPMNFlow (derived data — Layer 4)
        Workflow workflow;
        try (InputStream modelStream = new ByteArrayInputStream(bpmnContent);
             InputStream cfgStream   = new ByteArrayInputStream(configContent)) {
            var config = ConfigLoader.loadConfig(cfgStream);
            workflow = ModelParser.parser(modelStream, config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse BPMN model: " + e.getMessage(), e);
        }

        // 2. Parse DOM (structural elements — Layer 3)
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bpmnContent));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse BPMN XML (DOM): " + e.getMessage(), e);
        }

        // 3. Update in-memory engine
        engineRef.set(new WorkflowEngineImpl(workflow));

        // 4. Config
        BpmnConfigEntity configEntity = persistConfig(configYaml);

        // 5. Process
        String key = processKey != null ? processKey : workflow.getId();
        BpmnProcessEntity processEntity = processRepo.findByProcessKeyForUpdate(key)
                .orElseGet(() -> processRepo.save(BpmnProcessEntity.builder()
                        .processKey(key)
                        .name(workflow.getName() != null ? workflow.getName() : key)
                        .build()));

        // 6. New version
        int nextVersion = versionRepo.findMaxVersionNumber(processEntity.getProcessId()) + 1;
        BpmnProcessVersionEntity version = versionRepo.save(BpmnProcessVersionEntity.builder()
                .process(processEntity)
                .config(configEntity)
                .versionNumber(nextVersion)
                .versionTag(workflow.getVersion())
                .status("ACTIVE")
                .processType(workflow.getType())
                .processSubtype(workflow.getSubtype())
                .documentation(workflow.getDocumentation())
                .bpmnXml(bpmnXml)
                .valid(workflow.getInconsistencies().isEmpty())
                .build());

        // ==== Layer 3: Structural ====

        // 7. Extension properties of <bpmn:process>
        persistProcessExtensionProperties(version, doc);

        // 8. Participants + Lanes (each saved individually to have ID before ext props)
        Map<String, BpmnParticipantEntity> participantMap = persistParticipants(version, doc);

        // 9. Map elementBpmnId → BpmnLaneEntity (via flowNodeRef)
        Map<String, BpmnLaneEntity> elementToLane = buildElementToLaneMap(version, doc);

        // 10. BPMN elements linked to lanes
        Map<String, BpmnElementEntity> elementMap = persistElements(version, doc, elementToLane);

        // 11. Sequence flows
        persistSequenceFlows(version, doc, elementMap);

        // ==== Layer 4: Derived ====

        // 12. Stages
        persistStages(version, workflow);

        // 13. Activities + conclusions + element_id link + resolved laneName
        Map<String, ProcessActivityEntity> activityMap = persistActivities(version, workflow, elementMap);

        // 14. Rules
        persistRules(version, workflow, activityMap);

        // 15. Inconsistencies
        persistInconsistencies(version, workflow);

        // Actual counts — not relying on lazy collections of the entity
        int laneCount          = laneRepo.findByVersion_VersionId(version.getVersionId()).size();
        int sequenceFlowCount  = sequenceFlowRepo.findByVersion_VersionId(version.getVersionId()).size();
        List<String> inconsistencyMessages = workflow.getInconsistencies().stream()
                .map(inc -> (inc.getType() != null ? String.valueOf(inc.getType()) : "UNKNOWN")
                        + ": " + inc.getDescription())
                .collect(java.util.stream.Collectors.toList());

        log.info("Deployed '{}' v{} | participants={} lanes={} elements={} flows={} | stages={} activities={} rules={} issues={}",
                key, nextVersion,
                participantMap.size(), laneCount, elementMap.size(), sequenceFlowCount,
                workflow.getStages() != null ? workflow.getStages().size() : 0,
                activityMap.size(), workflow.getRules().size(), workflow.getInconsistencies().size());

        return DeployResult.builder()
                .version(version)
                .participantCount(participantMap.size())
                .laneCount(laneCount)
                .elementCount(elementMap.size())
                .sequenceFlowCount(sequenceFlowCount)
                .stageCount(workflow.getStages() != null ? workflow.getStages().size() : 0)
                .activityCount(activityMap.size())
                .ruleCount(workflow.getRules().size())
                .inconsistencyCount(workflow.getInconsistencies().size())
                .inconsistencies(inconsistencyMessages)
                .build();
    }

    // =========================================================================
    // Layer 3 — Structural
    // =========================================================================

    private void persistProcessExtensionProperties(BpmnProcessVersionEntity version, Document doc) {
        NodeList processList = doc.getElementsByTagNameNS(NS_BPMN, "process");
        if (processList.getLength() == 0) return;
        persistExtensionProperties(version, "PROCESS", version.getVersionId(),
                (Element) processList.item(0));
    }

    /**
     * Persists each {@code <bpmn:participant>} and then all {@code <bpmn:lane>}
     * elements in the document, associating each lane with its correct participant
     * via the DOM tree (lane → laneSet → process → participant.processRef).
     *
     * <p>Each entity is saved individually — not via cascade — so the generated ID
     * is available before persisting extension properties.</p>
     */
    private Map<String, BpmnParticipantEntity> persistParticipants(
            BpmnProcessVersionEntity version, Document doc) {

        // 1. Participants
        Map<String, BpmnParticipantEntity> participantByBpmnId = new LinkedHashMap<>();
        Map<String, BpmnParticipantEntity> participantByProcessRef = new HashMap<>();

        NodeList participants = doc.getElementsByTagNameNS(NS_BPMN, "participant");
        for (int i = 0; i < participants.getLength(); i++) {
            Element el         = (Element) participants.item(i);
            String  bpmnId     = el.getAttribute("id");
            String  processRef = nullIfBlank(el.getAttribute("processRef"));

            BpmnParticipantEntity entity = participantRepo.save(
                    BpmnParticipantEntity.builder()
                            .version(version)
                            .bpmnId(bpmnId)
                            .name(nullIfBlank(el.getAttribute("name")))
                            .processRef(processRef)
                            .build());

            persistExtensionProperties(version, "PARTICIPANT", entity.getParticipantId(), el);
            participantByBpmnId.put(bpmnId, entity);
            if (processRef != null) participantByProcessRef.put(processRef, entity);
        }

        // 2. Lanes
        AtomicInteger order = new AtomicInteger(1);
        NodeList lanes = doc.getElementsByTagNameNS(NS_BPMN, "lane");
        for (int i = 0; i < lanes.getLength(); i++) {
            Element laneEl = (Element) lanes.item(i);
            String  bpmnId = laneEl.getAttribute("id");

            // Resolve participant by walking the DOM tree: lane → laneSet → process
            BpmnParticipantEntity participant = resolveParticipantForLane(
                    laneEl, participantByProcessRef, participantByBpmnId);

            BpmnLaneEntity lane = laneRepo.save(BpmnLaneEntity.builder()
                    .version(version)
                    .participant(participant)
                    .bpmnId(bpmnId)
                    .name(nullIfBlank(laneEl.getAttribute("name")))
                    .displayOrder(order.getAndIncrement())
                    .build());

            persistExtensionProperties(version, "LANE", lane.getLaneId(), laneEl);
        }

        return participantByBpmnId;
    }

    /**
     * Walks up the DOM tree from the lane to find the parent {@code <bpmn:process>}
     * and resolves the participant via {@code processRef}.
     * Falls back to the first available participant if resolution fails.
     */
    private BpmnParticipantEntity resolveParticipantForLane(
            Element laneEl,
            Map<String, BpmnParticipantEntity> byProcessRef,
            Map<String, BpmnParticipantEntity> byBpmnId) {

        // lane → laneSet → process
        Node laneSet = laneEl.getParentNode();
        if (laneSet != null) {
            Node process = laneSet.getParentNode();
            if (process instanceof Element processEl) {
                String processId = processEl.getAttribute("id");
                BpmnParticipantEntity found = byProcessRef.get(processId);
                if (found != null) return found;
            }
        }
        // Fallback: first available participant
        return byBpmnId.values().iterator().next();
    }

    /**
     * Builds a map of elementBpmnId → BpmnLaneEntity by scanning
     * the {@code <flowNodeRef>} children of each lane already persisted.
     */
    private Map<String, BpmnLaneEntity> buildElementToLaneMap(
            BpmnProcessVersionEntity version, Document doc) {

        List<BpmnLaneEntity> savedLanes = laneRepo.findByVersion_VersionId(version.getVersionId());
        Map<String, BpmnLaneEntity> laneByBpmnId = new HashMap<>();
        for (BpmnLaneEntity l : savedLanes) laneByBpmnId.put(l.getBpmnId(), l);

        Map<String, BpmnLaneEntity> elementToLane = new HashMap<>();
        NodeList lanes = doc.getElementsByTagNameNS(NS_BPMN, "lane");
        for (int i = 0; i < lanes.getLength(); i++) {
            Element     laneEl     = (Element) lanes.item(i);
            BpmnLaneEntity laneEntity = laneByBpmnId.get(laneEl.getAttribute("id"));
            if (laneEntity == null) continue;

            NodeList refs = laneEl.getElementsByTagNameNS(NS_BPMN, "flowNodeRef");
            for (int j = 0; j < refs.getLength(); j++) {
                String elemBpmnId = refs.item(j).getTextContent().trim();
                if (!elemBpmnId.isBlank()) elementToLane.put(elemBpmnId, laneEntity);
            }
        }
        return elementToLane;
    }

    /**
     * Persists all BPMN elements.
     * Skips elements already processed (via map, to avoid duplicates in subProcesses).
     */
    private Map<String, BpmnElementEntity> persistElements(
            BpmnProcessVersionEntity version,
            Document doc,
            Map<String, BpmnLaneEntity> elementToLane) {

        String[] types = {
                "task", "userTask", "serviceTask", "manualTask", "scriptTask",
                "businessRuleTask", "sendTask", "receiveTask",
                "startEvent", "endEvent",
                "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent",
                "exclusiveGateway", "parallelGateway", "inclusiveGateway",
                "eventBasedGateway", "complexGateway",
                "subProcess", "callActivity"
        };

        Map<String, BpmnElementEntity> map = new LinkedHashMap<>();

        for (String type : types) {
            NodeList nodes = doc.getElementsByTagNameNS(NS_BPMN, type);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el    = (Element) nodes.item(i);
                String bpmnId = el.getAttribute("id");
                if (bpmnId.isBlank() || map.containsKey(bpmnId)) continue;

                BpmnElementEntity entity = elementRepo.save(BpmnElementEntity.builder()
                        .version(version)
                        .lane(elementToLane.get(bpmnId))
                        .bpmnId(bpmnId)
                        .elementType(type)
                        .name(nullIfBlank(el.getAttribute("name")))
                        .documentation(extractChildText(el, "documentation"))
                        .build());

                persistExtensionProperties(version, "ELEMENT", entity.getElementId(), el);
                map.put(bpmnId, entity);
            }
        }

        return map;
    }

    private void persistSequenceFlows(BpmnProcessVersionEntity version,
                                      Document doc,
                                      Map<String, BpmnElementEntity> elementMap) {

        NodeList flows = doc.getElementsByTagNameNS(NS_BPMN, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element el       = (Element) flows.item(i);
            String  bpmnId   = el.getAttribute("id");
            String  sourceId = el.getAttribute("sourceRef");
            String  targetId = el.getAttribute("targetRef");

            BpmnElementEntity source = elementMap.get(sourceId);
            BpmnElementEntity target = elementMap.get(targetId);

            if (source == null || target == null) {
                log.warn("SequenceFlow '{}' skipped — source='{}' or target='{}' not found",
                        bpmnId, sourceId, targetId);
                continue;
            }

            BpmnSequenceFlowEntity flow = sequenceFlowRepo.save(BpmnSequenceFlowEntity.builder()
                    .version(version)
                    .bpmnId(bpmnId)
                    .name(nullIfBlank(el.getAttribute("name")))
                    .sourceElement(source)
                    .targetElement(target)
                    .conditionExpression(extractChildText(el, "conditionExpression"))
                    .build());

            persistExtensionProperties(version, "SEQUENCE_FLOW", flow.getFlowId(), el);
        }
    }

    /**
     * Persists {@code <camunda:property>} elements from any DOM element.
     * Idempotent by (ownerType, ownerId, propertyName).
     */
    private void persistExtensionProperties(BpmnProcessVersionEntity version,
                                            String ownerType,
                                            Long ownerId,
                                            Element ownerEl) {
        if (ownerId == null) return;

        NodeList extList = ownerEl.getElementsByTagNameNS(NS_BPMN, "extensionElements");
        if (extList.getLength() == 0) return;

        NodeList props = ((Element) extList.item(0))
                .getElementsByTagNameNS(NS_CAMUNDA, "property");

        for (int i = 0; i < props.getLength(); i++) {
            Element prop = (Element) props.item(i);
            String  name = prop.getAttribute("name");
            if (name.isBlank()) continue;

            boolean exists = extPropRepo
                    .findByOwnerTypeAndOwnerIdAndPropertyName(ownerType, ownerId, name)
                    .isPresent();
            if (exists) continue;

            extPropRepo.save(BpmnExtensionPropertyEntity.builder()
                    .version(version)
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .propertyName(name)
                    .propertyValue(nullIfBlank(prop.getAttribute("value")))
                    .build());
        }
    }

    // =========================================================================
    // Layer 4 — Derived Data
    // =========================================================================

    private void persistStages(BpmnProcessVersionEntity version, Workflow workflow) {
        if (workflow.getStages() == null) return;
        AtomicInteger order = new AtomicInteger(1);
        for (Stage stage : workflow.getStages()) {
            if (stage.getCode() == null || stage.getCode().isBlank()) {
                log.warn("Stage skipped: null/blank code (name='{}')", stage.getName());
                continue;
            }
            version.getStages().add(ProcessStageEntity.builder()
                    .version(version)
                    .code(stage.getCode())
                    .name(stage.getName())
                    .displayOrder(order.getAndIncrement())
                    .build());
        }
    }

    /**
     * Persists activities and conclusions.
     * Links each activity to the structural element via abbreviation (stage-activity),
     * resolved by querying the extension properties already stored in ELEMENT.
     * The laneName is populated directly from the element's lane.
     */
    private Map<String, ProcessActivityEntity> persistActivities(
            BpmnProcessVersionEntity version,
            Workflow workflow,
            Map<String, BpmnElementEntity> elementMap) {

        Map<String, BpmnElementEntity> abbreviationToElement =
                buildAbbreviationToElementMap(elementMap);

        Map<String, ProcessActivityEntity> map = new LinkedHashMap<>();
        AtomicInteger order = new AtomicInteger(1);

        for (ActivityNode node : workflow.getActivities()) {
            BpmnElementEntity element = abbreviationToElement.get(node.getAbbreviation());

            ProcessActivityEntity entity = activityRepo.save(ProcessActivityEntity.builder()
                    .version(version)
                    .element(element)
                    .name(node.getName())
                    .abbreviation(node.getAbbreviation())
                    .stageCode(node.getStageCode())
                    .laneName(element != null && element.getLane() != null
                            ? element.getLane().getName() : null)
                    .displayOrder(order.getAndIncrement())
                    .build());

            if (node.getConclusions() != null) {
                for (var c : node.getConclusions()) {
                    entity.getConclusions().add(ProcessConclusionEntity.builder()
                            .activity(entity)
                            .code(c.getCode())
                            .name(c.getName())
                            .build());
                }
                activityRepo.save(entity);
            }

            map.put(node.getAbbreviation(), entity);
        }

        return map;
    }

    /**
     * For each element with {@code stage} and {@code activity} extension properties,
     * builds the abbreviation {@code stage-activity} and maps it to the element.
     */
    private Map<String, BpmnElementEntity> buildAbbreviationToElementMap(
            Map<String, BpmnElementEntity> elementMap) {

        Map<String, BpmnElementEntity> result = new HashMap<>();

        for (BpmnElementEntity element : elementMap.values()) {
            List<BpmnExtensionPropertyEntity> props =
                    extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", element.getElementId());

            String stageCode    = null;
            String activityCode = null;
            for (BpmnExtensionPropertyEntity p : props) {
                if ("stage".equals(p.getPropertyName()))    stageCode    = p.getPropertyValue();
                if ("activity".equals(p.getPropertyName())) activityCode = p.getPropertyValue();
            }

            if (stageCode != null && activityCode != null) {
                result.put(stageCode + "-" + activityCode, element);
            }
        }

        return result;
    }

    private void persistRules(BpmnProcessVersionEntity version,
                              Workflow workflow,
                              Map<String, ProcessActivityEntity> activityMap) {

        for (WorkflowRule rule : workflow.getRules()) {
            ProcessActivityEntity src = rule.getSource() != null
                    ? activityMap.get(rule.getSource().getAbbreviation()) : null;
            ProcessActivityEntity tgt = rule.getTarget() != null
                    ? activityMap.get(rule.getTarget().getAbbreviation()) : null;

            ruleRepo.save(ProcessRuleEntity.builder()
                    .version(version)
                    .ruleType(rule.getType().name())
                    .sourceActivity(src)
                    .targetActivity(tgt)
                    .sourceAbbreviation(src != null ? src.getAbbreviation() : null)
                    .targetAbbreviation(tgt != null ? tgt.getAbbreviation() : null)
                    .conclusionCode(rule.getConclusion() != null ? rule.getConclusion().getCode() : null)
                    .processStatus(rule.getProcessStatus())
                    .build());
        }
    }

    private void persistInconsistencies(BpmnProcessVersionEntity version, Workflow workflow) {
        for (Inconsistency inc : workflow.getInconsistencies()) {
            version.getInconsistencies().add(ProcessInconsistencyEntity.builder()
                    .version(version)
                    .incType(inc.getType() != null ? String.valueOf(inc.getType()) : "UNKNOWN")
                    .description(inc.getDescription())
                    .build());
        }
    }

    // =========================================================================
    // Config
    // =========================================================================

    @SuppressWarnings("unchecked")
    private BpmnConfigEntity persistConfig(String configYaml) {
        String hash = sha256(configYaml);
        Optional<BpmnConfigEntity> existing = configRepo.findByConfigHash(hash);
        if (existing.isPresent()) return existing.get();

        var yaml = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> root   = yaml.load(configYaml);
        Map<String, Object> parser = (Map<String, Object>) root.get("bpmn_model_parser");
        String engine = parser != null ? (String) parser.getOrDefault("engine", null) : null;

        BpmnConfigEntity config = BpmnConfigEntity.builder()
                .configName("bpmn-config")
                .configVersion(UUID.randomUUID().toString().substring(0, 8))
                .engine(engine)
                .configYaml(configYaml)
                .configHash(hash)
                .build();

        if (parser != null) {
            Map<String, Object> modelProps = (Map<String, Object>) parser.get("model_properties");
            if (modelProps != null) {
                for (Map.Entry<String, Object> entry : modelProps.entrySet()) {
                    String elementType = entry.getKey();
                    List<Map<String, Object>> properties =
                            (List<Map<String, Object>>) entry.getValue();
                    if (properties == null) continue;
                    for (Map<String, Object> prop : properties) {
                        String propName = (String) prop.get("name");
                        if (propName == null) continue;
                        config.getProperties().add(BpmnConfigPropertyEntity.builder()
                                .config(config)
                                .elementType(elementType)
                                .propertyName(propName)
                                .required(Boolean.TRUE.equals(prop.get("required")))
                                .extension(Boolean.TRUE.equals(prop.get("extension")))
                                .build());
                    }
                }
            }
        }

        return configRepo.save(config);
    }

    // =========================================================================
    // DOM utilities
    // =========================================================================

    private String extractChildText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(NS_BPMN, localName);
        if (nodes.getLength() == 0) return null;
        return nullIfBlank(nodes.item(0).getTextContent());
    }

    private String nullIfBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}