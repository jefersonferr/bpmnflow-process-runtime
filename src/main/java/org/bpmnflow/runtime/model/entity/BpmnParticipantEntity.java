package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pool/Participant do diagrama BPMN.
 * Extraído diretamente do XML via DOM parsing do {@code bpmn_xml} armazenado.
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

    /** ID do elemento no BPMN XML (ex: {@code Participant_pizza}). */
    @Column(name = "bpmn_id", nullable = false, length = 200)
    private String bpmnId;

    @Column(name = "name", length = 400)
    private String name;

    /** Valor do atributo {@code processRef} no XML. */
    @Column(name = "process_ref", length = 200)
    private String processRef;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<BpmnLaneEntity> lanes = new ArrayList<>();
}