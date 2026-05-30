package org.bpmnflow.runtime.api;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Immutable execution context passed to {@link ApiHandlerProvider#execute}.
 *
 * <p>Carries everything the provider needs to make the API call and map the
 * response back to process instance variables:</p>
 * <ul>
 *   <li>The call definition ({@code endpoint}, {@code method}, {@code headers},
 *       {@code payloadTemplate}) extracted from the activity's extension properties.</li>
 *   <li>The resolved instance variables ({@code instanceVariables}) for
 *       placeholder substitution in the template.</li>
 *   <li>The output mappings ({@code outputMappings}) that tell the provider
 *       which response fields to extract and under what variable name to return them.</li>
 *   <li>Diagnostic identifiers ({@code instanceId}, {@code activityAbbreviation})
 *       for logging and error messages.</li>
 * </ul>
 *
 * <h2>Placeholder resolution</h2>
 * <p>Placeholders follow the format {@code ${var.variableName}}. The provider
 * resolves them by looking up {@code variableName} in {@code instanceVariables}.</p>
 *
 * <h2>Output mappings</h2>
 * <p>Each entry in {@code outputMappings} is a pair of
 * {@code variableName → jsonPath}. The provider extracts the value at
 * {@code jsonPath} from the response body and returns it under
 * {@code variableName} in the result map.</p>
 */
@Getter
@Builder
public class ApiHandlerContext {

    /** Process instance identifier — used in log and error messages. */
    private final Long instanceId;

    /** Activity abbreviation (e.g. {@code "SC-PMT_AUTH"}) — used in log and error messages. */
    private final String activityAbbreviation;

    /** Full URL of the API endpoint to call. */
    private final String endpoint;

    /** HTTP method: GET, POST, PUT, PATCH, DELETE. */
    private final String method;

    /**
     * Static and dynamic HTTP headers.
     * Values may contain placeholders resolved from {@code instanceVariables}.
     */
    private final Map<String, String> headers;

    /**
     * Request body template. May contain {@code ${var.variableName}} placeholders
     * resolved from {@code instanceVariables} before the call is made.
     * Null or blank for GET/DELETE requests.
     */
    private final String payloadTemplate;

    /**
     * Current resolved variables of the process instance.
     * Key = variable name, value = variable value as String.
     * Used for placeholder substitution in {@code payloadTemplate} and {@code headers}.
     */
    private final Map<String, String> instanceVariables;

    /**
     * Output mappings: variable name → JSONPath expression.
     * The provider extracts each path from the JSON response and returns
     * the value under the variable name.
     * Example: {@code {"pagamento_txn_id" → "$.transaction_id"}}
     */
    private final List<OutputMapping> outputMappings;

    /**
     * Single output mapping entry.
     *
     * @param variableName name under which the extracted value is stored
     * @param jsonPath     JSONPath expression applied to the response body
     */
    public record OutputMapping(String variableName, String jsonPath) {}
}