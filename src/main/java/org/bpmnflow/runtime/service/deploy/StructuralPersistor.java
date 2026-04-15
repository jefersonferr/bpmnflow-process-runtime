package org.bpmnflow.runtime.service.deploy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persists the structural layer (Layer 3) of a deployed BPMN model.
 *
 * <p>Structural data is extracted from the raw DOM {@link Document} and saved
 * in dependency order so that every foreign-key reference is resolved before
 * the dependent row is inserted:</p>
 *
 * <ol>
 *   <li>Extension properties of {@code <bpmn:process>} (version-level).</li>
 *   <li>Participants → their extension properties.</li>
 *   <li>Lanes (resolved to participant via DOM walk) → their extension properties.</li>
 *   <li>BPMN elements (tasks, events, gateways…) linked to lanes.</li>
 *   <li>Sequence flows linking source → target elements.</li>
 * </ol>
 *
 * <p>Each entity is saved individually — not via cascade — so the generated PK
 * is available before extension properties are persisted.</p>
 *
 * <p>This component has no knowledge of the BPMNFlow {@code Workflow} model;
 * it works exclusively with the DOM and JPA repositories.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuralPersistor {

    private static final String NS_BPMN    = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String NS_CAMUNDA = "http://camunda.org/schema/1.0/bpmn";

    private final BpmnParticipantRepository       participantRepo;
    private final BpmnLaneRepository              laneRepo;
    private final BpmnElementRepository           elementRepo;
    private final BpmnSequenceFlowRepository      sequenceFlowRepo;
    private final BpmnExtensionPropertyRepository extPropRepo;

    /**
     * Result value carrying the maps needed by {@link DerivedDataPersistor}.
     *
     * @param participantMap bpmnId → saved {@link BpmnParticipantEntity}
     * @param elementMap     bpmnId → saved {@link BpmnElementEntity}
     */
    public record StructuralResult(
            Map<String, BpmnParticipantEntity> participantMap,
            Map<String, BpmnElementEntity>     elementMap
    ) {}

    /**
     * Runs all Layer-3 persistence steps for the given {@code version}
     * and returns the maps required by the derived-data step.
     */
    public StructuralResult persist(BpmnProcessVersionEntity version, Document doc) {

        persistProcessExtensionProperties(version, doc);

        Map<String, BpmnParticipantEntity> participantMap = persistParticipants(version, doc);

        Map<String, BpmnLaneEntity> elementToLane = buildElementToLaneMap(version, doc);

        Map<String, BpmnElementEntity> elementMap = persistElements(version, doc, elementToLane);

        persistSequenceFlows(version, doc, elementMap);

        return new StructuralResult(participantMap, elementMap);
    }

    // -------------------------------------------------------------------------
    // Extension properties — process level
    // -------------------------------------------------------------------------

    private void persistProcessExtensionProperties(BpmnProcessVersionEntity version, Document doc) {
        NodeList processList = doc.getElementsByTagNameNS(NS_BPMN, "process");
        if (processList.getLength() == 0) return;
        persistExtensionProperties(version, ExtPropOwner.PROCESS, version.getVersionId(),
                (Element) processList.item(0));
    }

    // -------------------------------------------------------------------------
    // Participants + Lanes
    // -------------------------------------------------------------------------

    private Map<String, BpmnParticipantEntity> persistParticipants(
            BpmnProcessVersionEntity version, Document doc) {

        Map<String, BpmnParticipantEntity> byBpmnId     = new LinkedHashMap<>();
        Map<String, BpmnParticipantEntity> byProcessRef = new HashMap<>();

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

            persistExtensionProperties(version, ExtPropOwner.PARTICIPANT, entity.getParticipantId(), el);
            byBpmnId.put(bpmnId, entity);
            if (processRef != null) byProcessRef.put(processRef, entity);
        }

        AtomicInteger order = new AtomicInteger(1);
        NodeList lanes = doc.getElementsByTagNameNS(NS_BPMN, "lane");
        for (int i = 0; i < lanes.getLength(); i++) {
            Element laneEl = (Element) lanes.item(i);
            String  bpmnId = laneEl.getAttribute("id");

            BpmnParticipantEntity participant =
                    resolveParticipantForLane(laneEl, byProcessRef, byBpmnId);

            BpmnLaneEntity lane = laneRepo.save(BpmnLaneEntity.builder()
                    .version(version)
                    .participant(participant)
                    .bpmnId(bpmnId)
                    .name(nullIfBlank(laneEl.getAttribute("name")))
                    .displayOrder(order.getAndIncrement())
                    .build());

            persistExtensionProperties(version, ExtPropOwner.LANE, lane.getLaneId(), laneEl);
        }

        return byBpmnId;
    }

    /** Walks lane → laneSet → process to resolve the owning participant. */
    private BpmnParticipantEntity resolveParticipantForLane(
            Element laneEl,
            Map<String, BpmnParticipantEntity> byProcessRef,
            Map<String, BpmnParticipantEntity> byBpmnId) {

        Node laneSet = laneEl.getParentNode();
        if (laneSet != null) {
            Node process = laneSet.getParentNode();
            if (process instanceof Element processEl) {
                BpmnParticipantEntity found = byProcessRef.get(processEl.getAttribute("id"));
                if (found != null) return found;
            }
        }
        return byBpmnId.values().iterator().next();
    }

    // -------------------------------------------------------------------------
    // Element → lane map (via flowNodeRef)
    // -------------------------------------------------------------------------

    private Map<String, BpmnLaneEntity> buildElementToLaneMap(
            BpmnProcessVersionEntity version, Document doc) {

        Map<String, BpmnLaneEntity> laneByBpmnId = new HashMap<>();
        for (BpmnLaneEntity l : laneRepo.findByVersion_VersionId(version.getVersionId())) {
            laneByBpmnId.put(l.getBpmnId(), l);
        }

        Map<String, BpmnLaneEntity> elementToLane = new HashMap<>();
        NodeList lanes = doc.getElementsByTagNameNS(NS_BPMN, "lane");
        for (int i = 0; i < lanes.getLength(); i++) {
            Element        laneEl     = (Element) lanes.item(i);
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

    // -------------------------------------------------------------------------
    // Elements
    // -------------------------------------------------------------------------

    private static final String[] ELEMENT_TYPES = {
            "task", "userTask", "serviceTask", "manualTask", "scriptTask",
            "businessRuleTask", "sendTask", "receiveTask",
            "startEvent", "endEvent",
            "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent",
            "exclusiveGateway", "parallelGateway", "inclusiveGateway",
            "eventBasedGateway", "complexGateway",
            "subProcess", "callActivity"
    };

    private Map<String, BpmnElementEntity> persistElements(
            BpmnProcessVersionEntity version,
            Document doc,
            Map<String, BpmnLaneEntity> elementToLane) {

        Map<String, BpmnElementEntity> map = new LinkedHashMap<>();

        for (String type : ELEMENT_TYPES) {
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

                persistExtensionProperties(version, ExtPropOwner.ELEMENT, entity.getElementId(), el);
                map.put(bpmnId, entity);
            }
        }

        return map;
    }

    // -------------------------------------------------------------------------
    // Sequence flows
    // -------------------------------------------------------------------------

    private void persistSequenceFlows(BpmnProcessVersionEntity version,
                                      Document doc,
                                      Map<String, BpmnElementEntity> elementMap) {

        NodeList flows = doc.getElementsByTagNameNS(NS_BPMN, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element el     = (Element) flows.item(i);
            String  bpmnId = el.getAttribute("id");

            BpmnElementEntity source = elementMap.get(el.getAttribute("sourceRef"));
            BpmnElementEntity target = elementMap.get(el.getAttribute("targetRef"));

            if (source == null || target == null) {
                log.warn("SequenceFlow '{}' skipped — source='{}' or target='{}' not found",
                        bpmnId, el.getAttribute("sourceRef"), el.getAttribute("targetRef"));
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

            persistExtensionProperties(version, ExtPropOwner.SEQUENCE_FLOW, flow.getFlowId(), el);
        }
    }

    // -------------------------------------------------------------------------
    // Extension properties (shared by all structural elements)
    // -------------------------------------------------------------------------

    /**
     * Persists {@code <camunda:property>} children of {@code ownerEl}.
     * Idempotent: skips properties already present for (ownerType, ownerId, name).
     */
    private void persistExtensionProperties(BpmnProcessVersionEntity version,
                                            ExtPropOwner ownerType,
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
                    .findByOwnerTypeAndOwnerIdAndPropertyName(ownerType.name(), ownerId, name)
                    .isPresent();
            if (exists) continue;

            extPropRepo.save(BpmnExtensionPropertyEntity.builder()
                    .version(version)
                    .ownerType(ownerType.name())
                    .ownerId(ownerId)
                    .propertyName(name)
                    .propertyValue(nullIfBlank(prop.getAttribute("value")))
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // DOM utilities
    // -------------------------------------------------------------------------

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
}