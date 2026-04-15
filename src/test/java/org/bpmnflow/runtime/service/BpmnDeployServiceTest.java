package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.bpmnflow.runtime.service.deploy.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BpmnDeployService}.
 * These tests exercise the full deploy pipeline against an in-memory H2
 * database (profile "test").  Each test method gets a fresh context via
 * {@link DirtiesContext} to guarantee isolation — necessary because
 * {@link BpmnProcessRepository#findByProcessKeyForUpdate} acquires a
 * pessimistic lock that H2 does not release between tests in the same
 * transaction.
 *
 * <p>Unit-level coverage for the parsing and config-deduplication logic
 * lives in {@link BpmnModelParserTest} and {@link BpmnConfigPersistorTest},
 * which run without Spring and are much faster.  The goal here is to verify
 * that the orchestrator wires everything correctly end-to-end.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("BpmnDeployService — integration")
class BpmnDeployServiceTest {

    @Autowired private BpmnDeployService           deployService;
    @Autowired private BpmnProcessVersionRepository versionRepo;
    @Autowired private BpmnProcessRepository        processRepo;
    @Autowired private BpmnActivityRepository       activityRepo;
    @Autowired private BpmnParticipantRepository    participantRepo;
    @Autowired private BpmnLaneRepository           laneRepo;
    @Autowired private BpmnElementRepository        elementRepo;
    @Autowired private BpmnSequenceFlowRepository   sequenceFlowRepo;

    private byte[] bpmn;
    private byte[] config;

    @BeforeEach
    void loadFiles() throws Exception {
        bpmn = Files.readAllBytes(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("pizza-delivery.bpmn")).toURI()));
        config = Files.readAllBytes(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("bpmn-config.yaml")).toURI()));
    }

    // ---------------------------------------------------------------
    // Process key resolution
    // ---------------------------------------------------------------

    @Test
    @DisplayName("explicit processKey is used as-is")
    void explicitKeyIsUsed() {
        var result = deployService.deploy(bpmn, config, "MY_CUSTOM_KEY");

        assertThat(result.getVersion().getProcess().getProcessKey()).isEqualTo("MY_CUSTOM_KEY");
        assertThat(processRepo.findByProcessKey("MY_CUSTOM_KEY")).isPresent();
    }

    @Test
    @DisplayName("null processKey falls back to BPMN id")
    void nullKeyFallsBackToBpmnId() {
        var result = deployService.deploy(bpmn, config, null);

        assertThat(result.getVersion().getProcess().getProcessKey()).isNotBlank();
        assertThat(result.getVersion().getVersionNumber()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // Counter accuracy
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DeployResult counters match actual DB row counts")
    void countersMatchDatabase() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");
        Long vId = result.getVersion().getVersionId();

        assertThat(result.getParticipantCount())
                .isEqualTo(participantRepo.findByVersion_VersionId(vId).size());
        assertThat(result.getLaneCount())
                .isEqualTo(laneRepo.findByVersion_VersionId(vId).size());
        assertThat(result.getElementCount())
                .isEqualTo(elementRepo.findByVersion_VersionId(vId).size());
        assertThat(result.getSequenceFlowCount())
                .isEqualTo(sequenceFlowRepo.findByVersion_VersionId(vId).size());
        assertThat(result.getActivityCount())
                .isEqualTo(activityRepo.findByVersion_VersionId(vId).size());
    }

    @Test
    @DisplayName("no inconsistencies for valid pizza-delivery model")
    void noInconsistencies() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");

        assertThat(result.getInconsistencyCount()).isZero();
        assertThat(result.getInconsistencies()).isEmpty();
        assertThat(result.getVersion().isValid()).isTrue();
    }

    @Test
    @DisplayName("expected structural counts for pizza-delivery")
    void expectedStructure() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");

        assertThat(result.getParticipantCount()).isEqualTo(1);
        assertThat(result.getLaneCount()).isEqualTo(4);
        assertThat(result.getElementCount()).isGreaterThanOrEqualTo(14);
        assertThat(result.getSequenceFlowCount()).isGreaterThanOrEqualTo(14);
        assertThat(result.getStageCount()).isEqualTo(4);
        assertThat(result.getActivityCount()).isEqualTo(9);
        assertThat(result.getRuleCount()).isGreaterThanOrEqualTo(14);
    }

    // ---------------------------------------------------------------
    // Version increment
    // ---------------------------------------------------------------

    @Test
    @DisplayName("second deploy of same processKey creates version 2")
    void secondDeployCreatesVersion2() {
        String key = "VERSION_TEST_" + System.nanoTime();
        var r1 = deployService.deploy(bpmn, config, key);
        var r2 = deployService.deploy(bpmn, config, key);

        assertThat(r1.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(r2.getVersion().getVersionNumber()).isEqualTo(2);
        assertThat(versionRepo.findByProcess_ProcessIdOrderByVersionNumberDesc(
                r2.getVersion().getProcess().getProcessId())).hasSize(2);
    }

    @Test
    @DisplayName("third deploy increments to version 3")
    void thirdDeployCreatesVersion3() {
        String key = "VERSION_TEST3_" + System.nanoTime();
        deployService.deploy(bpmn, config, key);
        deployService.deploy(bpmn, config, key);
        var r3 = deployService.deploy(bpmn, config, key);

        assertThat(r3.getVersion().getVersionNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("different processKeys create independent processes at version 1")
    void differentKeysAreIndependent() {
        String keyA = "PROC_A_" + System.nanoTime();
        String keyB = "PROC_B_" + System.nanoTime();
        var rA = deployService.deploy(bpmn, config, keyA);
        var rB = deployService.deploy(bpmn, config, keyB);

        assertThat(rA.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(rB.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(rA.getVersion().getProcess().getProcessId())
                .isNotEqualTo(rB.getVersion().getProcess().getProcessId());
    }

    // ---------------------------------------------------------------
    // Config deduplication
    // ---------------------------------------------------------------

    @Test
    @DisplayName("identical config content is reused (same configId)")
    void identicalConfigIsReused() {
        String keyA = "CFG_A_" + System.nanoTime();
        String keyB = "CFG_B_" + System.nanoTime();
        var rA = deployService.deploy(bpmn, config, keyA);
        var rB = deployService.deploy(bpmn, config, keyB);

        assertThat(rA.getVersion().getConfig().getConfigId())
                .isEqualTo(rB.getVersion().getConfig().getConfigId());
    }

    // ---------------------------------------------------------------
    // Version metadata
    // ---------------------------------------------------------------

    @Test
    @DisplayName("deployed version has status ACTIVE and stores full BPMN XML")
    void versionMetadata() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");
        var version = result.getVersion();

        assertThat(version.getStatus()).isEqualTo("ACTIVE");
        assertThat(version.isValid()).isTrue();
        assertThat(version.getBpmnXml()).contains("<?xml").contains("bpmn");
        assertThat(version.getProcess().getProcessKey()).isEqualTo("PIZZA_DELIVERY");
    }

    // ---------------------------------------------------------------
    // Error paths
    // ---------------------------------------------------------------

    @Test
    @DisplayName("throws IllegalStateException for invalid BPMN content")
    void throwsForInvalidBpmn() {
        byte[] invalid = "this is not valid XML".getBytes();

        assertThatThrownBy(() -> deployService.deploy(invalid, config, "BROKEN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    @DisplayName("throws for invalid config content")
    void throwsForInvalidConfig() {
        byte[] invalidConfig = ": : : not yaml".getBytes();

        assertThatThrownBy(() -> deployService.deploy(bpmn, invalidConfig, "BROKEN"))
                .isInstanceOf(Exception.class);
    }
}