package org.bpmnflow.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal fallback {@link ApiHandlerProvider} implemented in pure Java.
 *
 * <p>Works with any relational database — no Oracle-specific dependencies.</p>
 *
 * <h2>Placeholder resolution</h2>
 * <p>Placeholders follow the format {@code ${var.variableName}}.
 * Resolution looks up {@code variableName} in {@link ApiHandlerContext#getInstanceVariables()}.
 * Unknown placeholders are left as-is and logged as warnings.</p>
 *
 * <h2>Output mapping</h2>
 * <p>Each {@link ApiHandlerContext.OutputMapping} carries:</p>
 * <ul>
 *   <li>{@code variableName} — the name of the instance variable to persist
 *       (derived from {@code connector.output.<variableName>} in
 *       {@code bpmn_extension_property})</li>
 *   <li>{@code jsonPath} — the raw content of the {@code <camunda:outputParameter>}
 *       element, which is a Groovy/Spin script and NOT a standard JSONPath expression.
 *       This provider ignores the script and extracts the response field whose name
 *       matches {@code variableName} directly from the JSON response body.</li>
 * </ul>
 *
 * <p>For example, given {@code connector.output.tracking_id}, the provider looks up
 * {@code root.get("tracking_id")} in the response JSON regardless of what the
 * Groovy script says. This works correctly for all standard Camunda HTTP Connector
 * output parameters where the output variable name matches the JSON response field.</p>
 *
 * <h2>Fail fast</h2>
 * <p>Any non-2xx HTTP response or connection error throws
 * {@link ApiHandlerException}, blocking the activity transition.
 * The process instance remains {@code ACTIVE} at the current step.</p>
 *
 * <p>Registered as a Spring bean by {@link ApiHandlerAutoConfiguration}
 * with {@code @ConditionalOnMissingBean(ApiHandlerProvider.class)}.
 * Applications that provide their own {@code ApiHandlerProvider} bean
 * replace this implementation automatically.</p>
 */
@Slf4j
public class SpringApiHandlerProvider implements ApiHandlerProvider {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{var\\.([^}]+)}");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SpringApiHandlerProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper  = objectMapper;
    }

    // ---------------------------------------------------------------
    // ApiHandlerProvider
    // ---------------------------------------------------------------

    @Override
    public Map<String, String> execute(ApiHandlerContext context) {
        log.info("[{}] Executing API call: {} {} (instance={})",
                providerName(), context.getMethod(), context.getEndpoint(), context.getInstanceId());

        String resolvedPayload = resolve(context.getPayloadTemplate(), context);
        HttpHeaders httpHeaders = buildHeaders(context);

        HttpEntity<String> entity = new HttpEntity<>(resolvedPayload, httpHeaders);
        HttpMethod method = HttpMethod.valueOf(context.getMethod().toUpperCase());

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(context.getEndpoint(), method, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            throw new ApiHandlerException(
                    "API call failed for activity '%s': %s %s returned HTTP %d. Body: %s"
                            .formatted(
                                    context.getActivityAbbreviation(),
                                    context.getMethod(),
                                    context.getEndpoint(),
                                    ex.getStatusCode().value(),
                                    ex.getResponseBodyAsString()),
                    ex);
        } catch (RestClientException ex) {
            throw new ApiHandlerException(
                    "API call failed for activity '%s': could not reach %s %s. Cause: %s"
                            .formatted(
                                    context.getActivityAbbreviation(),
                                    context.getMethod(),
                                    context.getEndpoint(),
                                    ex.getMessage()),
                    ex);
        }

        log.info("[{}] API call succeeded: {} {} → HTTP {} (instance={})",
                providerName(), context.getMethod(), context.getEndpoint(),
                response.getStatusCode().value(), context.getInstanceId());

        return mapResponse(response.getBody(), context);
    }

    @Override
    public String providerName() {
        return "SpringApiHandlerProvider";
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Resolves {@code ${var.name}} placeholders in a template string.
     * Unknown placeholders are left unchanged and logged as warnings.
     */
    private String resolve(String template, ApiHandlerContext context) {
        if (template == null || template.isBlank()) return null;

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value   = context.getInstanceVariables().get(varName);
            if (value == null) {
                log.warn("[{}] Placeholder '${{var.{}}}' not found in instance variables " +
                                "(activity={}, instance={})",
                        providerName(), varName,
                        context.getActivityAbbreviation(), context.getInstanceId());
                value = matcher.group(0); // leave placeholder unchanged
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Builds the {@link HttpHeaders} from the context, resolving placeholders in values.
     */
    private HttpHeaders buildHeaders(ApiHandlerContext context) {
        HttpHeaders httpHeaders = new HttpHeaders();

        if (context.getHeaders() != null) {
            context.getHeaders().forEach((key, value) ->
                    httpHeaders.set(key, resolve(value, context)));
        }

        // Default Content-Type if not set and there is a body
        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)
                && context.getPayloadTemplate() != null
                && !context.getPayloadTemplate().isBlank()) {
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, "application/json");
        }

        return httpHeaders;
    }

    /**
     * Applies each output mapping to the response body JSON.
     *
     * <p>The {@code jsonPath} field of each {@link ApiHandlerContext.OutputMapping}
     * contains the raw Groovy/Spin script from {@code <camunda:outputParameter>} —
     * not a standard JSONPath expression. This provider ignores the script and
     * extracts the response field by {@code variableName} directly from the JSON root,
     * which matches the standard Camunda convention where the output variable name
     * equals the JSON response field name.</p>
     *
     * <p>A missing field yields a {@code null} value and a warning log — it does not throw.</p>
     */
    private Map<String, String> mapResponse(String responseBody, ApiHandlerContext context) {
        Map<String, String> result = new HashMap<>();

        if (context.getOutputMappings() == null || context.getOutputMappings().isEmpty()) {
            log.debug("[{}] No output mappings defined for activity '{}' (instance={})",
                    providerName(), context.getActivityAbbreviation(), context.getInstanceId());
            return result;
        }

        if (responseBody == null || responseBody.isBlank()) {
            log.warn("[{}] Response body is empty — all output mappings will be null " +
                            "(activity={}, instance={})",
                    providerName(), context.getActivityAbbreviation(), context.getInstanceId());
            context.getOutputMappings().forEach(m -> result.put(m.variableName(), null));
            return result;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new ApiHandlerException(
                    "Failed to parse API response as JSON for activity '%s'. Body: %s"
                            .formatted(context.getActivityAbbreviation(), responseBody),
                    ex);
        }

        for (ApiHandlerContext.OutputMapping mapping : context.getOutputMappings()) {
            // Use variableName as the JSON field name — the Groovy script in
            // mapping.jsonPath() is a Camunda Spin expression, not a JSONPath.
            String value = extractField(root, mapping.variableName(),
                    context.getActivityAbbreviation(), context.getInstanceId());
            result.put(mapping.variableName(), value);

            log.debug("[{}] Output mapping '{}' = '{}' (activity={}, instance={})",
                    providerName(), mapping.variableName(), value,
                    context.getActivityAbbreviation(), context.getInstanceId());
        }

        return result;
    }

    /**
     * Extracts a top-level field from a {@link JsonNode} by field name.
     *
     * <p>Returns the text value for scalar nodes, or the JSON string for
     * object/array nodes. Returns {@code null} if the field is absent or null.</p>
     */
    private String extractField(JsonNode root, String fieldName,
                                String activityAbbreviation, Long instanceId) {
        JsonNode node = root.get(fieldName);

        if (node == null || node.isNull() || node.isMissingNode()) {
            log.warn("[{}] Field '{}' not found in response JSON " +
                            "(activity={}, instance={})",
                    providerName(), fieldName, activityAbbreviation, instanceId);
            return null;
        }

        return node.isTextual() ? node.asText() : node.toString();
    }
}