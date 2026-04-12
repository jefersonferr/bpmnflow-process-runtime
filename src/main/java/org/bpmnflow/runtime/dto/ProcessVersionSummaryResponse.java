package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Summary of a process version for listing purposes.
 * Does not include bpmn_xml — use the deploy response for the full content.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessVersionSummaryResponse {

    private Long versionId;
    private Integer versionNumber;
    private String versionTag;
    private String status;
    private String processType;
    private String processSubtype;
    private boolean valid;
    private int inconsistencyCount;

    private int participantCount;
    private int laneCount;
    private int elementCount;
    private int sequenceFlowCount;
    private int stageCount;
    private int activityCount;
    private int ruleCount;

    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
}