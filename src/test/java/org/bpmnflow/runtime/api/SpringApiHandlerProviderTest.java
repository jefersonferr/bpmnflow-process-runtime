package org.bpmnflow.runtime.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("SpringApiHandlerProvider")
class SpringApiHandlerProviderTest {

    RestTemplate              restTemplate;
    MockRestServiceServer     mockServer;
    SpringApiHandlerProvider  provider;

    static final String ENDPOINT     = "https://api.example.com/v1/charge";
    static final Long   INSTANCE_ID  = 1L;
    static final String ACTIVITY_ABB = "SC-PMT";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer   = MockRestServiceServer.createServer(restTemplate);
        provider     = new SpringApiHandlerProvider(restTemplate, new ObjectMapper());
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private ApiHandlerContext context(String payloadTemplate,
                                      Map<String, String> vars,
                                      List<ApiHandlerContext.OutputMapping> mappings) {
        return ApiHandlerContext.builder()
                .instanceId(INSTANCE_ID)
                .activityAbbreviation(ACTIVITY_ABB)
                .endpoint(ENDPOINT)
                .method("POST")
                .headers(Map.of())
                .payloadTemplate(payloadTemplate)
                .instanceVariables(vars)
                .outputMappings(mappings)
                .build();
    }

    // ---------------------------------------------------------------
    // Successful calls
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("successful API calls")
    class SuccessTests {

