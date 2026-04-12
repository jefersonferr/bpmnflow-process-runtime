package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pool/Participant of the BPMN diagram.
 * Extracted directly from the XML via DOM parsing of the stored {@code bpmn_xml}.
 */
@Entity
@Table(name = "bpmn_participant")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /** Element ID in the BPMN XML (e.g. {@code Participant_pizza}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    @Column(name = "name", length = 400)
    private String name;

    /** Value of the {@code processRef} attribute in the XML. */
    @Column(name = "process_ref", length = 200)
    private String processRef;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<BpmnLaneEntity> lanes = new ArrayList<>();
}