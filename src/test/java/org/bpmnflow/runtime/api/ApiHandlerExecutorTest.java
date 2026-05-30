package org.bpmnflow.runtime.api;

import org.bpmnflow.runtime.model.entity.BpmnElementEntity;
import org.bpmnflow.runtime.model.entity.BpmnExtensionPropertyEntity;
import org.bpmnflow.runtime.model.entity.ProcessActivityEntity;
import org.bpmnflow.runtime.model.entity.WfInstanceVariableEntity;
import org.bpmnflow.runtime.repository.BpmnExtensionPropertyRepository;
import org.bpmnflow.runtime.repository.WfInstanceVariableRepository;
import org.bpmnflow.runtime.service.VariableUpsertHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ApiHandlerExecutor")
@ExtendWith(MockitoExtension.class)
class ApiHandlerExecutorTest {

    @Mock BpmnExtensionPropertyRepository extPropRepo;
    @Mock WfInstanceVariableRepository    variableRepo;
    @Mock VariableUpsertHelper            variableUpsertHelper;
    @Mock ApiHandlerProvider              apiHandlerProvider;

    ApiHandlerExecutor executor;

    static final Long INSTANCE_ID  = 42L;
    static final Long ELEMENT_ID   = 10L;

    @BeforeEach
    void setUp() {
        executor = new ApiHandlerExecutor(
                extPropRepo, variableRepo, variableUpsertHelper, apiHandlerProvider);
        lenient().when(apiHandlerProvider.providerName()).thenReturn("MockProvider");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ProcessActivityEntity activityWithElement(String abbreviation) {
        BpmnElementEntity element = BpmnElementEntity.builder()
                .elementId(ELEMENT_ID).build();
        return ProcessActivityEntity.builder()
                .abbreviation(abbreviation)
                .element(element)
                .build();
    }

    private ProcessActivityEntity activityWithoutElement(String abbreviation) {
        return ProcessActivityEntity.builder()
                .abbreviation(abbreviation)
                .element(null)
                .build();
    }

    private BpmnExtensionPropertyEntity prop(String name, String value) {
        return BpmnExtensionPropertyEntity.builder()
                .ownerType("ELEMENT")
                .ownerId(ELEMENT_ID)
                .propertyName(name)
                .propertyValue(value)
                .build();
    }

    private WfInstanceVariableEntity variable(String key, String value) {
        return WfInstanceVariableEntity.builder()
                .variableKey(key)
                .variableValue(value)
                .build();
    }

    // ---------------------------------------------------------------
    // No-op cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("no-op conditions")
    class NoOpTests {

        @Test
        @DisplayName("is a no-op when activity has no element")
        void noOp_whenNoElement() {
            ProcessActivityEntity activity = activityWithoutElement("SC-PMT");
            executor.executeIfApiActivity(INSTANCE_ID, activity);
            verifyNoInteractions(extPropRepo, variableRepo, apiHandlerProvider);
        }

        @Test
        @DisplayName("is a no-op when activity has no extension properties")
        void noOp_whenNoExtensionProperties() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of());

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verifyNoInteractions(variableRepo, apiHandlerProvider);
        }

