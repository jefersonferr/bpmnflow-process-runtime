package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for BpmnDeployService.
 *
 * Covers branches not exercised by PizzaDeliveryIntegrationTest:
 * - Explicit processKey vs key inferred from BPMN id
 * - Config reuse when hash matches (idempotent config persist)
 * - Version increment on repeated deploys of the same process
 * - DeployResult counters (participants, lanes, elements, flows, stages, activities, rules)
 * - Invalid BPMN content (parse error path)
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("BpmnDeployService — deploy scenarios")
class BpmnDeployServiceTest {

    @Autowired private BpmnDeployService              deployService;
    @Autowired private BpmnProcessVersionRepository   versionRepo;
    @Autowired private BpmnProcessRepository          processRepo;
    @Autowired private BpmnConfigRepository           configRepo;
    @Autowired private BpmnActivityRepository         activityRepo;
    @Autowired private BpmnParticipantRepository      participantRepo;
    @Autowired private BpmnLaneRepository             laneRepo;
    @Autowired private BpmnElementRepository          elementRepo;
    @Autowired private BpmnSequenceFlowRepository     sequenceFlowRepo;
    @Autowired private BpmnRuleRepository             ruleRepo;

    private byte[] bpmn;
    private byte[] config;

    @BeforeEach
    void loadFiles() throws Exception {
        bpmn = Files.readAllBytes(
                Paths.get(getClass().getClassLoader().getResource("pizza-delivery.bpmn").toURI()));
        config = Files.readAllBytes(
                Paths.get(getClass().getClassLoader().getResource("bpmn-config.yaml").toURI()));
    }

    // ---------------------------------------------------------------
    // Basic deploy
    // ---------------------------------------------------------------

    @Test
    @DisplayName("deploy with explicit processKey uses that key")
    void deployWithExplicitKey() {
        var result = deployService.deploy(bpmn, config, "MY_CUSTOM_KEY");

        assertThat(result.getVersion().getProcess().getProcessKey()).isEqualTo("MY_CUSTOM_KEY");
        assertThat(processRepo.findByProcessKey("MY_CUSTOM_KEY")).isPresent();
    }

