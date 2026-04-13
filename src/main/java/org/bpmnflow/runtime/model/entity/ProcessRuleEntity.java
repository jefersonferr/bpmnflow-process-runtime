package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.bpmnflow.model.RuleType;

@Entity
@Table(name = "process_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_activity_id")
    private ProcessActivityEntity sourceActivity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_activity_id")
    private ProcessActivityEntity targetActivity;

    @Column(name = "source_abbreviation", length = 100)
    private String sourceAbbreviation;

    @Column(name = "target_abbreviation", length = 100)
    private String targetAbbreviation;

    @Column(name = "conclusion_code", length = 200)
    private String conclusionCode;

    @Column(name = "process_status", length = 200)
    private String processStatus;
}