        @Test
        @DisplayName("is a no-op when connectorId property is absent")
        void noOp_whenNoConnectorId() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(prop("endpoint", "https://api.example.com")));

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verifyNoInteractions(variableRepo, apiHandlerProvider);
        }

        @Test
        @DisplayName("is a no-op when connectorId property is blank")
        void noOp_whenConnectorIdBlank() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(prop("connectorId", "  ")));

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verifyNoInteractions(variableRepo, apiHandlerProvider);
        }
    }

    // ---------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validation failures")
    class ValidationTests {

        @Test
        @DisplayName("throws ApiHandlerException when endpoint is missing")
        void throws_whenEndpointMissing() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("method", "POST")));

            ApiHandlerException ex = assertThrows(ApiHandlerException.class,
                    () -> executor.executeIfApiActivity(INSTANCE_ID, activity));

            assertTrue(ex.getMessage().contains("endpoint"));
            assertTrue(ex.getMessage().contains("SC-PMT"));
            verifyNoInteractions(apiHandlerProvider);
        }

        @Test
        @DisplayName("throws ApiHandlerException when method is missing")
        void throws_whenMethodMissing() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("endpoint",    "https://api.example.com")));

            ApiHandlerException ex = assertThrows(ApiHandlerException.class,
                    () -> executor.executeIfApiActivity(INSTANCE_ID, activity));

            assertTrue(ex.getMessage().contains("method"));
            verifyNoInteractions(apiHandlerProvider);
        }
    }

    // ---------------------------------------------------------------
    // Successful execution
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("successful execution")
    class SuccessTests {

        @Test
        @DisplayName("delegates to provider with correct context")
        void delegatesToProvider_withCorrectContext() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId",                    "http-connector"),
                            prop("endpoint",                       "https://api.pagamentos.com/v1/auth"),
                            prop("method",                         "POST"),
                            prop("payloadTemplate",                "{\"amount\":\"${var.valor}\"}"),
                            prop("outputMapping.txn_id",           "$.transaction_id"),
                            prop("outputMapping.status",           "$.status")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of(variable("valor", "100.00")));

            when(apiHandlerProvider.execute(any()))
                    .thenReturn(Map.of("txn_id", "TXN-123", "status", "APPROVED"));

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            ArgumentCaptor<ApiHandlerContext> captor = ArgumentCaptor.forClass(ApiHandlerContext.class);
            verify(apiHandlerProvider).execute(captor.capture());

            ApiHandlerContext ctx = captor.getValue();
            assertEquals(INSTANCE_ID,                            ctx.getInstanceId());
            assertEquals("SC-PMT",                               ctx.getActivityAbbreviation());
            assertEquals("https://api.pagamentos.com/v1/auth",   ctx.getEndpoint());
            assertEquals("POST",                                  ctx.getMethod());
            assertEquals("{\"amount\":\"${var.valor}\"}",         ctx.getPayloadTemplate());
            assertEquals(Map.of("valor", "100.00"),               ctx.getInstanceVariables());
            assertEquals(2, ctx.getOutputMappings().size());
        }

        @Test
        @DisplayName("persists all non-null response variables via VariableUpsertHelper")
        void persistsResponseVariables() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("endpoint",    "https://api.example.com"),
                            prop("method",      "POST")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of());

            when(apiHandlerProvider.execute(any()))
                    .thenReturn(Map.of("txn_id", "TXN-123", "status", "APPROVED"));

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verify(variableUpsertHelper).upsert(INSTANCE_ID, "txn_id",  "STRING", "TXN-123");
            verify(variableUpsertHelper).upsert(INSTANCE_ID, "status",  "STRING", "APPROVED");
        }

        @Test
        @DisplayName("does not persist null response values")
        void doesNotPersistNullValues() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("endpoint",    "https://api.example.com"),
                            prop("method",      "POST")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of());

            Map<String, String> responseWithNull = new java.util.HashMap<>();
            responseWithNull.put("txn_id", "TXN-123");
            responseWithNull.put("status", null);
            when(apiHandlerProvider.execute(any())).thenReturn(responseWithNull);

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verify(variableUpsertHelper).upsert(INSTANCE_ID, "txn_id", "STRING", "TXN-123");
            verify(variableUpsertHelper, never()).upsert(eq(INSTANCE_ID), eq("status"), any(), isNull());
        }

        @Test
        @DisplayName("extracts header properties with header. prefix into context headers")
        void extractsHeadersFromProps() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId",           "http-connector"),
                            prop("endpoint",              "https://api.example.com"),
                            prop("method",                "POST"),
                            prop("header.Authorization",  "Bearer ${var.token}")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of(variable("token", "abc123")));

            when(apiHandlerProvider.execute(any())).thenReturn(Map.of());

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            ArgumentCaptor<ApiHandlerContext> captor = ArgumentCaptor.forClass(ApiHandlerContext.class);
            verify(apiHandlerProvider).execute(captor.capture());

            Map<String, String> headers = captor.getValue().getHeaders();
            assertEquals("Bearer ${var.token}", headers.get("Authorization"));
        }

        @Test
        @DisplayName("propagates ApiHandlerException from provider without wrapping")
        void propagatesApiHandlerException() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("endpoint",    "https://api.example.com"),
                            prop("method",      "POST")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of());

            when(apiHandlerProvider.execute(any()))
                    .thenThrow(new ApiHandlerException("upstream error"));

            assertThrows(ApiHandlerException.class,
                    () -> executor.executeIfApiActivity(INSTANCE_ID, activity));

            verifyNoInteractions(variableUpsertHelper);
        }

        @Test
        @DisplayName("does not persist any variables when provider returns empty map")
        void noVariables_whenProviderReturnsEmpty() {
            ProcessActivityEntity activity = activityWithElement("SC-PMT");
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connectorId", "http-connector"),
                            prop("endpoint",    "https://api.example.com"),
                            prop("method",      "GET")));

            when(variableRepo.findByInstance_InstanceId(INSTANCE_ID))
                    .thenReturn(List.of());

            when(apiHandlerProvider.execute(any())).thenReturn(Map.of());

            executor.executeIfApiActivity(INSTANCE_ID, activity);

            verifyNoInteractions(variableUpsertHelper);
        }
    }
}