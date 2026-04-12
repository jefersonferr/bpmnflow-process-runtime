package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Variável tipada de uma instância de processo.
 * Substitui {@code WfInstancePayloadEntity} adicionando {@code variableType}
 * que controla a validação e conversão do valor armazenado como VARCHAR2.
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
     * Tipo da variável — determina a validação ao gravar e a conversão ao ler.
     * Persiste como STRING no banco (ex: "INTEGER"), mapeado pelo enum {@link VariableType}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "variable_type", nullable = false, length = 20)
    @Builder.Default
    private VariableType variableType = VariableType.STRING;

    /**
     * Valor serializado. Sempre armazenado como VARCHAR2(4000).
     * Interpretado conforme {@code variableType}.
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