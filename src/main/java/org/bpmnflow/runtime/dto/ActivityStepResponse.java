package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityStepResponse {

    private Integer stepNumber;
    private Long activityId;

    /**
     * BPMN ID do elemento estrutural de origem (ex: {@code Activity_0101spn}).
     * Nullable quando a activity não possui elemento estrutural vinculado.
     */
    private String elementBpmnId;

    private String abbreviation;
    private String activityName;
    private String stageCode;
    private String laneName;
    private String status;
    private LocalDateTime startedAt;
    private List<ConclusionOption> availableConclusions;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConclusionOption {
        private String code;
        private String name;
    }
}