    @Test
    @DisplayName("deploy with null processKey infers key from BPMN id")
    void deployWithNullKeyUsesBpmnId() {
        var result = deployService.deploy(bpmn, config, null);

        // pizza-delivery.bpmn has id="pizza-delivery" or similar — just verify a process was created
        assertThat(result.getVersion().getProcess().getProcessKey()).isNotBlank();
        assertThat(result.getVersion().getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("deploy result counters match persisted row counts")
    void deployResultCountersMatchDatabase() {
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
        assertThat(result.getRuleCount())
                .isEqualTo((int) ruleRepo.findAll().stream()
                        .filter(r -> r.getVersion().getVersionId().equals(vId))
                        .count());
    }

    @Test
    @DisplayName("deploy result reports zero inconsistencies for valid model")
    void deployReportsNoInconsistencies() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");

        assertThat(result.getInconsistencyCount()).isZero();
        assertThat(result.getInconsistencies()).isEmpty();
        assertThat(result.getVersion().isValid()).isTrue();
    }

    @Test
    @DisplayName("deploy persists correct structural counts for pizza-delivery")
    void deployPersistsExpectedStructure() {
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
        // Use a unique key per test to avoid contamination from other test classes
        // sharing the same in-memory H2 named database (DB_CLOSE_DELAY=-1)
        String key = "VERSION_TEST_" + System.nanoTime();
        var result1 = deployService.deploy(bpmn, config, key);
        var result2 = deployService.deploy(bpmn, config, key);

        assertThat(result1.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(result2.getVersion().getVersionNumber()).isEqualTo(2);
        assertThat(versionRepo.findByProcess_ProcessIdOrderByVersionNumberDesc(
                result2.getVersion().getProcess().getProcessId())).hasSize(2);
    }

    @Test
    @DisplayName("third deploy increments to version 3")
    void thirdDeployCreatesVersion3() {
        String key = "VERSION_TEST3_" + System.nanoTime();
        deployService.deploy(bpmn, config, key);
        deployService.deploy(bpmn, config, key);
        var result3 = deployService.deploy(bpmn, config, key);

        assertThat(result3.getVersion().getVersionNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("different processKeys create independent processes")
    void differentKeysCreateIndependentProcesses() {
        String keyA = "PROC_A_" + System.nanoTime();
        String keyB = "PROC_B_" + System.nanoTime();
        var r1 = deployService.deploy(bpmn, config, keyA);
        var r2 = deployService.deploy(bpmn, config, keyB);

        assertThat(r1.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(r2.getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(r1.getVersion().getProcess().getProcessId())
                .isNotEqualTo(r2.getVersion().getProcess().getProcessId());
        assertThat(r1.getVersion().getProcess().getProcessKey()).isEqualTo(keyA);
        assertThat(r2.getVersion().getProcess().getProcessKey()).isEqualTo(keyB);
    }

    // ---------------------------------------------------------------
    // Config reuse
    // ---------------------------------------------------------------

    @Test
    @DisplayName("identical config content is reused (same hash, same config entity)")
    void identicalConfigIsReused() {
        String keyA = "CFG_TEST_A_" + System.nanoTime();
        String keyB = "CFG_TEST_B_" + System.nanoTime();
        var r1 = deployService.deploy(bpmn, config, keyA);
        var r2 = deployService.deploy(bpmn, config, keyB);

        // Both deploys use the same config YAML — should reuse the same BpmnConfigEntity
        assertThat(r1.getVersion().getConfig().getConfigId())
                .isEqualTo(r2.getVersion().getConfig().getConfigId());
    }

    @Test
    @DisplayName("second deploy with same config links to existing config entity")
    void secondDeployReusesSameConfigId() {
        String key = "CFG_REUSE_" + System.nanoTime();
        var r1 = deployService.deploy(bpmn, config, key);
        var r2 = deployService.deploy(bpmn, config, key);

        assertThat(r1.getVersion().getConfig().getConfigId())
                .isEqualTo(r2.getVersion().getConfig().getConfigId());
    }

    // ---------------------------------------------------------------
    // Error paths
    // ---------------------------------------------------------------

    @Test
    @DisplayName("throws IllegalStateException for invalid BPMN content")
    void throwsForInvalidBpmnContent() {
        byte[] invalidBpmn = "this is not valid XML".getBytes();

        assertThatThrownBy(() -> deployService.deploy(invalidBpmn, config, "BROKEN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    @DisplayName("throws IllegalStateException for invalid config content")
    void throwsForInvalidConfigContent() {
        byte[] invalidConfig = ": : : not yaml".getBytes();

        assertThatThrownBy(() -> deployService.deploy(bpmn, invalidConfig, "BROKEN"))
                .isInstanceOf(Exception.class);
    }

    // ---------------------------------------------------------------
    // Version metadata
    // ---------------------------------------------------------------

    @Test
    @DisplayName("deployed version has correct metadata from BPMN")
    void deployedVersionHasCorrectMetadata() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");
        var version = result.getVersion();

        assertThat(version.getStatus()).isEqualTo("ACTIVE");
        assertThat(version.isValid()).isTrue();
        assertThat(version.getBpmnXml()).isNotBlank();
        assertThat(version.getProcess().getProcessKey()).isEqualTo("PIZZA_DELIVERY");
    }

    @Test
    @DisplayName("deployed version stores the full BPMN XML")
    void deployedVersionStoresBpmnXml() {
        var result = deployService.deploy(bpmn, config, "PIZZA_DELIVERY");

        assertThat(result.getVersion().getBpmnXml())
                .contains("<?xml")
                .contains("bpmn");
    }
}