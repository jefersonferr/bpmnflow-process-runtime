package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "process_inconsistency")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessInconsistencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inconsistency_id")
    private Long inconsistencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BpmnProcessVersionEntity version;

    @Column(name = "inc_type", nullable = false, length = 100)
    private String incType;

    @Column(name = "element_bpmn_id", length = 200)
    private String elementBpmnId;

    @Column(name = "element_type", length = 50)
    private String elementType;

    @Column(name = "property_name", length = 200)
    private String propertyName;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;
}
