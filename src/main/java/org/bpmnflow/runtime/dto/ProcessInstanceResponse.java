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
     * Variáveis da instância com tipo e valor convertido.
     */
    private List<VariableResponse> variables;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActivityHistoryEntry {
        private Integer stepNumber;

        /**
         * BPMN ID do elemento estrutural de origem (ex: {@code Activity_0101spn}).
         * Nullable quando a activity não possui elemento estrutural vinculado.
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