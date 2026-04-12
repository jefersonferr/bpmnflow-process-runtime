package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessInstanceResponse {

    private Long instanceId;
    private String externalId;
    private String instanceStatus;
    private String processStatus;

    private Long versionId;
    private Integer versionNumber;
    private String versionTag;
    private String processType;
    private String processSubtype;

    private ActivityStepResponse currentActivity;
    private List<ActivityHistoryEntry> activityHistory;

    /**
     * Instance variables with their declared type and converted value.
     */
    private List<VariableResponse> variables;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActivityHistoryEntry {
        private Integer stepNumber;

        /**
         * BPMN ID of the source structural element (e.g. {@code Activity_0101spn}).
         * Nullable when the activity has no linked structural element.
         */
        private String elementBpmnId;

        private String abbreviation;
        private String activityName;
        private String status;
        private String conclusionCode;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }
}