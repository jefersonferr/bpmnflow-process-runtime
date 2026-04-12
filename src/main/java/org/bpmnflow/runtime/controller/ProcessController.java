package org.bpmnflow.runtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.dto.WorkflowSummaryResponse;
import org.bpmnflow.runtime.service.ProcessInstanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(
        name = "Workflow",
        description = "Workflow execution — start, advance and inspect running process instances"
)
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessInstanceService instanceService;

    @Operation(
            summary = "List workflow instances",
            description = "Returns a summary list of workflow instances ordered by creation date (newest first). " +
                    "Optionally filter by **status** (ACTIVE, COMPLETED, CANCELLED) and/or **processKey**. " +
                    "For the full state of a specific instance — activity history, variables and conclusions — use GET /{instanceId}."
    )
    @GetMapping
    public ResponseEntity<?> listInstances(
            @Parameter(description = "Filter by instance status: ACTIVE, COMPLETED or CANCELLED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by process key (ex: PIZZA_DELIVERY)")
            @RequestParam(required = false) String processKey) {
        try {
            List<WorkflowSummaryResponse> result = instanceService.listInstances(status, processKey);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Start a workflow instance",
            description = "Creates a new workflow instance from a deployed process version. " +
                    "Resolves the first activity via the START_TO_TASK rule and returns it " +
                    "with its available conclusions. " +
                    "Optionally accepts an external correlation ID and initial typed variables."
    )
    @PostMapping("/start")
    public ResponseEntity<?> startProcess(
            @Parameter(description = "Version ID of the deployed BPMN process")
            @RequestParam Long versionId,
            @RequestBody(required = false) StartProcessRequest request) {
        try {
            ProcessInstanceResponse response = instanceService.startProcess(versionId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Complete current activity and advance",
            description = "Completes the active activity step with a conclusion code, resolves " +
                    "the next activity from the process rules, and advances the workflow. " +
                    "If the conclusion leads to an end event, the instance is marked as COMPLETED. " +
                    "Optionally sets or updates typed variables."
    )
    @PostMapping("/{instanceId}/complete")
    public ResponseEntity<?> completeActivity(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId,
            @RequestBody CompleteActivityRequest request) {
        try {
            ProcessInstanceResponse response = instanceService.completeActivity(instanceId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Get workflow instance state",
            description = "Returns the full state of a workflow instance: current activity with " +
                    "available conclusions, activity history, typed variables, and business process status."
    )
    @GetMapping("/{instanceId}")
    public ResponseEntity<?> getInstance(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId) {
        try {
            ProcessInstanceResponse response = instanceService.getInstance(instanceId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Set or update instance variables",
            description = "Adds or updates typed variables for a workflow instance. " +
                    "Each variable must declare its type: STRING, INTEGER, FLOAT, BOOLEAN, DATE or JSON. " +
                    "The value is validated against the declared type before persisting — " +
                    "mismatches (e.g. type INTEGER with value 'abc') return HTTP 400. " +
                    "Existing keys are overwritten; new keys are created. Type can be changed on update."
    )
    @PutMapping("/{instanceId}/variables")
    public ResponseEntity<?> setVariables(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId,
            @RequestBody List<VariableRequest> variables) {
        try {
            List<VariableResponse> result = instanceService.setVariables(instanceId, variables);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Get instance variables",
            description = "Returns all typed variables stored for this workflow instance, " +
                    "including the raw stored value and the converted Java type value."
    )
    @GetMapping("/{instanceId}/variables")
    public ResponseEntity<?> getVariables(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId) {
        try {
            List<VariableResponse> result = instanceService.getVariables(instanceId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}