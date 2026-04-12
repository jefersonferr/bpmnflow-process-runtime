package org.bpmnflow.runtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bpmnflow.WorkflowLoader;
import org.bpmnflow.runtime.dto.DeployResponse;
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
import java.util.Map;

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
                    "Each version includes counters for structural and derived data but omits the BPMN XML. " +
                    "Optionally filter by processKey to retrieve a specific process."
    )
    @GetMapping("/processes")
    public ResponseEntity<?> listProcesses() {
        List<ProcessSummaryResponse> result = catalogService.listProcesses();
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Get a deployed process by key",
            description = "Returns a specific process definition with all its versions and counters. " +
                    "Versions are ordered from newest to oldest."
    )
    @GetMapping("/processes/{processKey}")
    public ResponseEntity<?> getProcess(
            @Parameter(description = "Process key (ex: PIZZA_DELIVERY)")
            @PathVariable String processKey) {
        try {
            ProcessSummaryResponse result = catalogService.getProcess(processKey);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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
    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> deploy(
            @RequestParam("bpmn") MultipartFile bpmnFile,
            @RequestParam(value = "config", required = false) MultipartFile configFile,
            @RequestParam(value = "processKey", required = false) String processKey) {

        if (bpmnFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "BPMN file is empty"));
        }

        try {
            byte[] bpmnContent = bpmnFile.getBytes();
            byte[] configContent;

            if (configFile != null && !configFile.isEmpty()) {
                configContent = configFile.getBytes();
            } else {
                configContent = loader.getConfigStream().readAllBytes();
            }

            DeployResult result = deployService.deploy(bpmnContent, configContent, processKey);
            BpmnProcessVersionEntity version = result.getVersion();

            DeployResponse response = DeployResponse.builder()
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
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Deploy failed: " + e.getMessage()));
        }
    }
}
