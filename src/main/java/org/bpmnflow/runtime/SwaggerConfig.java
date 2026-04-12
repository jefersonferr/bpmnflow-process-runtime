package org.bpmnflow.runtime;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BPMNFlow Process Runtime")
                        .version("1.0.0")
                        .description(
                                "Process runtime for BPMNFlow — deploys BPMN models parsed by bpmnflow-core, " +
                                        "persists all structural and derived data, and executes workflow instances via REST.\n\n" +
                                        "**Tag groups:**\n" +
                                        "- **Process** — engine endpoints exposed by the Spring Boot Starter (in-memory model)\n" +
                                        "- **BPMN** — model management: deploy, parse and version process definitions\n" +
                                        "- **Workflow** — runtime: start, advance and inspect workflow instances")
                        .contact(new Contact()
                                .name("Jeferson Ferreira")
                                .url("https://github.com/jefersonferr")));
    }
}