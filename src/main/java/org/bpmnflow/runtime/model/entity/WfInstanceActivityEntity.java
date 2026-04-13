package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wf_instance_activity")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WfInstanceActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inst_activity_id")
    private Long instActivityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private WfProcessInstanceEntity instance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private ProcessActivityEntity activity;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ActivityStepStatus status;

    @Column(name = "conclusion_code", length = 200)
    private String conclusionCode;

    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        this.startedAt = LocalDateTime.now();
        if (this.status == null) this.status = ActivityStepStatus.ACTIVE;
    }
}
