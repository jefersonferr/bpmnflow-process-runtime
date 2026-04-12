package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Elemento universal do BPMN: task, startEvent, endEvent, exclusiveGateway,
 * parallelGateway, boundaryEvent, subProcess, etc.
 *
 * <p>{@code lane_id} é nullable — gateways e boundary events frequentemente
 * não têm referência explícita de lane no XML.
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
     * Lane à qual este elemento pertence.
     * Determinado via {@code flowNodeRef} nas lanes do XML.
     * Nullable para elementos sem lane explícita.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id")
    private BpmnLaneEntity lane;

    /** ID do elemento no BPMN XML (ex: {@code Activity_0101spn}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    /**
     * Tipo do elemento: {@code task}, {@code startEvent}, {@code endEvent},
     * {@code exclusiveGateway}, {@code parallelGateway}, {@code boundaryEvent}, etc.
     */
    @Column(name = "element_type", nullable = false, length = 50)
    private String elementType;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "documentation", length = 4000)
    private String documentation;

}