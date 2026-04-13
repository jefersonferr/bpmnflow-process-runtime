package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("updates existing variable and changes its type")
    void updatesExistingVariable() {
        WfProcessInstanceEntity inst = instance(INSTANCE_ID, "ACTIVE", "NEW");
        WfInstanceVariableEntity existing = WfInstanceVariableEntity.builder()
                .variableId(1L).instance(inst)
                .variableKey("total").variableType(VariableType.STRING).variableValue("0")
                .build();

        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(variableRepo.findByInstance_InstanceIdAndVariableKey(INSTANCE_ID, "total"))
                .thenReturn(Optional.of(existing));
        when(variableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(INSTANCE_ID)).thenReturn(List.of(existing));

        service.setVariables(INSTANCE_ID, List.of(
                VariableRequest.builder().key("total").type(VariableType.FLOAT).value("149.90").build()
        ));

        verify(variableRepo, times(1)).save(any());
        assertThat(existing.getVariableType()).isEqualTo(VariableType.FLOAT);
        assertThat(existing.getVariableValue()).isEqualTo("149.90");
    }

    @Test
    @DisplayName("accepts all valid types without throwing")
    void acceptsAllValidTypes() {
        when(instanceRepo.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(INSTANCE_ID, "ACTIVE", "NEW")));
        when(variableRepo.findByInstance_InstanceIdAndVariableKey(any(), any())).thenReturn(Optional.empty());
        when(variableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
        assertThat(result.get(0).getKey()).isEqualTo("customer");
        assertThat(result.get(0).getValue()).isEqualTo("Jeferson");
        assertThat(result.get(0).getConvertedValue()).isEqualTo("Jeferson");
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
        when(instActivityRepo.findByInstance_InstanceIdAndStatus(INSTANCE_ID, ActivityStepStatus.ACTIVE))
                .thenReturn(Optional.of(stepSEL));
        when(ruleRepo.findByVersion_VersionIdAndSourceActivity_ActivityId(
                VERSION_ID, actSEL.getActivityId()))
                .thenReturn(List.of(rule("TASK_TO_TASK", actSEL, actORD, null)));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceIdAndVariableKey(any(), any())).thenReturn(Optional.empty());
        when(variableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceId(any())).thenReturn(List.of());

        var resp = service.completeActivity(INSTANCE_ID,
                CompleteActivityRequest.builder()
                        .variables(List.of(
                                VariableRequest.builder().key("obs").type(VariableType.STRING).value("ok").build()
                        ))
                        .build());

        assertThat(resp.getCurrentActivity().getAbbreviation()).isEqualTo("CS-ORD");
        verify(variableRepo, times(1)).save(any());
    }
}