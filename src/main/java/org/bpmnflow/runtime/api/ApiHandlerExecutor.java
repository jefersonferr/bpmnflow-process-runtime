package org.bpmnflow.runtime.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bpmnflow.runtime.model.entity.BpmnExtensionPropertyEntity;
import org.bpmnflow.runtime.model.entity.ProcessActivityEntity;
import org.bpmnflow.runtime.model.entity.VariableType;
import org.bpmnflow.runtime.repository.BpmnExtensionPropertyRepository;
import org.bpmnflow.runtime.repository.WfInstanceVariableRepository;
import org.bpmnflow.runtime.service.VariableUpsertHelper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full API-call lifecycle for a {@link ProcessActivityEntity}
 * that is an API-handler service task.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Detect whether the activity has API-handler extension properties
 *       ({@code connectorId}, {@code endpoint}, {@code method}) in
 *       {@code bpmn_extension_property}.</li>
 *   <li>Load the current instance variables from {@code wf_instance_variable}
 *       for placeholder resolution.</li>
 *   <li>Build the {@link ApiHandlerContext} and delegate to the active
 *       {@link ApiHandlerProvider}.</li>
 *   <li>Persist the response fields returned by the provider back to
 *       {@code wf_instance_variable} via {@link VariableUpsertHelper}.</li>
 * </ol>
 *
 * <p>Extension properties are stored in {@code bpmn_extension_property} with
 * {@code owner_type = 'ELEMENT'} and {@code owner_id} matching the activity's
 * {@code element_id}. The following property names are recognised:</p>
 * <ul>
 *   <li>{@code connectorId} — connector / job type identifier (presence triggers API execution)</li>
 *   <li>{@code endpoint}    — full URL of the API endpoint</li>
 *   <li>{@code method}      — HTTP method (GET, POST, PUT, PATCH, DELETE)</li>
 *   <li>{@code outputMapping.<variableName>} — JSONPath expression to extract from the response</li>
 * </ul>
 *
 * <p>If the activity has no {@code connectorId} property this executor is a no-op —
 * the activity is treated as a plain human/manual task.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiHandlerExecutor {

    static final String OWNER_TYPE_ELEMENT      = "ELEMENT";
    static final String PROP_CONNECTOR_ID       = "connectorId";
    static final String PROP_ENDPOINT           = "endpoint";
    static final String PROP_METHOD             = "method";
    static final String PROP_PAYLOAD_TEMPLATE   = "payloadTemplate";
    static final String PROP_OUTPUT_MAPPING_PFX = "outputMapping.";

    private final BpmnExtensionPropertyRepository extPropRepo;
    private final WfInstanceVariableRepository    variableRepo;
    private final VariableUpsertHelper            variableUpsertHelper;
    private final ApiHandlerProvider              apiHandlerProvider;

    /**
     * Executes the API call for the given activity within the given instance,
     * if and only if the activity carries API-handler extension properties.
     *
     * <p>This method is a no-op when the activity is not a service task —
     * i.e. when {@code connectorId} is absent from its extension properties.</p>
     *
     * @param instanceId the process instance identifier
     * @param activity   the activity about to be executed
     * @throws ApiHandlerException if the API call fails (Opção A: fail fast)
     */
    public void executeIfApiActivity(Long instanceId, ProcessActivityEntity activity) {
        if (activity.getElement() == null) return;

        Long elementId = activity.getElement().getElementId();
        List<BpmnExtensionPropertyEntity> props =
                extPropRepo.findByOwnerTypeAndOwnerId(OWNER_TYPE_ELEMENT, elementId);

        if (props.isEmpty()) return;

        Map<String, String> propMap = toMap(props);
        String connectorId = propMap.get(PROP_CONNECTOR_ID);

        // Not an API activity — plain task, nothing to do
        if (connectorId == null || connectorId.isBlank()) return;

        String endpoint = propMap.get(PROP_ENDPOINT);
        String method   = propMap.get(PROP_METHOD);

        if (endpoint == null || endpoint.isBlank()) {
            throw new ApiHandlerException(
                    "Activity '%s' has connectorId '%s' but is missing required property 'endpoint'"
                            .formatted(activity.getAbbreviation(), connectorId));
        }
        if (method == null || method.isBlank()) {
            throw new ApiHandlerException(
                    "Activity '%s' has connectorId '%s' but is missing required property 'method'"
                            .formatted(activity.getAbbreviation(), connectorId));
        }

        Map<String, String> instanceVariables = loadInstanceVariables(instanceId);

        List<ApiHandlerContext.OutputMapping> outputMappings = propMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROP_OUTPUT_MAPPING_PFX))
                .map(e -> new ApiHandlerContext.OutputMapping(
                        e.getKey().substring(PROP_OUTPUT_MAPPING_PFX.length()),
                        e.getValue()))
                .toList();

        ApiHandlerContext context = ApiHandlerContext.builder()
                .instanceId(instanceId)
                .activityAbbreviation(activity.getAbbreviation())
                .endpoint(endpoint)
                .method(method)
                .headers(buildHeaderMap(propMap))
                .payloadTemplate(propMap.get(PROP_PAYLOAD_TEMPLATE))
                .instanceVariables(instanceVariables)
                .outputMappings(outputMappings)
                .build();

        log.info("Executing API activity '{}' via {} (instance={})",
                activity.getAbbreviation(), apiHandlerProvider.providerName(), instanceId);

        Map<String, String> responseValues = apiHandlerProvider.execute(context);

        persistResponseVariables(instanceId, responseValues);

        log.info("API activity '{}' completed — {} variable(s) persisted (instance={})",
                activity.getAbbreviation(), responseValues.size(), instanceId);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private Map<String, String> toMap(List<BpmnExtensionPropertyEntity> props) {
        Map<String, String> map = new HashMap<>();
        props.forEach(p -> map.put(p.getPropertyName(), p.getPropertyValue()));
        return map;
    }

    /**
     * Extracts header properties from the extension property map.
     * Headers are stored with the prefix {@code header.} (e.g. {@code header.Authorization}).
     */
    private Map<String, String> buildHeaderMap(Map<String, String> propMap) {
        Map<String, String> headers = new HashMap<>();
        propMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith("header."))
                .forEach(e -> headers.put(
                        e.getKey().substring("header.".length()),
                        e.getValue()));
        return headers;
    }

    private Map<String, String> loadInstanceVariables(Long instanceId) {
        Map<String, String> vars = new HashMap<>();
        variableRepo.findByInstance_InstanceId(instanceId)
                .forEach(v -> vars.put(v.getVariableKey(), v.getVariableValue()));
        return vars;
    }

    private void persistResponseVariables(Long instanceId, Map<String, String> responseValues) {
        responseValues.forEach((key, value) -> {
            if (value != null) {
                variableUpsertHelper.upsert(instanceId, key, VariableType.STRING.name(), value);
            }
        });
    }
}