package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompleteActivityRequest {

    private String conclusionCode;

    /**
     * Variáveis a gravar ou atualizar ao completar a atividade.
     * Cada item pode declarar seu próprio tipo; o serviço valida antes de persistir.
     */
    private List<VariableRequest> variables;
}