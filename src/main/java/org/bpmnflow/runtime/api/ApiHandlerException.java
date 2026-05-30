package org.bpmnflow.runtime.api;

/**
 * Thrown by {@link ApiHandlerProvider} implementations when an API call
 * fails during activity execution.
 *
 * <p>Treated as HTTP 502 Bad Gateway by {@link org.bpmnflow.runtime.GlobalExceptionHandler}
 * — the upstream API responded with an error or was unreachable. The process
 * instance remains {@code ACTIVE} at the same activity step (Opção A: fail fast).</p>
 *
 * <p>The message always includes:</p>
 * <ul>
 *   <li>the activity abbreviation</li>
 *   <li>the endpoint that was called</li>
 *   <li>the HTTP status or connection error</li>
 *   <li>the response body, if available</li>
 * </ul>
 */
public class ApiHandlerException extends RuntimeException {

    public ApiHandlerException(String message) {
        super(message);
    }

    public ApiHandlerException(String message, Throwable cause) {
        super(message, cause);
    }
}