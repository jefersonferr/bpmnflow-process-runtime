package org.bpmnflow.runtime.dto;

import lombok.*;
import org.bpmnflow.runtime.model.entity.VariableType;

/**
 * Representa uma variável individual enviada pelo cliente.
 *
 * <p>Quando {@code type} é omitido, o serviço assume {@link VariableType#STRING}.
 * O serviço valida se {@code value} é compatível com o {@code type} declarado
 * antes de persistir.
 *
 * <p>Exemplos válidos:
 * <pre>
 * { "key": "customer_name",  "value": "João Silva" }
 * { "key": "total_amount",   "type": "FLOAT",   "value": "149.90" }
 * { "key": "item_count",     "type": "INTEGER", "value": "3" }
 * { "key": "is_priority",    "type": "BOOLEAN", "value": "true" }
 * { "key": "delivery_date",  "type": "DATE",    "value": "2025-12-31" }
 * { "key": "address",        "type": "JSON",    "value": "{\"street\":\"Rua A\",\"number\":42}" }
 * </pre>
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VariableRequest {

    private String key;

    /**
     * Tipo da variável. Opcional — padrão {@code STRING} se não informado.
     */
    @Builder.Default
    private VariableType type = VariableType.STRING;

    private String value;
}