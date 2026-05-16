package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.model.RuleType;
import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.bpmnflow.runtime.model.entity.VariableType;
import org.bpmnflow.runtime.model.entity.WfInstanceActivityEntity;
import org.bpmnflow.runtime.model.entity.WfProcessInstanceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ProcessInstanceService — startProcess")
class StartProcessTest extends ProcessInstanceServiceTestBase {

    @Test
    @DisplayName("starts at CS-SEL with status NEW")
    void startsAtFirstActivity() {
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, RuleType.START_TO_TASK))
                .thenReturn(List.of(rule("START_TO_TASK", null, actSEL, "NEW")));
        when(instanceRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), INSTANCE_ID));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProcessInstanceResponse resp = service.startProcess(VERSION_ID, null);

        assertThat(resp.getInstanceStatus()).isEqualTo("ACTIVE");
        assertThat(resp.getProcessStatus()).isEqualTo("NEW");
        assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-SEL");
        assertThat(resp.getCurrentActivity().getStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("persists initial variables when provided")
    void persistsInitialVariables() {
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, RuleType.START_TO_TASK))
                .thenReturn(List.of(rule("START_TO_TASK", null, actSEL, "NEW")));
        when(instanceRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), INSTANCE_ID));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(any())).thenReturn(List.of());

        StartProcessRequest request = StartProcessRequest.builder()
                .externalId("EXT-001")
                .variables(List.of(
                        VariableRequest.builder().key("customer").type(VariableType.STRING).value("Jeferson").build(),
                        VariableRequest.builder().key("total").type(VariableType.FLOAT).value("49.90").build()
                ))
                .build();

        ProcessInstanceResponse resp = service.startProcess(VERSION_ID, request);

        assertThat(resp.getExternalId()).isEqualTo("EXT-001");
        verify(variableUpsertHelper).upsert(eq(INSTANCE_ID), eq("customer"), eq("STRING"), eq("Jeferson"));
        verify(variableUpsertHelper).upsert(eq(INSTANCE_ID), eq("total"), eq("FLOAT"), eq("49.90"));
    }

    @Test
    @DisplayName("throws IllegalStateException when START_TO_TASK rule has no target activity")
    void throwsWhenFirstActivityIsNull() {
        // Covers the firstActivity == null branch inside startProcess
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, RuleType.START_TO_TASK))
                .thenReturn(List.of(rule("START_TO_TASK", null, null, "NEW")));

        assertThatThrownBy(() -> service.startProcess(VERSION_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no target activity");
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when version does not exist")
    void throwsWhenVersionNotFound() {
        when(versionRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startProcess(99L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");
    }

    @Test
    @DisplayName("throws IllegalStateException when no START_TO_TASK rule exists")
    void throwsWhenNoStartRule() {
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, RuleType.START_TO_TASK))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startProcess(VERSION_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("START_TO_TASK");
    }

    @Test
    @DisplayName("preserves processStatus when matched rule has null processStatus")
    void preservesProcessStatusWhenRuleHasNullStatus() {
        // Covers the false branch of: if (matchedRule.getProcessStatus() != null)
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "ORIGINAL_STATUS");
        WfInstanceActivityEntity stepSEL = step(inst, actSEL, 1, "ACTIVE");
        inst.getInstanceActivities().add(stepSEL);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(stepSEL));
        // Rule with null processStatus — the service should NOT update processStatus
        when(ruleRepo.findMatchingRulesWithoutConclusion(VERSION_ID, actSEL.getActivityId()))
                .thenReturn(List.of(rule("TASK_TO_TASK", actSEL, actORD, null)));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(any())).thenReturn(List.of());

        ProcessInstanceResponse resp = service.completeActivity(INSTANCE_ID, null);

        assertThat(resp.getProcessStatus()).isEqualTo("ORIGINAL_STATUS");
        assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-ORD");
    }
}