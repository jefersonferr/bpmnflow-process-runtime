package org.bpmnflow.runtime.dto;

import lombok.*;
import org.bpmnflow.runtime.model.entity.VariableType;

/**
 * Represents an individual variable submitted by the client.
 *
 * <p>When {@code type} is omitted, the service defaults to {@link VariableType#STRING}.
 * The service validates that {@code value} is compatible with the declared {@code type}
 * before persisting.
 *
 * <p>Valid examples:
 * <pre>
 * { "key": "customer_name",  "value": "John Smith" }
 * { "key": "total_amount",   "type": "FLOAT",   "value": "149.90" }
 * { "key": "item_count",     "type": "INTEGER", "value": "3" }
 * { "key": "is_priority",    "type": "BOOLEAN", "value": "true" }
 * { "key": "delivery_date",  "type": "DATE",    "value": "2025-12-31" }
 * { "key": "address",        "type": "JSON",    "value": "{\"street\":\"Main St\",\"number\":42}" }
 * </pre>
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VariableRequest {

    private String key;

    /**
     * Variable type. Optional — defaults to {@code STRING} if not provided.
     */
    @Builder.Default
    private VariableType type = VariableType.STRING;

    private String value;
}