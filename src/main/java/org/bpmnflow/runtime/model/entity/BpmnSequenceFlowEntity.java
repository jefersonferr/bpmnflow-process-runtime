package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Sequence flow connecting two BPMN elements.
 * Extracted from {@code <bpmn:sequenceFlow>} elements in the XML.
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

    /** Flow ID in the BPMN XML (e.g. {@code Flow_03ufn72}). */
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

    /** Conditional expression of the flow, if present. */
    @Column(name = "condition_expression", length = 2000)
    private String conditionExpression;
}