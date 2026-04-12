package org.bpmnflow.runtime.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bpmn_config_property")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BpmnConfigPropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_prop_id")
    private Long configPropId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private BpmnConfigEntity config;

    @Column(name = "element_type", nullable = false, length = 50)
    private String elementType;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_extension", nullable = false)
    private boolean extension;
}
