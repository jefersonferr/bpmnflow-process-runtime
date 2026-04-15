package org.bpmnflow.runtime.service.deploy;

import org.bpmnflow.model.Workflow;
import org.bpmnflow.parser.ConfigLoader;
import org.bpmnflow.parser.ModelParser;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parses raw BPMN and config bytes into a {@link ParsedBpmnModel}.
 *
 * <p>Two independent parsing steps run here and nowhere else:</p>
 * <ol>
 *   <li>BPMNFlow {@link ModelParser} — produces the {@link Workflow} (derived model, Layer 4 source).</li>
 *   <li>JAXP DOM parser — produces the {@link Document} (structural XML, Layer 3 source).</li>
 * </ol>
 *
 * <p>XXE hardening is applied to the DOM factory (disallow-doctype-decl,
 * no external entities) so callers do not need to worry about it.</p>
 *
 * <p>This class has no state and no repository dependencies — it is safe to
 * call from any thread and is trivially testable without Spring context.</p>
 */
@Component
public class BpmnModelParser {

    /**
     * Parses {@code bpmnContent} and {@code configContent} and returns an immutable
     * {@link ParsedBpmnModel} carrying both results.
     *
     * @throws IllegalStateException if either parsing step fails
     */
    public ParsedBpmnModel parse(byte[] bpmnContent, byte[] configContent) {

        String bpmnXml    = new String(bpmnContent,   StandardCharsets.UTF_8);
        String configYaml = new String(configContent, StandardCharsets.UTF_8);

        Workflow workflow = parseBpmnFlow(bpmnContent, configContent);
        Document document = parseDom(bpmnContent);

        return ParsedBpmnModel.builder()
                .workflow(workflow)
                .document(document)
                .bpmnXml(bpmnXml)
                .configYaml(configYaml)
                .build();
    }

    // -------------------------------------------------------------------------

    private Workflow parseBpmnFlow(byte[] bpmnContent, byte[] configContent) {
        try (InputStream modelStream = new ByteArrayInputStream(bpmnContent);
             InputStream cfgStream   = new ByteArrayInputStream(configContent)) {
            var config = ConfigLoader.loadConfig(cfgStream);
            return ModelParser.parser(modelStream, config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse BPMN model: " + e.getMessage(), e);
        }
    }

    private Document parseDom(byte[] bpmnContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",  false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD",    "");
            factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bpmnContent));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse BPMN XML (DOM): " + e.getMessage(), e);
        }
    }
}