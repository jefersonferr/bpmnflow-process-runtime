package org.bpmnflow.runtime.api;

import java.util.Map;

/**
 * SPI (Service Provider Interface) for executing API calls during process
 * instance activity execution.
 *
 * <p>The default implementation is {@link SpringApiHandlerProvider} — a pure
 * Java, database-agnostic provider using {@code RestTemplate}. It is registered
 * as a Spring bean with {@code @ConditionalOnMissingBean}, so any application
 * that declares its own {@code ApiHandlerProvider} bean will automatically
 * replace it without any additional configuration.</p>
 *
 * <p>The {@code bpmnflow-process-runtime-oracle} project provides richer
 * implementations that delegate to Oracle-specific features:</p>
 * <ul>
 *   <li>{@code PlSqlApiHandlerProvider} — calls via {@code UTL_HTTP} stored procedure</li>
 *   <li>{@code McpApiHandlerProvider}   — delegates to an MCP Server</li>
 *   <li>{@code SelectAiApiHandlerProvider} — uses {@code DBMS_CLOUD_AI_AGENT}</li>
 * </ul>
 *
 * <p>Provider selection in the Oracle project follows a priority chain:</p>
 * <pre>
 * SelectAiApiHandlerProvider (if Oracle 26ai Autonomous available)
 *   → PlSqlApiHandlerProvider (if Oracle 19c+ with ACL configured)
 *     → McpApiHandlerProvider (if MCP Server configured)
 *       → SpringApiHandlerProvider (universal fallback)
 * </pre>
 */
public interface ApiHandlerProvider {

    /**
     * Executes an API call for the given activity and returns the response
     * fields to be persisted as instance variables.
     *
     * @param context execution context carrying all data needed for the call
     * @return map of variable key → value to be persisted via
     *         {@code outputMappings}; may be empty, never null
     * @throws ApiHandlerException if the API call fails for any reason
     *         (connection error, non-2xx response, response mapping failure)
     */
    Map<String, String> execute(ApiHandlerContext context);

    /**
     * Human-readable identifier for logging and diagnostic purposes.
     * Example: {@code "SpringApiHandlerProvider"}, {@code "PlSqlApiHandlerProvider"}.
     */
    String providerName();
}