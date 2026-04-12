package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "process_activity")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    /**
     * Source structural element in the XML.
     * Links the derived activity (Layer 4) to the raw structural element (Layer 3).
     * Nullable for activities with no direct corresponding BPMN element.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "element_id")
    private BpmnElementEntity element;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "abbreviation", nullable = false, length = 100)
    private String abbreviation;

    @Column(name = "stage_code", length = 100)
    private String stageCode;

    @Column(name = "lane_name", length = 400)
    private String laneName;

    @Column(name = "display_order")
    private Integer displayOrder;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessConclusionEntity> conclusions = new ArrayList<>();
}