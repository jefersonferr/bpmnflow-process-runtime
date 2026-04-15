package org.bpmnflow.runtime.service.deploy;

import lombok.Builder;
import lombok.Getter;
import org.bpmnflow.model.Workflow;
import org.w3c.dom.Document;

/**
 * Immutable value object produced by {@link BpmnModelParser}.
 *
 * <p>Carries both parsing results in a single unit so no caller needs to
 * run two separate parsing steps or pass raw bytes around after the initial
 * parse boundary.</p>
 *
 * <ul>
 *   <li>{@code workflow} — BPMNFlow derived model (Layer 4 source).</li>
 *   <li>{@code document} — JAXP DOM of the original XML (Layer 3 source).</li>
 *   <li>{@code bpmnXml}  — original XML string kept for storage.</li>
 *   <li>{@code configYaml} — original config YAML string kept for storage.</li>
 * </ul>
 */
@Getter
@Builder
public class ParsedBpmnModel {

    private final Workflow workflow;
    private final Document document;
    private final String   bpmnXml;
    private final String   configYaml;
}