package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
/**
 * Lane dentro de um Participant BPMN.
 * Contém os IDs dos elementos ({@code flowNodeRef}) que pertencem a ela.
 */
@Entity
@Table(name = "bpmn_lane")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnLaneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lane_id")
    private Long laneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private BpmnParticipantEntity participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /** ID do elemento no BPMN XML (ex: {@code Lane_0rq21io}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;

}