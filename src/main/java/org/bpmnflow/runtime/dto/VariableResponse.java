package org.bpmnflow.runtime.dto;

import lombok.*;
import org.bpmnflow.runtime.model.entity.VariableType;

/**
 * Representa uma variável de instância retornada ao cliente.
 *
 * <p>{@code value} é sempre o valor bruto armazenado como string.
 * {@code convertedValue} é o valor já interpretado pelo tipo:
 * Long para INTEGER, Double para FLOAT, Boolean para BOOLEAN,
 * LocalDate para DATE, JsonNode para JSON, String para STRING.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VariableResponse {

    private String key;
    private VariableType type;

    /** Valor bruto armazenado no banco (VARCHAR2). */
    private String value;

    /** Valor convertido para o tipo Java correspondente ao {@code type}. */
    private Object convertedValue;
}