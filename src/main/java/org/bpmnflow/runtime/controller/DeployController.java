package org.bpmnflow.runtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bpmnflow.WorkflowLoader;
import org.bpmnflow.runtime.dto.DeployResponse;
import org.bpmnflow.runtime.dto.ErrorResponse;
import org.bpmnflow.runtime.dto.ProcessSummaryResponse;
import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;
import org.bpmnflow.runtime.service.BpmnCatalogService;
import org.bpmnflow.runtime.service.BpmnDeployService;
import org.bpmnflow.runtime.service.DeployResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(
        name = "BPMN",
        description = "BPMN model management — upload, parse, persist and version process definitions"
)
@RestController
@RequestMapping("/bpmn")
@RequiredArgsConstructor
public class DeployController {

    private final BpmnDeployService deployService;
    private final BpmnCatalogService catalogService;
    private final WorkflowLoader loader;

    @Operation(
            summary = "List deployed processes",
            description = "Returns all process definitions with their versions ordered by creation date (newest first). " +
                    "Each version includes counters for structural and derived data but omits the BPMN XML."
    )
    @ApiResponse(responseCode = "200", description = "Process list returned successfully")
    @GetMapping("/processes")
    public ResponseEntity<List<ProcessSummaryResponse>> listProcesses() {
        return ResponseEntity.ok(catalogService.listProcesses());
    }

    @Operation(
            summary = "Get a deployed process by key",
            description = "Returns a specific process definition with all its versions and counters. " +
                    "Versions are ordered from newest to oldest."
    )
    @ApiResponse(responseCode = "200", description = "Process returned successfully")
    @ApiResponse(responseCode = "404", description = "Process not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/processes/{processKey}")
    public ResponseEntity<ProcessSummaryResponse> getProcess(
            @Parameter(description = "Process key (ex: PIZZA_DELIVERY)")
            @PathVariable String processKey) {

        // ResourceNotFoundException → 404 via GlobalExceptionHandler
        return ResponseEntity.ok(catalogService.getProcess(processKey));
    }

    @Operation(
            summary = "Deploy a BPMN model",
            description = "Uploads a .bpmn file and an optional config YAML, parses the model using BPMNFlow, " +
                    "and persists the full structure in the database as a new version. " +
                    "Structural data (participants, lanes, elements, sequence flows, extension properties) " +
                    "and derived data (stages, activities, conclusions, rules, inconsistencies) are all stored. " +
                    "If no config file is provided, the default classpath bpmn-config.yaml is used. " +
                    "Each call to the same processKey increments the version number."
    )
    @ApiResponse(responseCode = "200", description = "Model deployed successfully")
    @ApiResponse(responseCode = "400", description = "Empty file or invalid BPMN content",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "File exceeds maximum allowed size",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error during deploy",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeployResponse> deploy(
            @RequestParam("bpmn") MultipartFile bpmnFile,
            @RequestParam(value = "config", required = false) MultipartFile configFile,
            @RequestParam(value = "processKey", required = false) String processKey) throws Exception {

        if (bpmnFile.isEmpty()) {
            throw new IllegalArgumentException("BPMN file must not be empty");
        }

        byte[] bpmnContent   = bpmnFile.getBytes();
        byte[] configContent = (configFile != null && !configFile.isEmpty())
                ? configFile.getBytes()
                : loader.getConfigStream().readAllBytes();

        DeployResult result               = deployService.deploy(bpmnContent, configContent, processKey);
        BpmnProcessVersionEntity version  = result.getVersion();

        return ResponseEntity.ok(DeployResponse.builder()
                .message("Model deployed successfully")
                .processKey(version.getProcess().getProcessKey())
                .versionId(version.getVersionId())
                .versionNumber(version.getVersionNumber())
                .versionTag(version.getVersionTag())
                .processType(version.getProcessType())
                .processSubtype(version.getProcessSubtype())
                .valid(version.isValid())
                .participantCount(result.getParticipantCount())
                .laneCount(result.getLaneCount())
                .elementCount(result.getElementCount())
                .sequenceFlowCount(result.getSequenceFlowCount())
                .stageCount(result.getStageCount())
                .activityCount(result.getActivityCount())
                .ruleCount(result.getRuleCount())
                .inconsistencyCount(result.getInconsistencyCount())
                .inconsistencies(result.getInconsistencies())
                .build());
    }
}
