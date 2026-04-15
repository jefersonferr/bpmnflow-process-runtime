package org.bpmnflow.runtime.service.deploy;

import org.bpmnflow.runtime.model.entity.BpmnConfigEntity;
import org.bpmnflow.runtime.repository.BpmnConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BpmnConfigPersistor}.
 * Uses Mockito — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BpmnConfigPersistor")
public class BpmnConfigPersistorTest {

    @Mock  BpmnConfigRepository  configRepo;
    @InjectMocks BpmnConfigPersistor persistor;

    private String configYaml;

    @BeforeEach
    void loadYaml() throws Exception {
        configYaml = Files.readString(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("bpmn-config.yaml")).toURI()));
    }

    @Test
    @DisplayName("returns existing entity when hash matches — no insert")
    void returnsExistingWhenHashMatches() {
        BpmnConfigEntity existing = BpmnConfigEntity.builder()
                .configId(1L).configName("bpmn-config").configVersion("abc123")
                .configHash("hash").configYaml(configYaml).build();

        when(configRepo.findByConfigHash(anyString())).thenReturn(Optional.of(existing));

        BpmnConfigEntity result = persistor.persist(configYaml);

        assertThat(result).isSameAs(existing);
        verify(configRepo, never()).save(any());
    }

    @Test
    @DisplayName("saves new entity when no matching hash exists")
    void savesNewEntityWhenNoHashMatch() {
        when(configRepo.findByConfigHash(anyString())).thenReturn(Optional.empty());
        when(configRepo.save(any())).thenAnswer(inv -> {
            BpmnConfigEntity e = inv.getArgument(0);
            // simulate DB-generated ID
            return BpmnConfigEntity.builder()
                    .configId(42L)
                    .configName(e.getConfigName())
                    .configVersion(e.getConfigVersion())
                    .configHash(e.getConfigHash())
                    .configYaml(e.getConfigYaml())
                    .engine(e.getEngine())
                    .build();
        });

        BpmnConfigEntity result = persistor.persist(configYaml);

        verify(configRepo, times(1)).save(any());
        assertThat(result.getConfigId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("saved entity carries the original YAML and a non-blank hash")
    void savedEntityHasYamlAndHash() {
        when(configRepo.findByConfigHash(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<BpmnConfigEntity> captor = ArgumentCaptor.forClass(BpmnConfigEntity.class);
        when(configRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        persistor.persist(configYaml);

        BpmnConfigEntity saved = captor.getValue();
        assertThat(saved.getConfigYaml()).isEqualTo(configYaml);
        assertThat(saved.getConfigHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("throws IllegalArgumentException for non-map YAML root")
    void throwsForNonMapYaml() {
        when(configRepo.findByConfigHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistor.persist("- item1\n- item2\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map at root level");
    }

    @Test
    @DisplayName("two calls with identical YAML share the same entity (idempotent)")
    void twoCallsWithSameYamlShareEntity() {
        BpmnConfigEntity shared = BpmnConfigEntity.builder()
                .configId(7L).configName("bpmn-config").configVersion("x")
                .configHash("h").configYaml(configYaml).build();

        when(configRepo.findByConfigHash(anyString())).thenReturn(Optional.of(shared));

        BpmnConfigEntity r1 = persistor.persist(configYaml);
        BpmnConfigEntity r2 = persistor.persist(configYaml);

        assertThat(r1).isSameAs(r2);
        verify(configRepo, never()).save(any());
    }
}