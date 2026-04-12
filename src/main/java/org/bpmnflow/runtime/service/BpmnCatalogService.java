package org.bpmnflow.runtime.service;

import lombok.RequiredArgsConstructor;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.dto.ProcessSummaryResponse;
import org.bpmnflow.runtime.dto.ProcessVersionSummaryResponse;
import org.bpmnflow.runtime.model.entity.BpmnProcessEntity;
import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;
import org.bpmnflow.runtime.repository.BpmnProcessRepository;
import org.bpmnflow.runtime.repository.BpmnProcessVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpmnCatalogService {

    private final BpmnProcessRepository processRepo;
    private final BpmnProcessVersionRepository versionRepo;

    @Transactional(readOnly = true)
    public List<ProcessSummaryResponse> listProcesses() {
        return processRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::buildProcessSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProcessSummaryResponse getProcess(String processKey) {
        BpmnProcessEntity process = processRepo.findByProcessKey(processKey)
                .orElseThrow(() -> new ResourceNotFoundException("Process not found: " + processKey));
        return buildProcessSummary(process);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private ProcessSummaryResponse buildProcessSummary(BpmnProcessEntity process) {
        List<ProcessVersionSummaryResponse> versions =
                versionRepo.findByProcess_ProcessIdOrderByVersionNumberDesc(process.getProcessId())
                        .stream()
                        .map(this::buildVersionSummary)
                        .collect(Collectors.toList());

        return ProcessSummaryResponse.builder()
                .processId(process.getProcessId())
                .processKey(process.getProcessKey())
                .name(process.getName())
                .description(process.getDescription())
                .createdAt(process.getCreatedAt())
                .updatedAt(process.getUpdatedAt())
                .versions(versions)
                .build();
    }

    private ProcessVersionSummaryResponse buildVersionSummary(BpmnProcessVersionEntity v) {
        return ProcessVersionSummaryResponse.builder()
                .versionId(v.getVersionId())
                .versionNumber(v.getVersionNumber())
                .versionTag(v.getVersionTag())
                .status(v.getStatus())
                .processType(v.getProcessType())
                .processSubtype(v.getProcessSubtype())
                .valid(v.isValid())
                .inconsistencyCount(v.getInconsistencies().size())
                .participantCount(v.getParticipants().size())
                .laneCount((int) v.getParticipants().stream()
                        .mapToLong(p -> p.getLanes().size()).sum())
                .elementCount(v.getElements().size())
                .sequenceFlowCount(v.getSequenceFlows().size())
                .stageCount(v.getStages().size())
                .activityCount(v.getActivities().size())
                .ruleCount(v.getRules().size())
                .parsedAt(v.getParsedAt())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
