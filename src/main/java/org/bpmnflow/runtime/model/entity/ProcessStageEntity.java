package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "process_stage")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessStageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long stageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", length = 400)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;
}
