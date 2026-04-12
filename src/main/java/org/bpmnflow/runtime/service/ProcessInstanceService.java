package org.bpmnflow.runtime.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.dto.WorkflowSummaryResponse;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {

    private final BpmnProcessVersionRepository versionRepo;
    private final BpmnRuleRepository ruleRepo;
    private final BpmnActivityRepository activityRepo;
    private final WfProcessInstanceRepository instanceRepo;
    private final WfInstanceActivityRepository instActivityRepo;
    private final WfInstanceVariableRepository variableRepo;

    // ---------------------------------------------------------------
    // Instance operations
    // ---------------------------------------------------------------

    @Transactional
    public ProcessInstanceResponse startProcess(Long versionId, StartProcessRequest request) {
        BpmnProcessVersionEntity version = versionRepo.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));

        List<ProcessRuleEntity> entryRules = ruleRepo.findByVersion_VersionIdAndRuleType(versionId, "START_TO_TASK");
        if (entryRules.isEmpty()) {
            throw new IllegalStateException("No START_TO_TASK rule found for version " + versionId);
        }

        ProcessRuleEntity entryRule = entryRules.get(0);
        ProcessActivityEntity firstActivity = entryRule.getTargetActivity();
        if (firstActivity == null) {
            throw new IllegalStateException("START_TO_TASK rule has no target activity");
        }

        WfProcessInstanceEntity instance = WfProcessInstanceEntity.builder()
                .version(version)
                .externalId(request != null ? request.getExternalId() : null)
                .status("ACTIVE")
                .processStatus(entryRule.getProcessStatus())
                .build();
        instance = instanceRepo.save(instance);

        WfInstanceActivityEntity firstStep = WfInstanceActivityEntity.builder()
                .instance(instance)
                .activity(firstActivity)
                .stepNumber(1)
                .status("ACTIVE")
                .build();
        instActivityRepo.save(firstStep);

        if (request != null && request.getVariables() != null) {
            persistVariables(instance, request.getVariables());
        }

        log.info("Started instance {} for version {} at '{}'",
                instance.getInstanceId(), versionId, firstActivity.getAbbreviation());

        return buildResponse(instance, firstStep);
    }

    @Transactional
    public ProcessInstanceResponse completeActivity(Long instanceId, CompleteActivityRequest request) {
        WfProcessInstanceEntity instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));

        if ("COMPLETED".equals(instance.getStatus())) {
            throw new IllegalStateException("Process instance is already completed");
        }

        WfInstanceActivityEntity currentStep = instActivityRepo
                .findByInstance_InstanceIdAndStatus(instanceId, "ACTIVE")
                .orElseThrow(() -> new IllegalStateException("No active activity for instance " + instanceId));

        ProcessActivityEntity currentActivity = currentStep.getActivity();
        Long versionId = instance.getVersion().getVersionId();

        String conclusionCode = request != null ? request.getConclusionCode() : null;
        boolean hasConclusions = !currentActivity.getConclusions().isEmpty();

        if (hasConclusions) {
            if (conclusionCode == null || conclusionCode.isBlank()) {
                List<String> available = currentActivity.getConclusions().stream()
                        .map(ProcessConclusionEntity::getCode).collect(Collectors.toList());
                throw new IllegalArgumentException(
                        "Activity '" + currentActivity.getAbbreviation() +
                                "' requires a conclusion. Available: " + available);
            }
            boolean valid = currentActivity.getConclusions().stream()
                    .anyMatch(c -> conclusionCode.equals(c.getCode()));
            if (!valid) {
                List<String> available = currentActivity.getConclusions().stream()
                        .map(ProcessConclusionEntity::getCode).collect(Collectors.toList());
                throw new IllegalArgumentException(
                        "Invalid conclusion '" + conclusionCode +
                                "' for activity '" + currentActivity.getAbbreviation() +
                                "'. Available: " + available);
            }
        }

        currentStep.setStatus("COMPLETED");
        currentStep.setConclusionCode(conclusionCode);
        currentStep.setCompletedAt(LocalDateTime.now());
        instActivityRepo.save(currentStep);

        List<ProcessRuleEntity> matchingRules;
        if (conclusionCode != null && !conclusionCode.isBlank()) {
            matchingRules = ruleRepo
                    .findByVersion_VersionIdAndSourceActivity_ActivityIdAndConclusionCode(
                            versionId, currentActivity.getActivityId(), conclusionCode);
        } else {
            matchingRules = List.of();
        }

        if (matchingRules.isEmpty()) {
            matchingRules = ruleRepo
                    .findByVersion_VersionIdAndSourceActivity_ActivityId(
                            versionId, currentActivity.getActivityId())
                    .stream()
                    .filter(r -> r.getConclusionCode() == null ||
                            r.getConclusionCode().equals(conclusionCode))
                    .collect(Collectors.toList());
        }

        if (matchingRules.isEmpty()) {
            throw new IllegalStateException("No rule found for activity '" +
                    currentActivity.getAbbreviation() + "' with conclusion '" + conclusionCode + "'");
        }

        ProcessRuleEntity matchedRule = matchingRules.get(0);

        if (matchedRule.getProcessStatus() != null) {
            instance.setProcessStatus(matchedRule.getProcessStatus());
        }

        if (request != null && request.getVariables() != null) {
            persistVariables(instance, request.getVariables());
        }

        ProcessActivityEntity nextActivity = matchedRule.getTargetActivity();
        boolean isEnd = nextActivity == null || matchedRule.getRuleType().endsWith("_TO_END");

        if (isEnd) {
            instance.setStatus("COMPLETED");
            instance.setCompletedAt(LocalDateTime.now());
            instanceRepo.save(instance);
            log.info("Instance {} completed at step {}", instanceId, currentStep.getStepNumber());
            return buildResponse(instance, null);
        }

        WfInstanceActivityEntity nextStep = WfInstanceActivityEntity.builder()
                .instance(instance)
                .activity(nextActivity)
                .stepNumber(currentStep.getStepNumber() + 1)
                .status("ACTIVE")
                .build();
        instActivityRepo.save(nextStep);
        instanceRepo.save(instance);

        log.info("Instance {} advanced '{}' → '{}' (conclusion: '{}')",
                instanceId, currentActivity.getAbbreviation(),
                nextActivity.getAbbreviation(), conclusionCode);

        return buildResponse(instance, nextStep);
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummaryResponse> listInstances(String status, String processKey) {
        List<WfProcessInstanceEntity> instances;

        if (processKey != null && status != null) {
            instances = instanceRepo.findByProcessKeyAndStatusOrderByCreatedAtDesc(processKey, status.toUpperCase());
        } else if (processKey != null) {
            instances = instanceRepo.findByProcessKeyOrderByCreatedAtDesc(processKey);
        } else if (status != null) {
            instances = instanceRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            instances = instanceRepo.findAllByOrderByCreatedAtDesc();
        }

        return instances.stream().map(this::buildSummary).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProcessInstanceResponse getInstance(Long instanceId) {
        WfProcessInstanceEntity instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
        WfInstanceActivityEntity activeStep = instActivityRepo
                .findByInstance_InstanceIdAndStatus(instanceId, "ACTIVE").orElse(null);
        return buildResponse(instance, activeStep);
    }

    // ---------------------------------------------------------------
    // Variable operations
    // ---------------------------------------------------------------

    @Transactional
    public List<VariableResponse> setVariables(Long instanceId, List<VariableRequest> variables) {
        WfProcessInstanceEntity instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
        persistVariables(instance, variables);
        return getVariableList(instanceId);
    }

    @Transactional(readOnly = true)
    public List<VariableResponse> getVariables(Long instanceId) {
        if (!instanceRepo.existsById(instanceId)) {
            throw new ResourceNotFoundException("Instance not found: " + instanceId);
        }
        return getVariableList(instanceId);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void persistVariables(WfProcessInstanceEntity instance,
                                  List<VariableRequest> variables) {
        if (variables == null || variables.isEmpty()) return;

        for (VariableRequest req : variables) {
            if (req.getKey() == null || req.getKey().isBlank()) {
                throw new IllegalArgumentException("Variable key must not be blank");
            }
            VariableType type = req.getType() != null ? req.getType() : VariableType.STRING;
            type.validate(req.getValue());
        }

        for (VariableRequest req : variables) {
            VariableType type = req.getType() != null ? req.getType() : VariableType.STRING;

            WfInstanceVariableEntity entity = variableRepo
                    .findByInstance_InstanceIdAndVariableKey(instance.getInstanceId(), req.getKey())
                    .orElse(null);

            if (entity != null) {
                entity.setVariableType(type);
                entity.setVariableValue(req.getValue());
                variableRepo.save(entity);
            } else {
                variableRepo.save(WfInstanceVariableEntity.builder()
                        .instance(instance)
                        .variableKey(req.getKey())
                        .variableType(type)
                        .variableValue(req.getValue())
                        .build());
            }
        }
    }

    private List<VariableResponse> getVariableList(Long instanceId) {
        return variableRepo.findByInstance_InstanceId(instanceId).stream()
                .map(v -> VariableResponse.builder()
                        .key(v.getVariableKey())
                        .type(v.getVariableType())
                        .value(v.getVariableValue())
                        .convertedValue(v.getVariableType().convert(v.getVariableValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private WorkflowSummaryResponse buildSummary(WfProcessInstanceEntity instance) {
        BpmnProcessVersionEntity version = instance.getVersion();

        String currentAbbreviation = null;
        String currentActivityName = null;
        WfInstanceActivityEntity activeStep = instance.getInstanceActivities().stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .findFirst().orElse(null);
        if (activeStep != null) {
            currentAbbreviation = activeStep.getActivity().getAbbreviation();
            currentActivityName  = activeStep.getActivity().getName();
        }

        return WorkflowSummaryResponse.builder()
                .instanceId(instance.getInstanceId())
                .externalId(instance.getExternalId())
                .instanceStatus(instance.getStatus())
                .processStatus(instance.getProcessStatus())
                .versionId(version.getVersionId())
                .versionNumber(version.getVersionNumber())
                .processKey(version.getProcess().getProcessKey())
                .processName(version.getProcess().getName())
                .currentActivityAbbreviation(currentAbbreviation)
                .currentActivityName(currentActivityName)
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .completedAt(instance.getCompletedAt())
                .build();
    }

    private ProcessInstanceResponse buildResponse(WfProcessInstanceEntity instance,
                                                  WfInstanceActivityEntity activeStep) {
        BpmnProcessVersionEntity version = instance.getVersion();

        ActivityStepResponse currentAct = null;
        if (activeStep != null) {
            ProcessActivityEntity act = activeStep.getActivity();
            currentAct = ActivityStepResponse.builder()
                    .stepNumber(activeStep.getStepNumber())
                    .activityId(act.getActivityId())
                    .elementBpmnId(act.getElement() != null ? act.getElement().getBpmnId() : null)
                    .abbreviation(act.getAbbreviation())
                    .activityName(act.getName())
                    .stageCode(act.getStageCode())
                    .laneName(act.getLaneName())
                    .status(activeStep.getStatus())
                    .startedAt(activeStep.getStartedAt())
                    .availableConclusions(act.getConclusions().stream()
                            .map(c -> ActivityStepResponse.ConclusionOption.builder()
                                    .code(c.getCode()).name(c.getName()).build())
                            .collect(Collectors.toList()))
                    .build();
        }

        List<ProcessInstanceResponse.ActivityHistoryEntry> history =
                instance.getInstanceActivities().stream()
                        .map(s -> {
                            ProcessActivityEntity act = s.getActivity();
                            return ProcessInstanceResponse.ActivityHistoryEntry.builder()
                                    .stepNumber(s.getStepNumber())
                                    .elementBpmnId(act.getElement() != null
                                            ? act.getElement().getBpmnId() : null)
                                    .abbreviation(act.getAbbreviation())
                                    .activityName(act.getName())
                                    .status(s.getStatus())
                                    .conclusionCode(s.getConclusionCode())
                                    .startedAt(s.getStartedAt())
                                    .completedAt(s.getCompletedAt())
                                    .build();
                        })
                        .collect(Collectors.toList());

        return ProcessInstanceResponse.builder()
                .instanceId(instance.getInstanceId())
                .externalId(instance.getExternalId())
                .instanceStatus(instance.getStatus())
                .processStatus(instance.getProcessStatus())
                .versionId(version.getVersionId())
                .versionNumber(version.getVersionNumber())
                .versionTag(version.getVersionTag())
                .processType(version.getProcessType())
                .processSubtype(version.getProcessSubtype())
                .currentActivity(currentAct)
                .activityHistory(history)
                .variables(getVariableList(instance.getInstanceId()))
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .completedAt(instance.getCompletedAt())
                .build();
    }
}
