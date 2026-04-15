package org.bpmnflow.runtime.service.deploy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.model.*;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persists the derived-data layer (Layer 4) of a deployed BPMN model.
 *
 * <p>Derived data is produced by the BPMNFlow {@link org.bpmnflow.parser.ModelParser}
 * and stored as normalised rows so that the runtime can resolve rules and
 * activities without re-parsing the BPMN XML on every request.</p>
 *
 * <p>Persistence order within this component:</p>
 * <ol>
 *   <li>Stages (appended directly to the version's collection).</li>
 *   <li>Activities + their conclusions, linked back to Layer-3 elements.</li>
 *   <li>Transition rules referencing source/target activity entities.</li>
 *   <li>Inconsistencies (appended to the version's collection).</li>
 * </ol>
 *
 * <p>This component has no knowledge of the DOM or namespace constants;
 * it works exclusively with the BPMNFlow {@link Workflow} model and
 * the {@code elementMap} produced by {@link StructuralPersistor}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DerivedDataPersistor {

    private final BpmnExtensionPropertyRepository extPropRepo;
    private final BpmnActivityRepository          activityRepo;
    private final BpmnRuleRepository              ruleRepo;

    /**
     * Runs all Layer-4 persistence steps and returns a map of
     * activity abbreviation → saved {@link ProcessActivityEntity},
     * which is used by {@link org.bpmnflow.runtime.service.BpmnDeployService}
     * to build the {@link org.bpmnflow.runtime.service.DeployResult}.
     */
    public Map<String, ProcessActivityEntity> persist(
            BpmnProcessVersionEntity version,
            Workflow workflow,
            Map<String, BpmnElementEntity> elementMap) {

        persistStages(version, workflow);

        Map<String, ProcessActivityEntity> activityMap =
                persistActivities(version, workflow, elementMap);

        persistRules(version, workflow, activityMap);
        persistInconsistencies(version, workflow);

        return activityMap;
    }

    // -------------------------------------------------------------------------
    // Stages
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Activities + conclusions
    // -------------------------------------------------------------------------

    /**
     * Persists each {@link ActivityNode} as a {@link ProcessActivityEntity}.
     *
     * <p>To link the derived activity back to its structural element (Layer 3),
     * the extension properties already stored for each element are queried to
     * find the matching {@code stage-activity} abbreviation.</p>
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
     * Queries stored extension properties to build an
     * {@code abbreviation (stage-activity) → BpmnElementEntity} map.
     */
    private Map<String, BpmnElementEntity> buildAbbreviationToElementMap(
            Map<String, BpmnElementEntity> elementMap) {

        Map<String, BpmnElementEntity> result = new HashMap<>();

        for (BpmnElementEntity element : elementMap.values()) {
            List<BpmnExtensionPropertyEntity> props =
                    extPropRepo.findByOwnerTypeAndOwnerId(
                            ExtPropOwner.ELEMENT.name(), element.getElementId());

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

    // -------------------------------------------------------------------------
    // Rules
    // -------------------------------------------------------------------------

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
                    .ruleType(rule.getType())
                    .sourceActivity(src)
                    .targetActivity(tgt)
                    .sourceAbbreviation(src != null ? src.getAbbreviation() : null)
                    .targetAbbreviation(tgt != null ? tgt.getAbbreviation() : null)
                    .conclusionCode(rule.getConclusion() != null
                            ? rule.getConclusion().getCode() : null)
                    .processStatus(rule.getProcessStatus())
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Inconsistencies
    // -------------------------------------------------------------------------

    private void persistInconsistencies(BpmnProcessVersionEntity version, Workflow workflow) {
        for (Inconsistency inc : workflow.getInconsistencies()) {
            version.getInconsistencies().add(ProcessInconsistencyEntity.builder()
                    .version(version)
                    .incType(inc.getType() != null ? String.valueOf(inc.getType()) : "UNKNOWN")
                    .description(inc.getDescription())
                    .build());
        }
    }
}