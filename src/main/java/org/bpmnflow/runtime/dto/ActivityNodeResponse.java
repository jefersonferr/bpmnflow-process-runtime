package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

/**
 * JSON shape identical to {@code ActivityNode} / {@code ApiActivityNode} from bpmnflow-core.
 *
 * <p>Field order matches bpmnflow-core's {@code ActivityNode}:</p>
 * <pre>
 *   stageCode, activityCode, name, documentation, abbreviation, conclusions
 * </pre>
 * <p>When the activity is a {@code <camunda:serviceTask>} with a {@code <camunda:connector>},
 * an {@code apiHandler} block is included — identical to what
 * {@code GET /process/activities} returns in the bpmnflow-spring-boot-starter.</p>
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityNodeResponse {

    private String stageCode;
    private String activityCode;
    private String name;
    private String documentation;
    private String abbreviation;

    /**
     * Populated only for service tasks with a {@code <camunda:connector>}.
     * {@code null} for plain tasks — Jackson omits the field when
     * {@code spring.jackson.default-property-inclusion: non_null} is active.
     */
    private ApiHandlerResponse apiHandler;

    private List<ConclusionResponse> conclusions;

    // ---------------------------------------------------------------
    // Nested types — identical shape to the starter's ApiActivityNode
    // ---------------------------------------------------------------

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConclusionResponse {
        private String code;
        private String name;
        private String documentation;
    }

    /**
     * Mirrors the {@code apiHandler} field of {@code ApiActivityNode}.
     *
     * <p>Populated from {@code bpmn_extension_property} rows stored by
     * {@code StructuralPersistor.persistConnectorProperties()} under the
     * {@code connector.*} namespace:</p>
     * <ul>
     *   <li>{@code connector.id}            → connectorId</li>
     *   <li>{@code connector.input.url}     → endpoint</li>
     *   <li>{@code connector.input.method}  → method</li>
     *   <li>{@code connector.input.<name>}  → inputMappings entry</li>
     *   <li>{@code connector.output.<name>} → outputMappings entry</li>
     * </ul>
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApiHandlerResponse {
        private String         connectorId;
        private String         endpoint;
        private String         method;
        private Integer        retries;
        private List<KeyValue> taskHeaders;
        private List<KeyValue> inputMappings;
        private List<KeyValue> outputMappings;
    }

    /** Generic key-value pair used in inputMappings and outputMappings. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class KeyValue {
        private String key;
        private String value;
    }
}