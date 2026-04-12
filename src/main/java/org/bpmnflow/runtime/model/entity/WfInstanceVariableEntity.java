package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Typed variable for a process instance.
 * Replaces {@code WfInstancePayloadEntity} by adding {@code variableType},
 * which controls validation and conversion of values stored as VARCHAR.
 */
@Entity
@Table(name = "wf_instance_variable")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WfInstanceVariableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variable_id")
    private Long variableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private WfProcessInstanceEntity instance;

    @Column(name = "variable_key", nullable = false, length = 200)
    private String variableKey;

    /**
     * Variable type — determines validation on write and conversion on read.
     * Persisted as a STRING in the database (e.g. "INTEGER"), mapped by the {@link VariableType} enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "variable_type", nullable = false, length = 20)
    @Builder.Default
    private VariableType variableType = VariableType.STRING;

    /**
     * Serialized value. Always stored as VARCHAR(4000).
     * Interpreted according to {@code variableType}.
     */
    @Column(name = "variable_value", length = 4000)
    private String variableValue;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.variableType == null) this.variableType = VariableType.STRING;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}