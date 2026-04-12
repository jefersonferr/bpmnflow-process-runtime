package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StartProcessRequest {

    private String externalId;

    /**
     * Initial variables for the instance.
     * Each entry may declare its own type; the service validates before persisting.
     */
    private List<VariableRequest> variables;
}