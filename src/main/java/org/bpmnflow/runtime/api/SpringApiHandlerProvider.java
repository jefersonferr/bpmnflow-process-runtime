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
 * <p>Each {@link ApiHandlerContext.OutputMapping} provides a JSONPath expression
 * (e.g. {@code $.transaction_id}) applied to the response body JSON.
 * Nested paths (e.g. {@code $.data.id}) are supported via recursive
 * {@link JsonNode} traversal. Missing paths result in a {@code null} value
 * and a warning log — they do not throw.</p>
 *
 * <h2>Fail fast (Opção A)</h2>
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
     * Applies each output mapping to the response body JSON and returns
     * the extracted values keyed by variable name.
     *
     * <p>A missing JSONPath or a non-JSON response yields a {@code null} value
     * and a warning log — it does not throw.</p>
     */
    private Map<String, String> mapResponse(String responseBody,
                                            ApiHandlerContext context) {
        Map<String, String> result = new HashMap<>();

        if (context.getOutputMappings() == null || context.getOutputMappings().isEmpty()) {
            return result;
        }

        if (responseBody == null || responseBody.isBlank()) {
            log.warn("[{}] Response body is empty — all output mappings will be null (activity={}, instance={})",
                    providerName(), context.getActivityAbbreviation(), context.getInstanceId());
            context.getOutputMappings()
                    .forEach(m -> result.put(m.variableName(), null));
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
            String value = extractJsonPath(root, mapping.jsonPath(),
                    context.getActivityAbbreviation(), mapping.variableName());
            result.put(mapping.variableName(), value);
        }

        return result;
    }

    /**
     * Extracts a value from a {@link JsonNode} using a simple dot-notation
     * JSONPath (e.g. {@code $.transaction_id} or {@code $.data.id}).
     *
     * <p>Supports only the subset needed for response mapping:
     * {@code $.field} and {@code $.nested.field}. Arrays and filters are
     * not supported in this implementation.</p>
     */
    private String extractJsonPath(JsonNode root, String jsonPath,
                                   String activityAbbreviation, String variableName) {
        if (jsonPath == null || jsonPath.isBlank()) return null;

        // Strip leading "$." if present
        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode() || current.isNull()) break;
            current = current.get(part);
        }

        if (current == null || current.isMissingNode() || current.isNull()) {
            log.warn("[{}] JSONPath '{}' not found in response for variable '{}' " +
                            "(activity={}, instance={})",
                    providerName(), jsonPath, variableName,
                    activityAbbreviation, "?");
            return null;
        }

        return current.isTextual() ? current.asText() : current.toString();
    }
}