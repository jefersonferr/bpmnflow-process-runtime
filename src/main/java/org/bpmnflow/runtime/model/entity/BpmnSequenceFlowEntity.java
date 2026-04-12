package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Sequence flow conectando dois elementos BPMN.
 * Extraído dos {@code <bpmn:sequenceFlow>} do XML.
 */
@Entity
@Table(name = "bpmn_sequence_flow")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnSequenceFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flow_id")
    private Long flowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /** ID do flow no BPMN XML (ex: {@code Flow_03ufn72}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    @Column(name = "name", length = 400)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_element_id", nullable = false)
    private BpmnElementEntity sourceElement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_element_id", nullable = false)
    private BpmnElementEntity targetElement;

    /** Expressão condicional do flow, se existir. */
    @Column(name = "condition_expression", length = 2000)
    private String conditionExpression;

}