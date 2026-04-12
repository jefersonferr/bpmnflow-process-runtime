package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Extension property genérica (EAV) para qualquer elemento BPMN.
 *
 * <p>Design intencional: referência polimórfica manual via {@code ownerType} + {@code ownerId},
 * sem FKs JPA para os donos. Isso evita o problema de múltiplos {@code @ManyToOne}
 * apontando para a mesma coluna {@code owner_id}, que o Hibernate rejeita.
 *
 * <p>{@code ownerType} discrimina a tabela de origem:
 * <ul>
 *   <li>{@code PROCESS}       → versionId da {@link BpmnProcessVersionEntity}</li>
 *   <li>{@code PARTICIPANT}   → participantId da {@link BpmnParticipantEntity}</li>
 *   <li>{@code LANE}          → laneId da {@link BpmnLaneEntity}</li>
 *   <li>{@code ELEMENT}       → elementId da {@link BpmnElementEntity}</li>
 *   <li>{@code SEQUENCE_FLOW} → flowId da {@link BpmnSequenceFlowEntity}</li>
 * </ul>
 *
 * <p>Para navegar até o dono, use o repositório correspondente ao {@code ownerType}
 * buscando por {@code ownerId} — não há navegação JPA direta por design.
 */
@Entity
@Table(name = "bpmn_extension_property")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnExtensionPropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ext_prop_id")
    private Long extPropId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /**
     * Tipo do dono: PROCESS, PARTICIPANT, LANE, ELEMENT, SEQUENCE_FLOW.
     * Combinado com {@code ownerId} forma a referência polimórfica completa.
     */
    @Column(name = "owner_type", nullable = false, length = 50)
    private String ownerType;

    /**
     * PK do dono na sua tabela.
     * Interpretado conforme {@code ownerType}.
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(name = "property_value", length = 4000)
    private String propertyValue;
}