package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.model.entity.ActivityStepStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Flow transition tests for ProcessInstanceService.
 * Covers all branching paths in the pizza-delivery process:
 *   - Scenario 1: Happy path with payment (SEL→ORD→RCV→BAK→DLV→PMT→RCP→EAT→END)
 *   - Scenario 2: Prepaid / no payment (DLV→EAT skipping PMT and RCP)
 *   - Scenario 3: Attention loop (RCV→CLM→CLM→BAK)
 *   - Scenario 4: Bake loop (BAK→BAK on NOT_READY)
 */
@DisplayName("ProcessInstanceService — workflow flow transitions")
class WorkflowFlowTest extends ProcessInstanceServiceTestBase {

    // ---------------------------------------------------------------
    // Scenario 1 — Happy path with payment
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Scenario 1 — Happy path with payment")
    class HappyPathWithPayment {

        @Test
        @DisplayName("SEL → ORD (no conclusion required)")
        void selToOrd() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "NEW");
            WfInstanceActivityEntity step = step(instance, actSEL, 1, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithoutConclusion(VERSION_ID, actSEL.getActivityId()))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actSEL, actORD, null)));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-ORD");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("RCV(ORDER_CONFIRMED) → BAK with status IN_PREPARATION")
        void rcvConfirmedToBak() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "NEW");
            WfInstanceActivityEntity step = step(instance, actRCV, 3, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actRCV.getActivityId(), "ORDER_CONFIRMED"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actRCV, actBAK, "IN_PREPARATION")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("ORDER_CONFIRMED").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
        }

        @Test
        @DisplayName("BAK(READY_FOR_DELIVERY) → DLV with status OUT_FOR_DELIVERY")
        void bakReadyToDlv() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "IN_PREPARATION");
            WfInstanceActivityEntity step = step(instance, actBAK, 4, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actBAK.getActivityId(), "READY_FOR_DELIVERY"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actBAK, actDLV, "OUT_FOR_DELIVERY")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("READY_FOR_DELIVERY").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("DL-DLV");
            assertThat(resp.getProcessStatus()).isEqualTo("OUT_FOR_DELIVERY");
        }

        @Test
        @DisplayName("DLV(COLLECT_PAYMENT) → PMT")
        void dlvCollectPaymentToPmt() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "OUT_FOR_DELIVERY");
            WfInstanceActivityEntity step = step(instance, actDLV, 5, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actDLV.getActivityId(), "COLLECT_PAYMENT"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actDLV, actPMT, null)));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("COLLECT_PAYMENT").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("DL-PMT");
        }

        @Test
        @DisplayName("RCP → EAT → COMPLETED with status CLOSED")
        void rcpToEatToCompleted() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "OUT_FOR_DELIVERY");
            WfInstanceActivityEntity stepRCP = step(instance, actRCP, 7, "ACTIVE");
            instance.getInstanceActivities().add(stepRCP);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(stepRCP));
            when(ruleRepo.findMatchingRulesWithoutConclusion(VERSION_ID, actRCP.getActivityId()))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actRCP, actEAT, null)));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().build());
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-EAT");

            WfInstanceActivityEntity stepEAT = step(instance, actEAT, 8, "ACTIVE");
            instance.getInstanceActivities().add(stepEAT);
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(stepEAT));
            when(ruleRepo.findMatchingRulesWithoutConclusion(VERSION_ID, actEAT.getActivityId()))
                    .thenReturn(List.of(rule("TASK_TO_END", actEAT, null, "CLOSED")));

            ProcessInstanceResponse respFinal = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().build());

            assertThat(respFinal.getInstanceStatus()).isEqualTo("COMPLETED");
            assertThat(respFinal.getProcessStatus()).isEqualTo("CLOSED");
            assertThat(respFinal.getCurrentActivity()).isNull();
        }
    }

    // ---------------------------------------------------------------
    // Scenario 2 — Prepaid / no payment (ONLY_DELIVERY)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Scenario 2 — Prepaid (ONLY_DELIVERY)")
    class PrepaidFlow {

        @Test
        @DisplayName("DLV(ONLY_DELIVERY) → EAT skipping PMT and RCP")
        void dlvOnlyDeliveryToEat() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "OUT_FOR_DELIVERY");
            WfInstanceActivityEntity step = step(instance, actDLV, 5, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actDLV.getActivityId(), "ONLY_DELIVERY"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actDLV, actEAT, null)));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("ONLY_DELIVERY").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-EAT");
            verify(instActivityRepo, times(2)).save(any());
        }
    }

    // ---------------------------------------------------------------
    // Scenario 3 — Attention loop (SC-CLM)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Scenario 3 — Attention loop (SC-CLM)")
    class AttentionLoop {

        @Test
        @DisplayName("RCV(NEEDS_ATTENTION) → CLM with status PENDING")
        void rcvNeedsAttentionToClm() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "NEW");
            WfInstanceActivityEntity step = step(instance, actRCV, 3, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actRCV.getActivityId(), "NEEDS_ATTENTION"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actRCV, actCLM, "PENDING")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("NEEDS_ATTENTION").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("SC-CLM");
            assertThat(resp.getProcessStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("CLM(NEEDS_ATTENTION) loops back to CLM")
        void clmNeedsAttentionLoops() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "PENDING");
            WfInstanceActivityEntity step = step(instance, actCLM, 4, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actCLM.getActivityId(), "NEEDS_ATTENTION"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actCLM, actCLM, "PENDING")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("NEEDS_ATTENTION").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("SC-CLM");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("CLM(ORDER_CONFIRMED) → BAK exits the loop")
        void clmConfirmedToBak() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "PENDING");
            WfInstanceActivityEntity step = step(instance, actCLM, 5, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actCLM.getActivityId(), "ORDER_CONFIRMED"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actCLM, actBAK, "IN_PREPARATION")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("ORDER_CONFIRMED").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
        }
    }

    // ---------------------------------------------------------------
    // Scenario 4 — Bake loop (CH-BAK)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Scenario 4 — Bake loop (CH-BAK)")
    class BakeLoop {

        @Test
        @DisplayName("BAK(NOT_READY) loops back to BAK")
        void bakNotReadyLoops() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "IN_PREPARATION");
            WfInstanceActivityEntity step = step(instance, actBAK, 4, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(ruleRepo.findMatchingRulesWithConclusion(
                    VERSION_ID, actBAK.getActivityId(), "NOT_READY"))
                    .thenReturn(List.of(rule("TASK_TO_TASK", actBAK, actBAK, "IN_PREPARATION")));
            when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID,
                    CompleteActivityRequest.builder().conclusionCode("NOT_READY").build());

            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(5);
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
        }
    }
}