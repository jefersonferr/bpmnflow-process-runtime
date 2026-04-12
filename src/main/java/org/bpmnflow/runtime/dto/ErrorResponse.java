package org.bpmnflow.runtime.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardised error response returned on all 4xx and 5xx responses")
public class ErrorResponse {

    @Schema(description = "Timestamp of the error")
    private final LocalDateTime timestamp;

    @Schema(description = "HTTP status code")
    private final int status;

    @Schema(description = "Short error classification")
    private final String error;

    @Schema(description = "Human-readable error message")
    private final String message;

    @Schema(description = "Request path that triggered the error")
    private final String path;
}