        @Test
        @DisplayName("returns empty map when no outputMappings defined")
        void noOutputMappings_returnsEmpty() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of()));

            assertTrue(result.isEmpty());
            mockServer.verify();
        }

        @Test
        @DisplayName("extracts single top-level JSONPath field")
        void extractsTopLevelField() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess(
                            "{\"txn_id\":\"TXN-999\",\"pay_status\":\"APPROVED\"}",
                            MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of(
                    new ApiHandlerContext.OutputMapping("txn_id",    "$.transaction_id"),
                    new ApiHandlerContext.OutputMapping("pay_status", "$.status")
            )));

            assertEquals("TXN-999",  result.get("txn_id"));
            assertEquals("APPROVED", result.get("pay_status"));
            mockServer.verify();
        }

        @Test
        @DisplayName("extracts nested JSONPath field ($.data.id)")
        void extractsNestedField() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess(
                            "{\"ext_id\":\"ABC-1\",\"amount\":99.90}",
                            MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of(
                    new ApiHandlerContext.OutputMapping("ext_id", "$.data.id")
            )));

            assertEquals("ABC-1", result.get("ext_id"));
            mockServer.verify();
        }

        @Test
        @DisplayName("resolves ${var.name} placeholders in payload template")
        void resolvesPlaceholders() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andExpect(content().string("{\"customer\":\"cust-42\",\"amount\":\"150.00\"}"))
                    .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            provider.execute(context(
                    "{\"customer\":\"${var.customerId}\",\"amount\":\"${var.valor}\"}",
                    Map.of("customerId", "cust-42", "valor", "150.00"),
                    List.of()
            ));

            mockServer.verify();
        }

        @Test
        @DisplayName("leaves unknown placeholder unchanged when variable not found")
        void unknownPlaceholder_leftUnchanged() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andExpect(content().string("{\"id\":\"${var.missing}\"}"))
                    .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            provider.execute(context(
                    "{\"id\":\"${var.missing}\"}",
                    Map.of(),
                    List.of()
            ));

            mockServer.verify();
        }

        @Test
        @DisplayName("returns null for JSONPath not found in response")
        void missingJsonPath_returnsNull() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess("{\"other\":\"value\"}", MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of(
                    new ApiHandlerContext.OutputMapping("txn_id", "$.transaction_id")
            )));

            assertNull(result.get("txn_id"));
            mockServer.verify();
        }

        @Test
        @DisplayName("returns null for all mappings when response body is empty")
        void emptyResponseBody_returnsNullMappings() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of(
                    new ApiHandlerContext.OutputMapping("txn_id", "$.transaction_id")
            )));

            assertTrue(result.containsKey("txn_id"));
            assertNull(result.get("txn_id"));
            mockServer.verify();
        }

        @Test
        @DisplayName("sets Content-Type: application/json when payload is present and no header set")
        void setsDefaultContentType() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andExpect(header("Content-Type", "application/json"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            provider.execute(context("{\"key\":\"val\"}", Map.of(), List.of()));

            mockServer.verify();
        }

        @Test
        @DisplayName("numeric JSON value is returned as string")
        void numericValueReturnedAsString() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess("{\"count\":42}", MediaType.APPLICATION_JSON));

            Map<String, String> result = provider.execute(context(null, Map.of(), List.of(
                    new ApiHandlerContext.OutputMapping("count", "$.count")
            )));

            assertEquals("42", result.get("count"));
            mockServer.verify();
        }

        @Test
        @DisplayName("providerName returns SpringApiHandlerProvider")
        void providerName() {
            assertEquals("SpringApiHandlerProvider", provider.providerName());
        }
    }

    // ---------------------------------------------------------------
    // Failure cases — Opção A (fail fast)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("failure cases — Opção A fail fast")
    class FailureTests {

        @Test
        @DisplayName("throws ApiHandlerException on HTTP 400")
        void throws_on400() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withBadRequest().body("{\"error\":\"bad input\"}"));

            ApiHandlerException ex = assertThrows(ApiHandlerException.class,
                    () -> provider.execute(context(null, Map.of(), List.of())));

            assertTrue(ex.getMessage().contains("400"));
            assertTrue(ex.getMessage().contains(ACTIVITY_ABB));
            mockServer.verify();
        }

        @Test
        @DisplayName("throws ApiHandlerException on HTTP 500")
        void throws_on500() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withServerError());

            ApiHandlerException ex = assertThrows(ApiHandlerException.class,
                    () -> provider.execute(context(null, Map.of(), List.of())));

            assertTrue(ex.getMessage().contains("500"));
            mockServer.verify();
        }

        @Test
        @DisplayName("throws ApiHandlerException on HTTP 404")
        void throws_on404() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withResourceNotFound());

            assertThrows(ApiHandlerException.class,
                    () -> provider.execute(context(null, Map.of(), List.of())));

            mockServer.verify();
        }

        @Test
        @DisplayName("throws ApiHandlerException when response body is not valid JSON")
        void throws_whenResponseNotJson() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess("NOT JSON", MediaType.TEXT_PLAIN));

            assertThrows(ApiHandlerException.class,
                    () -> provider.execute(context(null, Map.of(), List.of(
                            new ApiHandlerContext.OutputMapping("txn_id", "$.id")
                    ))));

            mockServer.verify();
        }

        @Test
        @DisplayName("error message includes endpoint and activity abbreviation")
        void errorMessage_includesContext() {
            mockServer.expect(requestTo(ENDPOINT))
                    .andRespond(withBadRequest().body("invalid"));

            ApiHandlerException ex = assertThrows(ApiHandlerException.class,
                    () -> provider.execute(context(null, Map.of(), List.of())));

            assertTrue(ex.getMessage().contains(ENDPOINT));
            assertTrue(ex.getMessage().contains(ACTIVITY_ABB));
            mockServer.verify();
        }
    }

    // ---------------------------------------------------------------
    // GET request (no body)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("executes GET request without payload")
    void getRequest_noPayload() {
        ApiHandlerContext getCtx = ApiHandlerContext.builder()
                .instanceId(INSTANCE_ID)
                .activityAbbreviation(ACTIVITY_ABB)
                .endpoint(ENDPOINT)
                .method("GET")
                .headers(Map.of())
                .payloadTemplate(null)
                .instanceVariables(Map.of())
                .outputMappings(List.of(
                        new ApiHandlerContext.OutputMapping("name", "$.name")
                ))
                .build();

        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"pizza\"}", MediaType.APPLICATION_JSON));

        Map<String, String> result = provider.execute(getCtx);

        assertEquals("pizza", result.get("name"));
        mockServer.verify();
    }
}