package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Summary of a workflow instance for listing purposes.
 * Does not include activity history or variables — use GET /{instanceId} for the full state.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowSummaryResponse {

    private Long instanceId;
    private String externalId;
    private String instanceStatus;
    private String processStatus;

    private Long versionId;
    private Integer versionNumber;
    private String processKey;
    private String processName;

    /** Abbreviation of the currently active activity. Null when the instance is completed. */
    private String currentActivityAbbreviation;
    private String currentActivityName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}