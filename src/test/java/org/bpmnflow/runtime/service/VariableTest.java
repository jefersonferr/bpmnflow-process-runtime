package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.model.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ProcessInstanceService — typed variables")
class VariableTest extends ProcessInstanceServiceTestBase {

    @Test
    @DisplayName("rejects INTEGER with non-numeric value")
    void rejectsInvalidInteger() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("qty").type(VariableType.INTEGER).value("abc").build()
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INTEGER");
    }

    @Test
    @DisplayName("rejects FLOAT with non-numeric value")
    void rejectsInvalidFloat() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("total").type(VariableType.FLOAT).value("not-a-number").build()
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FLOAT");
    }

    @Test
    @DisplayName("rejects BOOLEAN with value outside valid set")
    void rejectsInvalidBoolean() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("active").type(VariableType.BOOLEAN).value("maybe").build()
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BOOLEAN");
    }

    @Test
    @DisplayName("rejects DATE with invalid format")
    void rejectsInvalidDate() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("delivery").type(VariableType.DATE).value("31/12/2025").build()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DATE")
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    @DisplayName("rejects malformed JSON")
    void rejectsMalformedJson() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("data").type(VariableType.JSON).value("{key without quotes}").build()
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("rejects variable with blank key")
    void rejectsBlankKey() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));

        assertThatThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("").type(VariableType.STRING).value("value").build()
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key must not be blank");
    }

    @Test
    @DisplayName("upserts variable via VariableUpsertHelper")
    void updatesExistingVariable() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(INSTANCE_ID)).thenReturn(List.of());

        service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("total").type(VariableType.FLOAT).value("149.90").build()
        ));

        // Variables are now persisted via VariableUpsertHelper — verify the upsert was called
        verify(variableUpsertHelper).upsert(eq(INSTANCE_ID), eq("total"), eq("FLOAT"), eq("149.90"));
        // instanceRepo.save is called to bump @Version (dirty-check for optimistic locking)
        verify(instanceRepo, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("accepts all valid types without throwing")
    void acceptsAllValidTypes() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(any())).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("s").type(VariableType.STRING).value("text").build(),
                VariableRequest.builder().key("i").type(VariableType.INTEGER).value("42").build(),
                VariableRequest.builder().key("f").type(VariableType.FLOAT).value("3.14").build(),
                VariableRequest.builder().key("b").type(VariableType.BOOLEAN).value("true").build(),
                VariableRequest.builder().key("d").type(VariableType.DATE).value("2025-12-31").build(),
                VariableRequest.builder().key("j").type(VariableType.JSON).value("{\"ok\":true}").build()
        )));
    }

    @Test
    @DisplayName("getVariables returns variables with converted values")
    void getVariablesReturnsWithConvertedValues() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceVariableEntity var = WfInstanceVariableEntity.builder()
                .variableId(1L).instance(inst)
                .variableKey("customer").variableType(VariableType.STRING).variableValue("Jeferson")
                .build();

        when(instanceRepo.existsById(INSTANCE_ID)).thenReturn(true);
        when(variableRepo.findByInstance_InstanceId(INSTANCE_ID)).thenReturn(List.of(var));

        List<VariableResponse> result = service.getVariables(INSTANCE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKey()).isEqualTo("customer");
        assertThat(result.getFirst().getValue()).isEqualTo("Jeferson");
        assertThat(result.getFirst().getConvertedValue()).isEqualTo("Jeferson");
    }

    @Test
    @DisplayName("getVariables throws ResourceNotFoundException when instance does not exist")
    void getVariablesThrowsWhenInstanceNotFound() {
        when(instanceRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getVariables(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Instance not found");
    }

    @Test
    @DisplayName("persists variables when advancing activity")
    void persistsVariablesOnAdvance() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceActivityEntity stepSEL = step(inst, actSEL, 1, "ACTIVE");
        inst.getInstanceActivities().add(stepSEL);

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.valueOf("ACTIVE")))
                .thenReturn(Optional.of(stepSEL));
        when(ruleRepo.findMatchingRulesWithoutConclusion(VERSION_ID, actSEL.getActivityId()))
                .thenReturn(List.of(rule("TASK_TO_TASK", actSEL, actORD, null)));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(any())).thenReturn(List.of());

        var resp = service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder()
                        .variables(List.of(
                                VariableRequest.builder().key("obs").type(VariableType.STRING).value("ok").build()
                        ))
                        .build());

        assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-ORD");
        // Variable persisted via upsert helper, not variableRepo.save
        verify(variableUpsertHelper).upsert(eq(INSTANCE_ID), eq("obs"), eq("STRING"), eq("ok"));
    }
}