package org.bpmnflow.runtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bpmnflow.runtime.dto.*;
import org.bpmnflow.runtime.dto.WorkflowSummaryResponse;
import org.bpmnflow.runtime.service.ProcessInstanceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Workflow",
        description = "Workflow execution — start, advance and inspect running process instances"
)
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class ProcessController {

    private final ProcessInstanceService instanceService;

    @Operation(
            summary = "List workflow instances",
            description = "Returns a summary list of workflow instances ordered by creation date (newest first). " +
                    "Optionally filter by **status** (ACTIVE, COMPLETED, CANCELLED) and/or **processKey**. " +
                    "For the full state of a specific instance — activity history, variables and conclusions — use GET /{instanceId}."
    )
    @ApiResponse(responseCode = "200", description = "Instance list returned successfully")
    @GetMapping
    public ResponseEntity<List<WorkflowSummaryResponse>> listInstances(
            @Parameter(description = "Filter by instance status: ACTIVE, COMPLETED or CANCELLED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by process key (ex: PIZZA_DELIVERY)")
            @RequestParam(required = false) String processKey) {

        return ResponseEntity.ok(instanceService.listInstances(status, processKey));
    }

    @Operation(
            summary = "Start a workflow instance",
            description = "Creates a new workflow instance from a deployed process version. " +
                    "Resolves the first activity via the START_TO_TASK rule and returns it " +
                    "with its available conclusions. " +
                    "Optionally accepts an external correlation ID and initial typed variables."
    )
    @ApiResponse(responseCode = "200", description = "Instance started successfully")
    @ApiResponse(responseCode = "404", description = "Version not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Version not found: 99",
                              "path": "/workflow/start"
                            }
                            """)))
    @ApiResponse(responseCode = "409", description = "No START_TO_TASK rule defined for this version",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 409,
                              "error": "Conflict",
                              "message": "No START_TO_TASK rule found for version 1",
                              "path": "/workflow/start"
                            }
                            """)))
    @PostMapping("/start")
    public ResponseEntity<ProcessInstanceResponse> startProcess(
            @Parameter(description = "Version ID of the deployed BPMN process")
            @RequestParam Long versionId,
            @RequestBody(required = false) StartProcessRequest request) {

        return ResponseEntity.ok(instanceService.startProcess(versionId, request));
    }

    @Operation(
            summary = "Complete current activity and advance",
            description = "Completes the active activity step with a conclusion code, resolves " +
                    "the next activity from the process rules, and advances the workflow. " +
                    "If the conclusion leads to an end event, the instance is marked as COMPLETED. " +
                    "Optionally sets or updates typed variables."
    )
    @ApiResponse(responseCode = "200", description = "Activity completed and instance advanced")
    @ApiResponse(responseCode = "400", description = "Invalid or missing conclusion code, or malformed request body",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "Invalid conclusion", value = """
                                    {
                                      "timestamp": "2026-04-12T10:00:00",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "message": "Invalid conclusion 'WRONG_CODE' for activity 'CS-ORD'. Available: [ORDER_CONFIRMED, NEEDS_ATTENTION]",
                                      "path": "/workflow/1/complete"
                                    }
                                    """),
                            @ExampleObject(name = "Malformed JSON", value = """
                                    {
                                      "timestamp": "2026-04-12T10:00:00",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "message": "Request body is missing or contains invalid JSON",
                                      "path": "/workflow/1/complete"
                                    }
                                    """)
                    }))
    @ApiResponse(responseCode = "404", description = "Instance not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Instance not found: 99",
                              "path": "/workflow/99/complete"
                            }
                            """)))
    @ApiResponse(responseCode = "409", description = "Instance already completed or no active activity",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 409,
                              "error": "Conflict",
                              "message": "Process instance is already completed",
                              "path": "/workflow/1/complete"
                            }
                            """)))
    @PostMapping("/{instanceId}/complete")
    public ResponseEntity<ProcessInstanceResponse> completeActivity(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId,
            @RequestBody CompleteActivityRequest request) {

        return ResponseEntity.ok(instanceService.completeActivity(instanceId, request));
    }

    @Operation(
            summary = "Get workflow instance state",
            description = "Returns the full state of a workflow instance: current activity with " +
                    "available conclusions, activity history, typed variables, and business process status."
    )
    @ApiResponse(responseCode = "200", description = "Instance state returned successfully")
    @ApiResponse(responseCode = "404", description = "Instance not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Instance not found: 99",
                              "path": "/workflow/99"
                            }
                            """)))
    @GetMapping("/{instanceId}")
    public ResponseEntity<ProcessInstanceResponse> getInstance(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId) {

        return ResponseEntity.ok(instanceService.getInstance(instanceId));
    }

    @Operation(
            summary = "Set or update instance variables",
            description = "Adds or updates typed variables for a workflow instance. " +
                    "Each variable must declare its type: STRING, INTEGER, FLOAT, BOOLEAN, DATE or JSON. " +
                    "The value is validated against the declared type before persisting — " +
                    "mismatches (e.g. type INTEGER with value 'abc') return HTTP 400. " +
                    "Existing keys are overwritten; new keys are created. Type can be changed on update."
    )
    @ApiResponse(responseCode = "200", description = "Variables set successfully")
    @ApiResponse(responseCode = "400", description = "Invalid variable type or value",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 400,
                              "error": "Bad Request",
                              "message": "Invalid value for type INTEGER: \\"abc\\"",
                              "path": "/workflow/1/variables"
                            }
                            """)))
    @ApiResponse(responseCode = "404", description = "Instance not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Instance not found: 99",
                              "path": "/workflow/99/variables"
                            }
                            """)))
    @PutMapping("/{instanceId}/variables")
    public ResponseEntity<List<VariableResponse>> setVariables(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId,
            @RequestBody List<VariableRequest> variables) {

        return ResponseEntity.ok(instanceService.setVariables(instanceId, variables));
    }

    @Operation(
            summary = "Get instance variables",
            description = "Returns all typed variables stored for this workflow instance, " +
                    "including the raw stored value and the converted Java type value."
    )
    @ApiResponse(responseCode = "200", description = "Variables returned successfully")
    @ApiResponse(responseCode = "404", description = "Instance not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-04-12T10:00:00",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Instance not found: 99",
                              "path": "/workflow/99/variables"
                            }
                            """)))
    @GetMapping("/{instanceId}/variables")
    public ResponseEntity<List<VariableResponse>> getVariables(
            @Parameter(description = "Workflow instance ID")
            @PathVariable Long instanceId) {

        return ResponseEntity.ok(instanceService.getVariables(instanceId));
    }
}