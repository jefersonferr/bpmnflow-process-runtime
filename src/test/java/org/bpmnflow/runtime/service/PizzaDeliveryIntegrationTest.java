package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.repository.BpmnProcessVersionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes de integração do fluxo completo do processo pizza-delivery.
 *
 * Sobe o contexto Spring completo com:
 * - H2 em memória (MODE=Oracle)
 * - Liquibase aplicando as migrations reais (V001–V005)
 * - BpmnDeployService parseando o pizza-delivery.bpmn real
 * - ProcessInstanceService executando o fluxo contra o banco
 *
 * Cada teste recebe um contexto limpo via @DirtiesContext para garantir
 * isolamento completo entre cenários.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integração — Pizza Delivery Process")
class PizzaDeliveryIntegrationTest {

    @Autowired private BpmnDeployService    deployService;
    @Autowired private ProcessInstanceService instanceService;
    @Autowired private BpmnProcessVersionRepository versionRepo;

    private Long versionId;

    @BeforeEach
    void deploy() throws Exception {
        byte[] bpmn   = Files.readAllBytes(
                Paths.get(getClass().getClassLoader().getResource("pizza-delivery.bpmn").toURI()));
        byte[] config = Files.readAllBytes(
                Paths.get(getClass().getClassLoader().getResource("bpmn-config.yaml").toURI()));

        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");
        versionId = result.getVersion().getVersionId();
    }

    // ===============================================================
    // Helpers
    // ===============================================================

    private ProcessInstanceResponse start() {
        return instanceService.startProcess(versionId, null);
    }

    private ProcessInstanceResponse complete(Long instanceId, String conclusionCode) {
        return instanceService.completeActivity(instanceId,
                CompleteActivityRequest.builder().conclusionCode(conclusionCode).build());
    }

    private ProcessInstanceResponse complete(Long instanceId) {
        return instanceService.completeActivity(instanceId,
                CompleteActivityRequest.builder().build());
    }

    // ===============================================================
    // Cenário 1 — Fluxo feliz com pagamento
    // SEL → ORD → RCV(ORDER_CONFIRMED) → BAK(READY_FOR_DELIVERY)
    //   → DLV(COLLECT_PAYMENT) → PMT → RCP → EAT → COMPLETED/CLOSED
    // ===============================================================

    @Nested
    @DisplayName("Cenário 1 — Fluxo feliz com pagamento")
    class FluxoFelizComPagamento {

        @Test
        @DisplayName("start → CS-SEL com processStatus NEW")
        void startDeveIniciarEmSelectPizza() {
            ProcessInstanceResponse resp = start();

            assertThat(resp.getInstanceStatus()).isEqualTo("ACTIVE");
            assertThat(resp.getProcessStatus()).isEqualTo("NEW");
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-SEL");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(1);
            assertThat(resp.getCurrentActivity().getAvailableConclusions()).isEmpty();
        }

        @Test
        @DisplayName("CS-SEL → CS-ORD sem conclusão")
        void selParaOrd() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            resp = complete(id);

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-ORD");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("CS-ORD → SC-RCV → RCV(ORDER_CONFIRMED) → CH-BAK com status IN_PREPARATION")
        void rcvOrderConfirmedParaBak() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);           // SEL → ORD
            complete(id);           // ORD → RCV

