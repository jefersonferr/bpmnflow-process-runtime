package org.bpmnflow.runtime.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.WorkflowEngine;
import org.bpmnflow.WorkflowEngineImpl;
import org.bpmnflow.model.Workflow;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.bpmnflow.runtime.service.deploy.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Orchestrates the full BPMN deploy pipeline within a single transaction.
 *
 * <p>This service owns the transaction boundary and the sequencing of
 * steps; all business logic is delegated to focused collaborators:</p>
 *
 * <ul>
 *   <li>{@link BpmnModelParser}     — parses bytes into {@link ParsedBpmnModel}.</li>
 *   <li>{@link BpmnConfigPersistor} — deduplicates and saves the config entity.</li>
 *   <li>{@link StructuralPersistor} — Layer 3: participants, lanes, elements, flows.</li>
 *   <li>{@link DerivedDataPersistor}— Layer 4: stages, activities, rules, inconsistencies.</li>
 * </ul>
 *
 * <p>The method body reads as a numbered pipeline (1–8) that matches the
 * layer model documented in the Liquibase changelogs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BpmnDeployService {

    private final BpmnModelParser     modelParser;
    private final AtomicReference<WorkflowEngine> engineRef;
    private final BpmnConfigPersistor configPersistor;
    private final StructuralPersistor structuralPersistor;
    private final DerivedDataPersistor derivedDataPersistor;

    private final BpmnProcessRepository        processRepo;
    private final BpmnProcessVersionRepository versionRepo;
    private final BpmnLaneRepository           laneRepo;
    private final BpmnSequenceFlowRepository   sequenceFlowRepo;

    // =========================================================================
    // Entry point
    // =========================================================================

    @Transactional
    public DeployResult deploy(byte[] bpmnContent, byte[] configContent, String processKey) {

        // 1. Parse — no DB touch, no side effects
        ParsedBpmnModel parsed = modelParser.parse(bpmnContent, configContent);
        engineRef.set(new WorkflowEngineImpl(parsed.getWorkflow()));
        Workflow workflow = parsed.getWorkflow();

        // 2. Config — deduplication by SHA-256
        BpmnConfigEntity configEntity = configPersistor.persist(parsed.getConfigYaml());

        // 3. Process — create or reuse, with PESSIMISTIC_WRITE to prevent version race
        String key = processKey != null ? processKey : workflow.getId();
        BpmnProcessEntity processEntity = processRepo.findByProcessKeyForUpdate(key)
                .orElseGet(() -> processRepo.save(BpmnProcessEntity.builder()
                        .processKey(key)
                        .name(workflow.getName() != null ? workflow.getName() : key)
                        .build()));

        // 4. Version
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
                .bpmnXml(parsed.getBpmnXml())
                .valid(workflow.getInconsistencies().isEmpty())
                .build());

        // 5–6. Layer 3: structural elements (participants, lanes, elements, flows)
        StructuralPersistor.StructuralResult structural =
                structuralPersistor.persist(version, parsed.getDocument());

        // 7–8. Layer 4: derived data (stages, activities, rules, inconsistencies)
        Map<String, ProcessActivityEntity> activityMap =
                derivedDataPersistor.persist(version, workflow, structural.elementMap());

        // ---- counters (actual DB rows, not in-memory collections) ----
        int laneCount         = laneRepo.findByVersion_VersionId(version.getVersionId()).size();
        int sequenceFlowCount = sequenceFlowRepo.findByVersion_VersionId(version.getVersionId()).size();

        List<String> inconsistencyMessages = workflow.getInconsistencies().stream()
                .map(inc -> (inc.getType() != null ? String.valueOf(inc.getType()) : "UNKNOWN")
                        + ": " + inc.getDescription())
                .collect(Collectors.toList());

        log.info("Deployed '{}' v{} | participants={} lanes={} elements={} flows={} "
                        + "| stages={} activities={} rules={} issues={}",
                key, nextVersion,
                structural.participantMap().size(), laneCount,
                structural.elementMap().size(), sequenceFlowCount,
                workflow.getStages() != null ? workflow.getStages().size() : 0,
                activityMap.size(), workflow.getRules().size(),
                workflow.getInconsistencies().size());

        return DeployResult.builder()
                .version(version)
                .participantCount(structural.participantMap().size())
                .laneCount(laneCount)
                .elementCount(structural.elementMap().size())
                .sequenceFlowCount(sequenceFlowCount)
                .stageCount(workflow.getStages() != null ? workflow.getStages().size() : 0)
                .activityCount(activityMap.size())
                .ruleCount(workflow.getRules().size())
                .inconsistencyCount(workflow.getInconsistencies().size())
                .inconsistencies(inconsistencyMessages)
                .build();
    }
}