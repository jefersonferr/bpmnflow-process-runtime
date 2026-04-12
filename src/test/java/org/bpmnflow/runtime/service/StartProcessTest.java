package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.model.entity.VariableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ProcessInstanceService — startProcess")
class StartProcessTest extends ProcessInstanceServiceTestBase {

    @Test
    @DisplayName("starts at CS-SEL with status NEW")
    void startsAtFirstActivity() {
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, "START_TO_TASK"))
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
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, "START_TO_TASK"))
                .thenReturn(List.of(rule("START_TO_TASK", null, actSEL, "NEW")));
        when(instanceRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), INSTANCE_ID));
        when(instActivityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepo.findByInstance_InstanceIdAndVariableKey(any(), any()))
                .thenReturn(Optional.empty());
        when(variableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
        verify(variableRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("throws IllegalArgumentException when version does not exist")
    void throwsWhenVersionNotFound() {
        when(versionRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startProcess(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version not found");
    }

    @Test
    @DisplayName("throws IllegalStateException when no START_TO_TASK rule exists")
    void throwsWhenNoStartRule() {
        when(versionRepo.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(ruleRepo.findByVersion_VersionIdAndRuleType(VERSION_ID, "START_TO_TASK"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startProcess(VERSION_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("START_TO_TASK");
    }
}
