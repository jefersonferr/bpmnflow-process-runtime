package org.bpmnflow.runtime.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Resumo de uma instância de workflow para listagem.
 * Não carrega histórico de atividades nem variáveis — use GET /{instanceId} para o estado completo.
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

    /** Abbreviation da atividade atualmente ativa. Null quando a instância está concluída. */
    private String currentActivityAbbreviation;
    private String currentActivityName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
