package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bpmn_process_version")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnProcessVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private BpmnProcessEntity process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private BpmnConfigEntity config;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "version_tag", length = 50)
    private String versionTag;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "process_type", length = 100)
    private String processType;

    @Column(name = "process_subtype", length = 100)
    private String processSubtype;

    @Lob
    @Column(name = "documentation")
    private String documentation;

    @Lob
    @Column(name = "bpmn_xml", nullable = false)
    private String bpmnXml;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "parsed_at", updatable = false)
    private LocalDateTime parsedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ---- Layer 3: Structural Elements ----

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BpmnParticipantEntity> participants = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BpmnElementEntity> elements = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BpmnSequenceFlowEntity> sequenceFlows = new ArrayList<>();

    // ---- Layer 4: Derived Data ----

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessStageEntity> stages = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessActivityEntity> activities = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessRuleEntity> rules = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessInconsistencyEntity> inconsistencies = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.parsedAt = LocalDateTime.now();
        if (this.status == null) this.status = "DRAFT";
    }
}