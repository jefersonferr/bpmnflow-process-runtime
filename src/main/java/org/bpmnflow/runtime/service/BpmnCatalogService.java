package org.bpmnflow.runtime.service;

import lombok.RequiredArgsConstructor;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.dto.ActivityNodeResponse.ApiHandlerResponse;
import org.bpmnflow.runtime.dto.ActivityNodeResponse.ConclusionResponse;
import org.bpmnflow.runtime.dto.ActivityNodeResponse.KeyValue;
import org.bpmnflow.runtime.model.entity.BpmnExtensionPropertyEntity;
import org.bpmnflow.runtime.model.entity.BpmnProcessEntity;
import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;
import org.bpmnflow.runtime.model.entity.ProcessActivityEntity;
import org.bpmnflow.runtime.repository.BpmnActivityRepository;
import org.bpmnflow.runtime.repository.BpmnExtensionPropertyRepository;
import org.bpmnflow.runtime.repository.BpmnProcessRepository;
import org.bpmnflow.runtime.repository.BpmnProcessVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpmnCatalogService {

    // Property-name prefixes written by StructuralPersistor.persistConnectorProperties()
    static final String CONNECTOR_ID         = "connector.id";
    static final String CONNECTOR_INPUT_PFX  = "connector.input.";
    static final String CONNECTOR_INPUT_URL  = "connector.input.url";
    static final String CONNECTOR_INPUT_METHOD = "connector.input.method";
    static final String CONNECTOR_OUTPUT_PFX = "connector.output.";

    private final BpmnProcessRepository           processRepo;
    private final BpmnProcessVersionRepository    versionRepo;
    private final BpmnActivityRepository          activityRepo;
    private final BpmnExtensionPropertyRepository extPropRepo;

    // ---------------------------------------------------------------
    // Process catalog (unchanged)
    // ---------------------------------------------------------------

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
    // Activity catalog
    // ---------------------------------------------------------------

    /**
     * Returns all activities of the given version.
     * Service tasks with a {@code <camunda:connector>} include an {@code apiHandler} block.
     * Plain tasks have {@code apiHandler = null} (field omitted by Jackson).
     */
    @Transactional(readOnly = true)
    public List<ActivityNodeResponse> listActivities(Long versionId) {
        requireVersionExists(versionId);
        return activityRepo.findByVersion_VersionId(versionId).stream()
                .map(a -> buildActivityNodeResponse(a, buildApiHandlerResponse(a)))
                .collect(Collectors.toList());
    }

    /**
     * Returns only activities that have a {@code <camunda:connector>}
     * (i.e. those with a {@code connector.id} extension property).
     */
    @Transactional(readOnly = true)
    public List<ActivityNodeResponse> listApiActivities(Long versionId) {
        requireVersionExists(versionId);
        return activityRepo.findByVersion_VersionId(versionId).stream()
                .map(a -> {
                    ApiHandlerResponse api = buildApiHandlerResponse(a);
                    return api != null ? buildActivityNodeResponse(a, api) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void requireVersionExists(Long versionId) {
        if (!versionRepo.existsById(versionId)) {
            throw new ResourceNotFoundException("Version not found: " + versionId);
        }
    }

    /**
     * Builds the {@link ApiHandlerResponse} from {@code connector.*} extension
     * properties persisted by {@code StructuralPersistor.persistConnectorProperties()}.
     *
     * <p>Returns {@code null} when the activity has no element, no extension
     * properties, or no {@code connector.id} property (i.e. not a connector task).</p>
     */
    private ApiHandlerResponse buildApiHandlerResponse(ProcessActivityEntity activity) {
        if (activity.getElement() == null) return null;

        Long elementId = activity.getElement().getElementId();
        List<BpmnExtensionPropertyEntity> props =
                extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", elementId);

        if (props.isEmpty()) return null;

        // Check for connector.id — mandatory discriminator
        String connectorId = props.stream()
                .filter(p -> CONNECTOR_ID.equals(p.getPropertyName()))
                .map(BpmnExtensionPropertyEntity::getPropertyValue)
                .findFirst().orElse(null);

        if (connectorId == null || connectorId.isBlank()) return null;

        // Build inputMappings and outputMappings, extract endpoint + method
        String endpoint = null;
        String method   = null;
        List<KeyValue> inputMappings  = new ArrayList<>();
        List<KeyValue> outputMappings = new ArrayList<>();

        for (BpmnExtensionPropertyEntity prop : props) {
            String name  = prop.getPropertyName();
            String value = prop.getPropertyValue();

            if (CONNECTOR_INPUT_URL.equals(name)) {
                endpoint = value;
            } else if (CONNECTOR_INPUT_METHOD.equals(name)) {
                method = value;
            } else if (name.startsWith(CONNECTOR_INPUT_PFX)) {
                String key = name.substring(CONNECTOR_INPUT_PFX.length());
                inputMappings.add(new KeyValue(key, value));
            } else if (name.startsWith(CONNECTOR_OUTPUT_PFX)) {
                String key = name.substring(CONNECTOR_OUTPUT_PFX.length());
                outputMappings.add(new KeyValue(key, value));
            }
        }

        return ApiHandlerResponse.builder()
                .connectorId(connectorId)
                .endpoint(endpoint)
                .method(method)
                .retries(0)
                .taskHeaders(List.of())
                .inputMappings(inputMappings.isEmpty() ? null : inputMappings)
                .outputMappings(outputMappings.isEmpty() ? null : outputMappings)
                .build();
    }

    /**
     * Builds an {@link ActivityNodeResponse} with the same field set as
     * bpmnflow-core's {@code ActivityNode}.
     */
    private ActivityNodeResponse buildActivityNodeResponse(ProcessActivityEntity activity,
                                                           ApiHandlerResponse apiHandler) {
        String abbreviation = activity.getAbbreviation();
        String stageCode    = activity.getStageCode();
        String activityCode = abbreviation != null && stageCode != null
                && abbreviation.startsWith(stageCode + "-")
                ? abbreviation.substring(stageCode.length() + 1)
                : abbreviation;

        List<ConclusionResponse> conclusions = activity.getConclusions().stream()
                .map(c -> ConclusionResponse.builder()
                        .code(c.getCode())
                        .name(c.getName())
                        .documentation(null)
                        .build())
                .collect(Collectors.toList());

        return ActivityNodeResponse.builder()
                .stageCode(stageCode)
                .activityCode(activityCode)
                .name(activity.getName())
                .documentation(null)
                .abbreviation(abbreviation)
                .apiHandler(apiHandler)
                .conclusions(conclusions)
                .build();
    }

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