package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompleteActivityRequest {

    private String conclusionCode;

    /**
     * Variables to write or update when completing the activity.
     * Each entry may declare its own type; the service validates before persisting.
     */
    private List<VariableRequest> variables;
}