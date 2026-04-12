package org.bpmnflow.runtime.dto;

import lombok.*;
import org.bpmnflow.runtime.model.entity.VariableType;

/**
 * Represents an instance variable returned to the client.
 *
 * <p>{@code value} is always the raw value stored as a string.
 * {@code convertedValue} is the value already interpreted by its type:
 * Long for INTEGER, Double for FLOAT, Boolean for BOOLEAN,
 * LocalDate for DATE, JsonNode for JSON, String for STRING.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VariableResponse {

    private String key;
    private VariableType type;

    /** Raw value stored in the database (VARCHAR). */
    private String value;

    /** Value converted to the Java type corresponding to {@code type}. */
    private Object convertedValue;
}