package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lane within a BPMN Participant.
 * Holds the IDs of the elements ({@code flowNodeRef}) that belong to it.
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

    /** Element ID in the BPMN XML (e.g. {@code Lane_0rq21io}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;
}