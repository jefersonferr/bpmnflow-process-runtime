package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeployResponse {

    private String message;
    private String processKey;
    private Long versionId;
    private Integer versionNumber;
    private String versionTag;
    private String processType;
    private String processSubtype;
    private boolean valid;

    // ---- Layer 3: Structural ----
    private int participantCount;
    private int laneCount;
    private int elementCount;
    private int sequenceFlowCount;

    // ---- Layer 4: Derived ----
    private int stageCount;
    private int activityCount;
    private int ruleCount;
    private int inconsistencyCount;
    private List<String> inconsistencies;
}
