package org.bpmnflow.runtime.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the {@link ApiHandlerProvider} SPI.
 *
 * <p>Registers {@link SpringApiHandlerProvider} as the default implementation
 * using {@code @ConditionalOnMissingBean}. This guarantees that any application
 * that declares its own {@link ApiHandlerProvider} bean — such as the
 * {@code bpmnflow-process-runtime-oracle} project with its
 * {@code PlSqlApiHandlerProvider} or {@code SelectAiApiHandlerProvider} —
 * will automatically replace this default without any additional configuration.</p>
 *
 * <p>Also registers a shared {@link RestTemplate} bean if none is present,
 * following the same conditional pattern to allow full customization
 * (timeouts, interceptors, SSL) by the consuming application.</p>
 */
@Configuration
public class ApiHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(ApiHandlerProvider.class)
    public ApiHandlerProvider apiHandlerProvider(RestTemplate restTemplate,
                                                 ObjectMapper objectMapper) {
        return new SpringApiHandlerProvider(restTemplate, objectMapper);
    }
}