package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Summary of a process with its deployed versions.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessSummaryResponse {

    private Long processId;
    private String processKey;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProcessVersionSummaryResponse> versions;
}