package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Universal BPMN element: task, startEvent, endEvent, exclusiveGateway,
 * parallelGateway, boundaryEvent, subProcess, etc.
 *
 * <p>{@code lane_id} is nullable — gateways and boundary events frequently
 * have no explicit lane reference in the XML.
 */
@Entity
@Table(name = "bpmn_element")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnElementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "element_id")
    private Long elementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /**
     * Lane this element belongs to.
     * Resolved via {@code flowNodeRef} in the XML lane definitions.
     * Nullable for elements with no explicit lane reference.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id")
    private BpmnLaneEntity lane;

    /** Element ID in the BPMN XML (e.g. {@code Activity_0101spn}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    /**
     * Element type: {@code task}, {@code startEvent}, {@code endEvent},
     * {@code exclusiveGateway}, {@code parallelGateway}, {@code boundaryEvent}, etc.
     */
    @Column(name = "element_type", nullable = false, length = 50)
    private String elementType;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "documentation", length = 4000)
    private String documentation;
}