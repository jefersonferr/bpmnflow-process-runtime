package org.bpmnflow.runtime.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StartProcessRequest {

    private String externalId;

    /**
     * Variáveis iniciais da instância.
     * Cada item pode declarar seu próprio tipo; o serviço valida antes de persistir.
     */
    private List<VariableRequest> variables;
}