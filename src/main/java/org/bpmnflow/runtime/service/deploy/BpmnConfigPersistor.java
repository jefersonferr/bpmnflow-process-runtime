package org.bpmnflow.runtime.service.deploy;

import lombok.RequiredArgsConstructor;
import org.bpmnflow.runtime.model.entity.BpmnConfigEntity;
import org.bpmnflow.runtime.model.entity.BpmnConfigPropertyEntity;
import org.bpmnflow.runtime.repository.BpmnConfigRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Persists {@link BpmnConfigEntity} with deduplication by SHA-256 hash.
 *
 * <p>If a config with the same content was previously stored, the existing
 * entity is returned without inserting a duplicate row.  This ensures that
 * multiple deploys using the same {@code bpmn-config.yaml} share one config
 * record while full traceability is preserved.</p>
 *
 * <p>The YAML is parsed with SnakeYAML only to extract the engine name and
 * the {@code model_properties} metamodel; the raw YAML string is always
 * stored verbatim.</p>
 */
@Component
@RequiredArgsConstructor
public class BpmnConfigPersistor {

    private final BpmnConfigRepository configRepo;

    /**
     * Returns an existing config entity if one with the same hash already
     * exists, otherwise parses, builds and saves a new one.
     */
    public BpmnConfigEntity persist(String configYaml) {

        String hash = sha256(configYaml);

        return configRepo.findByConfigHash(hash)
                .orElseGet(() -> buildAndSave(configYaml, hash));
    }

    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private BpmnConfigEntity buildAndSave(String configYaml, String hash) {

        var yaml    = new org.yaml.snakeyaml.Yaml();
        Object raw  = yaml.load(configYaml);

        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(
                    "bpmn-config.yaml must have a map at root level, got: " +
                            (raw == null ? "null" : raw.getClass().getSimpleName()));
        }

        Map<String, Object> root   = (Map<String, Object>) rawMap;
        Map<String, Object> parser = (Map<String, Object>) root.get("bpmn_model_parser");
        String engine = parser != null ? (String) parser.getOrDefault("engine", null) : null;

        BpmnConfigEntity config = BpmnConfigEntity.builder()
                .configName("bpmn-config")
                .configVersion(UUID.randomUUID().toString().substring(0, 8))
                .engine(engine)
                .configYaml(configYaml)
                .configHash(hash)
                .build();

        if (parser != null) {
            Map<String, Object> modelProps =
                    (Map<String, Object>) parser.get("model_properties");
            if (modelProps != null) {
                for (Map.Entry<String, Object> entry : modelProps.entrySet()) {
                    String elementType = entry.getKey();
                    List<Map<String, Object>> properties =
                            (List<Map<String, Object>>) entry.getValue();
                    if (properties == null) continue;
                    for (Map<String, Object> prop : properties) {
                        String propName = (String) prop.get("name");
                        if (propName == null) continue;
                        config.getProperties().add(BpmnConfigPropertyEntity.builder()
                                .config(config)
                                .elementType(elementType)
                                .propertyName(propName)
                                .required(Boolean.TRUE.equals(prop.get("required")))
                                .extension(Boolean.TRUE.equals(prop.get("extension")))
                                .build());
                    }
                }
            }
        }

        return configRepo.save(config);
    }

    // -------------------------------------------------------------------------

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            // Should never happen — SHA-256 is always available.
            return UUID.randomUUID().toString();
        }
    }
}