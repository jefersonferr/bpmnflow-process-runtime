package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.CompleteActivityRequest;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ProcessInstanceService — conclusion validation")
class ConclusionValidationTest extends ProcessInstanceServiceTestBase {

    @Test
    @DisplayName("throws when required conclusion is missing")
    void throwsWhenConclusionMissing() {
        WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceActivityEntity step = step(instance, actRCV, 3, "ACTIVE");
        instance.getInstanceActivities().add(step);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(step));

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a conclusion")
                .hasMessageContaining("SC-RCV");
    }

    @Test
    @DisplayName("throws when conclusion is invalid for the activity")
    void throwsWhenConclusionInvalid() {
        WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "IN_PREPARATION");
        WfInstanceActivityEntity step = step(instance, actBAK, 4, "ACTIVE");
        instance.getInstanceActivities().add(step);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(step));

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().conclusionCode("INVALID_CONCLUSION").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid conclusion")
                .hasMessageContaining("INVALID_CONCLUSION");
    }

    @Test
    @DisplayName("throws when instance is already COMPLETED")
    void throwsWhenInstanceAlreadyCompleted() {
        WfProcessInstanceEntity instance = instance(INSTANCE_ID, "COMPLETED", "CLOSED");

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    @DisplayName("throws when no rule matches the conclusion")
    void throwsWhenNoRuleFound() {
        WfProcessInstanceEntity instance = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceActivityEntity step = step(instance, actSEL, 1, "ACTIVE");
        instance.getInstanceActivities().add(step);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(step));
        when(ruleRepo.findByVersion_VersionIdAndSourceActivity_ActivityId(
                VERSION_ID, actSEL.getActivityId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No rule found");
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when instance does not exist")
    void throwsWhenInstanceNotFound() {
        when(instanceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeActivity(99L,
                CompleteActivityRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Instance not found");
    }

    @Test
    @DisplayName("throws when no active activity is found")
    void throwsWhenNoActiveActivity() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active activity");
    }

    @Test
    @DisplayName("completes via TASK_TO_SPLIT_TO_END rule type")
    void completesViaSplitToEnd() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceActivityEntity stepRCV = step(inst, actRCV, 3, "ACTIVE");
        inst.getInstanceActivities().add(stepRCV);

        ProcessRuleEntity endRule = rule("TASK_TO_SPLIT_TO_END", actRCV, null, "ORDER_CONFIRMED", "CLOSED");

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(stepRCV));
        when(ruleRepo.findByVersion_VersionIdAndSourceActivity_ActivityIdAndConclusionCode(
                VERSION_ID, actRCV.getActivityId(), "ORDER_CONFIRMED"))
                .thenReturn(List.of(endRule));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().conclusionCode("ORDER_CONFIRMED").build());

        assertThat(resp.getInstanceStatus()).isEqualTo("COMPLETED");
        assertThat(resp.getProcessStatus()).isEqualTo("CLOSED");
        assertThat(resp.getCurrentActivity()).isNull();
    }

    @Test
    @DisplayName("throws when fallback rule lookup also returns empty")
    void throwsWhenFallbackAlsoEmpty() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
        ProcessActivityEntity actNoConclusion = activity(10L, "TS-TST", "Test Activity");
        WfInstanceActivityEntity stepTest = step(inst, actNoConclusion, 1, "ACTIVE");
        inst.getInstanceActivities().add(stepTest);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(stepTest));
        when(ruleRepo.findByVersion_VersionIdAndSourceActivity_ActivityIdAndConclusionCode(
                VERSION_ID, actNoConclusion.getActivityId(), "SOME_CODE"))
                .thenReturn(List.of());
        when(ruleRepo.findByVersion_VersionIdAndSourceActivity_ActivityId(
                VERSION_ID, actNoConclusion.getActivityId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder().conclusionCode("SOME_CODE").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No rule found");
    }
}