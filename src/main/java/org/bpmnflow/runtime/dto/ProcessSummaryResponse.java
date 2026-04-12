package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resumo de um processo com suas versões deployadas.
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
