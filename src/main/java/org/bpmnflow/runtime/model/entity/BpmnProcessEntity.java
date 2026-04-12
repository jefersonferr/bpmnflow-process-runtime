package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bpmn_process")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnProcessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "process_id")
    private Long processId;

    @Column(name = "process_key", nullable = false, unique = true, length = 200)
    private String processKey;

    @Column(name = "name", nullable = false, length = 400)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL)
    @Builder.Default
    private List<BpmnProcessVersionEntity> versions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
