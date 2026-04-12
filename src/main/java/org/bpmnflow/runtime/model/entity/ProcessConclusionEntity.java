package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "process_conclusion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessConclusionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conclusion_id")
    private Long conclusionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private ProcessActivityEntity activity;

    @Column(name = "code", nullable = false, length = 200)
    private String code;

    @Column(name = "name", length = 400)
    private String name;
}
