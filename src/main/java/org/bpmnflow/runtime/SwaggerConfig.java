package org.bpmnflow.runtime;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BPMNFlow Process Runtime")
                        .version("1.0.0")
                        .description("""
                                Process runtime for BPMNFlow — deploys BPMN models parsed by bpmnflow-core, \
                                persists all structural and derived data, and executes workflow instances via REST.

                                **Tag groups:**
                                - **Process** — engine endpoints exposed by the Spring Boot Starter (in-memory model)
                                - **BPMN** — model management: deploy, parse and version process definitions
                                - **Workflow** — runtime: start, advance and inspect workflow instances
                                """)
                        .contact(new Contact()
                                .name("Jeferson Ferreira")
                                .url("https://github.com/jefersonferr")));
    }
}