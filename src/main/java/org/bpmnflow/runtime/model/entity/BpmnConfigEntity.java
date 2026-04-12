package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bpmn_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    @Column(name = "config_version", nullable = false, length = 50)
    private String configVersion;

    @Column(name = "engine", length = 50)
    private String engine;

    @Lob
    @Column(name = "config_yaml")
    private String configYaml;

    @Column(name = "config_hash", length = 64)
    private String configHash;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BpmnConfigPropertyEntity> properties = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