            resp = complete(id, "ORDER_CONFIRMED"); // RCV → BAK

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
            assertThat(resp.getCurrentActivity().getAvailableConclusions())
                    .extracting("code")
                    .containsExactlyInAnyOrder("READY_FOR_DELIVERY", "NOT_READY");
        }

        @Test
        @DisplayName("CH-BAK(READY_FOR_DELIVERY) → DL-DLV com status OUT_FOR_DELIVERY")
        void bakReadyParaDlv() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "ORDER_CONFIRMED");        // RCV → BAK

            resp = complete(id, "READY_FOR_DELIVERY"); // BAK → DLV

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("DL-DLV");
            assertThat(resp.getProcessStatus()).isEqualTo("OUT_FOR_DELIVERY");
            assertThat(resp.getCurrentActivity().getAvailableConclusions())
                    .extracting("code")
                    .containsExactlyInAnyOrder("COLLECT_PAYMENT", "ONLY_DELIVERY");
        }

        @Test
        @DisplayName("DL-DLV(COLLECT_PAYMENT) → DL-PMT → DL-RCP → CS-EAT → COMPLETED/CLOSED")
        void fluxoCompletoComPagamento() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "ORDER_CONFIRMED");        // RCV → BAK
            complete(id, "READY_FOR_DELIVERY");     // BAK → DLV
            complete(id, "COLLECT_PAYMENT");        // DLV → PMT

            resp = complete(id);                    // PMT → RCP
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("DL-RCP");

            resp = complete(id);                    // RCP → EAT
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-EAT");

            resp = complete(id);                    // EAT → END

            assertThat(resp.getInstanceStatus()).isEqualTo("COMPLETED");
            assertThat(resp.getProcessStatus()).isEqualTo("CLOSED");
            assertThat(resp.getCurrentActivity()).isNull();
            assertThat(resp.getActivityHistory()).hasSize(8);
            assertThat(resp.getActivityHistory())
                    .extracting("abbreviation")
                    .containsExactly("CS-SEL", "CS-ORD", "SC-RCV", "CH-BAK",
                                     "DL-DLV", "DL-PMT", "DL-RCP", "CS-EAT");
        }
    }

    // ===============================================================
    // Cenário 2 — Prepaid / ONLY_DELIVERY
    // DLV(ONLY_DELIVERY) → EAT → COMPLETED, pulando PMT e RCP
    // ===============================================================

    @Nested
    @DisplayName("Cenário 2 — Prepaid (ONLY_DELIVERY)")
    class FluxoPrepaid {

        @Test
        @DisplayName("DL-DLV(ONLY_DELIVERY) → CS-EAT → COMPLETED pulando PMT e RCP")
        void dlvOnlyDeliveryParaEatSemPagamento() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "ORDER_CONFIRMED");        // RCV → BAK
            complete(id, "READY_FOR_DELIVERY");     // BAK → DLV

            resp = complete(id, "ONLY_DELIVERY");   // DLV → EAT (pula PMT e RCP)
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-EAT");

            resp = complete(id);                    // EAT → END

            assertThat(resp.getInstanceStatus()).isEqualTo("COMPLETED");
            assertThat(resp.getProcessStatus()).isEqualTo("CLOSED");
            assertThat(resp.getActivityHistory()).hasSize(6);
            assertThat(resp.getActivityHistory())
                    .extracting("abbreviation")
                    .containsExactly("CS-SEL", "CS-ORD", "SC-RCV", "CH-BAK", "DL-DLV", "CS-EAT");

            // PMT e RCP não aparecem no histórico
            assertThat(resp.getActivityHistory())
                    .extracting("abbreviation")
                    .doesNotContain("DL-PMT", "DL-RCP");
        }
    }

    // ===============================================================
    // Cenário 3 — Pedido com atenção (loop SC-CLM)
    // RCV(NEEDS_ATTENTION) → CLM → loop → CLM(ORDER_CONFIRMED) → BAK
    // ===============================================================

    @Nested
    @DisplayName("Cenário 3 — Pedido com atenção (loop SC-CLM)")
    class PedidoComAtencao {

        @Test
        @DisplayName("SC-RCV(NEEDS_ATTENTION) → SC-CLM com status PENDING")
        void rcvNeedsAttentionParaClm() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);           // SEL → ORD
            complete(id);           // ORD → RCV

            resp = complete(id, "NEEDS_ATTENTION"); // RCV → CLM

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("SC-CLM");
            assertThat(resp.getProcessStatus()).isEqualTo("PENDING");
            assertThat(resp.getCurrentActivity().getAvailableConclusions())
                    .extracting("code")
                    .containsExactlyInAnyOrder("ORDER_CONFIRMED", "NEEDS_ATTENTION");
        }

        @Test
        @DisplayName("SC-CLM(NEEDS_ATTENTION) faz loop de volta ao SC-CLM incrementando stepNumber")
        void clmNeedsAttentionFazLoop() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "NEEDS_ATTENTION");        // RCV → CLM (step 4)

            resp = complete(id, "NEEDS_ATTENTION"); // CLM → CLM (step 5)

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("SC-CLM");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(5);
            assertThat(resp.getProcessStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("SC-CLM(ORDER_CONFIRMED) → CH-BAK saindo do loop com status IN_PREPARATION")
        void clmConfirmadoSaiDoLoop() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "NEEDS_ATTENTION");        // RCV → CLM
            complete(id, "NEEDS_ATTENTION");        // CLM → CLM (loop)

            resp = complete(id, "ORDER_CONFIRMED"); // CLM → BAK

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
            // Histórico deve conter dois SC-CLM
            assertThat(resp.getActivityHistory())
                    .extracting("abbreviation")
                    .filteredOn(a -> a.equals("SC-CLM"))
                    .hasSize(2);
        }
    }

    // ===============================================================
    // Cenário 4 — Pizza não pronta (loop CH-BAK)
    // BAK(NOT_READY) → BAK → BAK(READY_FOR_DELIVERY) → DLV
    // ===============================================================

    @Nested
    @DisplayName("Cenário 4 — Pizza não pronta (loop CH-BAK)")
    class PizzaNaoPronta {

        @Test
        @DisplayName("CH-BAK(NOT_READY) faz loop incrementando stepNumber e mantém IN_PREPARATION")
        void bakNotReadyFazLoop() {
            ProcessInstanceResponse resp = start();
            Long id = resp.getInstanceId();

            complete(id);                           // SEL → ORD
            complete(id);                           // ORD → RCV
            complete(id, "ORDER_CONFIRMED");        // RCV → BAK (step 4)

            resp = complete(id, "NOT_READY");       // BAK → BAK (step 5)
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(5);
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");

            resp = complete(id, "NOT_READY");       // BAK → BAK (step 6)
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(6);

            resp = complete(id, "READY_FOR_DELIVERY"); // BAK → DLV
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("DL-DLV");
            assertThat(resp.getProcessStatus()).isEqualTo("OUT_FOR_DELIVERY");

            // Histórico deve conter três CH-BAK
            assertThat(resp.getActivityHistory())
                    .extracting("abbreviation")
                    .filteredOn(a -> a.equals("CH-BAK"))
                    .hasSize(3);
        }
    }
}
