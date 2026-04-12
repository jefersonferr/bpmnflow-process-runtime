package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Generic extension property (EAV) for any BPMN element.
 *
 * <p>Intentional design: manual polymorphic reference via {@code ownerType} + {@code ownerId},
 * with no JPA foreign keys to the owner entities. This avoids the problem of multiple
 * {@code @ManyToOne} mappings pointing to the same {@code owner_id} column,
 * which Hibernate rejects.
 *
 * <p>{@code ownerType} discriminates the source table:
 * <ul>
 *   <li>{@code PROCESS}       → versionId of {@link BpmnProcessVersionEntity}</li>
 *   <li>{@code PARTICIPANT}   → participantId of {@link BpmnParticipantEntity}</li>
 *   <li>{@code LANE}          → laneId of {@link BpmnLaneEntity}</li>
 *   <li>{@code ELEMENT}       → elementId of {@link BpmnElementEntity}</li>
 *   <li>{@code SEQUENCE_FLOW} → flowId of {@link BpmnSequenceFlowEntity}</li>
 * </ul>
 *
 * <p>To navigate to the owner, use the repository matching {@code ownerType}
 * and query by {@code ownerId} — there is no direct JPA navigation by design.
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
     * Owner type: PROCESS, PARTICIPANT, LANE, ELEMENT, SEQUENCE_FLOW.
     * Combined with {@code ownerId} forms the complete polymorphic reference.
     */
    @Column(name = "owner_type", nullable = false, length = 50)
    private String ownerType;

    /**
     * Primary key of the owner in its table.
     * Interpreted according to {@code ownerType}.
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(name = "property_value", length = 4000)
    private String propertyValue;
}