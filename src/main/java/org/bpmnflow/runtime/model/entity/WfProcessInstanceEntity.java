package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wf_process_instance")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WfProcessInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instance_id")
    private Long instanceId;

    /**
     * Optimistic locking version column.
     * Hibernate increments this on every UPDATE. If two transactions read
     * the same instance and both try to save, the second will find that the
     * row version no longer matches and throw OptimisticLockException.
     * The GlobalExceptionHandler translates OptimisticLockException → HTTP 409.
     * Clients must retry with a fresh GET to obtain the current state.
     * This protects the root entity. Because all activity step writes go
     * through a transaction that also saves WfProcessInstanceEntity (via
     * instanceRepo.save), the version check covers the full completeActivity
     * operation — not just the instance row itself.
     */
    @Version
    @Column(name = "occ_version", nullable = false)
    @Builder.Default
    private Long occVersion = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InstanceStatus status;

    @Column(name = "process_status", length = 200)
    private String processStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    @Builder.Default
    private List<WfInstanceActivityEntity> instanceActivities = new ArrayList<>();

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WfInstanceVariableEntity> variables = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = InstanceStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}