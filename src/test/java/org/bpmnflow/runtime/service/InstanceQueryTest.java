package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.bpmnflow.runtime.model.entity.InstanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ProcessInstanceService — instance queries")
class InstanceQueryTest extends ProcessInstanceServiceTestBase {

    // ---------------------------------------------------------------
    // getInstance
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("returns full instance state with available conclusions")
        void returnsFullState() {
            WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "IN_PREPARATION");
            WfInstanceActivityEntity step = step(instance, actBAK, 4, "ACTIVE");
            instance.getInstanceActivities().add(step);

            when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                    .thenReturn(Optional.of(step));
            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID)).thenReturn(List.of());

            ProcessInstanceResponse resp = service.getInstance(INSTANCE_ID);

            assertThat(resp.getInstanceId()).isEqualTo(INSTANCE_ID);
            assertThat(resp.getInstanceStatus()).isEqualTo("ACTIVE");
            assertThat(resp.getProcessStatus()).isEqualTo("IN_PREPARATION");
            assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CH-BAK");
            assertThat(resp.getCurrentActivity().getAvailableConclusions())
                    .extracting("code")
                    .containsExactlyInAnyOrder("READY_FOR_DELIVERY", "NOT_READY");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when instance does not exist")
        void throwsWhenNotFound() {
            when(instanceRepo.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInstance(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Instance not found");
        }
    }

    // ---------------------------------------------------------------
    // listInstances
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("listInstances")
    class ListInstances {

        @Test
        @DisplayName("returns all instances when no filter is provided")
        void returnsAllWithNoFilter() {
            WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
            inst.getInstanceActivities().add(step(inst, actSEL, 1, "ACTIVE"));

            when(instanceRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(inst));

            List<WorkflowSummaryResponse> result = service.listInstances(null, null);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getInstanceId()).isEqualTo(INSTANCE_ID);
            assertThat(result.getFirst().getCurrentActivityAbbreviation()).isEqualTo("CS-SEL");
        }

        @Test
        @DisplayName("filters by status (uppercases the value)")
        void filtersByStatus() {
            WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
            inst.getInstanceActivities().add(step(inst, actSEL, 1, "ACTIVE"));

            when(instanceRepo.findByStatusOrderByCreatedAtDesc(InstanceStatus.ACTIVE)).thenReturn(List.of(inst));

            List<WorkflowSummaryResponse> result = service.listInstances("active", null);

            assertThat(result).hasSize(1);
            verify(instanceRepo).findByStatusOrderByCreatedAtDesc(InstanceStatus.ACTIVE);
        }

        @Test
        @DisplayName("filters by processKey")
        void filtersByProcessKey() {
            WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
            inst.getInstanceActivities().add(step(inst, actSEL, 1, "ACTIVE"));

            when(instanceRepo.findByProcessKeyOrderByCreatedAtDesc("PIZZA_DELIVERY"))
                    .thenReturn(List.of(inst));

            List<WorkflowSummaryResponse> result = service.listInstances(null, "PIZZA_DELIVERY");

            assertThat(result).hasSize(1);
            verify(instanceRepo).findByProcessKeyOrderByCreatedAtDesc("PIZZA_DELIVERY");
        }

        @Test
        @DisplayName("filters by both processKey and status")
        void filtersByProcessKeyAndStatus() {
            WfProcessInstanceEntity inst = instance(INSTANCE_ID, "COMPLETED", "CLOSED");
            inst.getInstanceActivities().add(step(inst, actEAT, 8, "COMPLETED"));

            when(instanceRepo.findByProcessKeyAndStatusOrderByCreatedAtDesc("PIZZA_DELIVERY", InstanceStatus.COMPLETED))
                    .thenReturn(List.of(inst));

            List<WorkflowSummaryResponse> result = service.listInstances("completed", "PIZZA_DELIVERY");

            assertThat(result).hasSize(1);
            verify(instanceRepo).findByProcessKeyAndStatusOrderByCreatedAtDesc("PIZZA_DELIVERY", InstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("returns empty list when no instances match")
        void returnsEmptyList() {
            when(instanceRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            assertThat(service.listInstances(null, null)).isEmpty();
        }

        @Test
        @DisplayName("returns null currentActivity in summary for COMPLETED instance")
        void completedInstanceHasNoCurrentActivity() {
            WfProcessInstanceEntity inst = instance(INSTANCE_ID, "COMPLETED", "CLOSED");
            inst.getInstanceActivities().add(step(inst, actEAT, 8, "COMPLETED"));

            when(instanceRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(inst));

            List<WorkflowSummaryResponse> result = service.listInstances(null, null);

            assertThat(result.getFirst().getCurrentActivityAbbreviation()).isNull();
            assertThat(result.getFirst().getInstanceStatus()).isEqualTo("COMPLETED");
        }
    }
}