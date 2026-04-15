package org.bpmnflow.runtime.service.deploy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BpmnModelParser}.
 * No Spring context, no database — the parser is a pure function.
 * Tests run in milliseconds.
 */
@DisplayName("BpmnModelParser")
public class BpmnModelParserTest {

    private BpmnModelParser parser;
    private byte[] bpmn;
    private byte[] config;

    @BeforeEach
    void setUp() throws Exception {
        parser = new BpmnModelParser();
        bpmn   = Files.readAllBytes(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("pizza-delivery.bpmn")).toURI()));
        config = Files.readAllBytes(Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("bpmn-config.yaml")).toURI()));
    }

    @Test
    @DisplayName("returns non-null workflow and document for valid inputs")
    void returnsNonNullResult() {
        ParsedBpmnModel result = parser.parse(bpmn, config);

        assertThat(result.getWorkflow()).isNotNull();
        assertThat(result.getDocument()).isNotNull();
    }

    @Test
    @DisplayName("preserves original XML and YAML strings")
    void preservesOriginalStrings() {
        ParsedBpmnModel result = parser.parse(bpmn, config);

        assertThat(result.getBpmnXml()).contains("<?xml").contains("bpmn");
        assertThat(result.getConfigYaml()).contains("bpmn_model_parser");
    }

    @Test
    @DisplayName("workflow contains activities and rules for pizza-delivery")
    void workflowHasActivitiesAndRules() {
        ParsedBpmnModel result = parser.parse(bpmn, config);

        assertThat(result.getWorkflow().getActivities()).isNotEmpty();
        assertThat(result.getWorkflow().getRules()).isNotEmpty();
    }

    @Test
    @DisplayName("throws IllegalStateException for invalid BPMN bytes")
    void throwsForInvalidBpmn() {
        byte[] invalid = "this is not valid bpmn xml".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(invalid, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    @DisplayName("throws IllegalStateException for malformed XML")
    void throwsForMalformedXml() {
        byte[] malformed = "<unclosed".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(malformed, config))
                .isInstanceOf(IllegalStateException.class);
    }
}