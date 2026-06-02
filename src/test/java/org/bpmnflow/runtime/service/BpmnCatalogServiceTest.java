package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.ResourceNotFoundException;
import org.bpmnflow.runtime.dto.ActivityNodeResponse;
import org.bpmnflow.runtime.dto.ActivityNodeResponse.ApiHandlerResponse;
import org.bpmnflow.runtime.dto.ActivityNodeResponse.KeyValue;
import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.BpmnActivityRepository;
import org.bpmnflow.runtime.repository.BpmnExtensionPropertyRepository;
import org.bpmnflow.runtime.repository.BpmnProcessRepository;
import org.bpmnflow.runtime.repository.BpmnProcessVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("BpmnCatalogService")
@ExtendWith(MockitoExtension.class)
class BpmnCatalogServiceTest {

    @Mock BpmnProcessRepository           processRepo;
    @Mock BpmnProcessVersionRepository    versionRepo;
    @Mock BpmnActivityRepository          activityRepo;
    @Mock BpmnExtensionPropertyRepository extPropRepo;

    BpmnCatalogService service;

    static final Long VERSION_ID = 1L;
    static final Long ELEMENT_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new BpmnCatalogService(processRepo, versionRepo, activityRepo, extPropRepo);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ProcessActivityEntity activityWithElement(String abbreviation, String stageCode) {
        BpmnElementEntity element = BpmnElementEntity.builder()
                .elementId(ELEMENT_ID).build();
        return ProcessActivityEntity.builder()
                .abbreviation(abbreviation)
                .stageCode(stageCode)
                .name("Activity " + abbreviation)
                .element(element)
                .conclusions(List.of())
                .build();
    }

    private ProcessActivityEntity activityWithoutElement(String abbreviation) {
        return ProcessActivityEntity.builder()
                .abbreviation(abbreviation)
                .stageCode("CS")
                .name("Activity " + abbreviation)
                .element(null)
                .conclusions(List.of())
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

    // ---------------------------------------------------------------
    // Version guard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("version guard")
    class VersionGuardTests {

        @Test
        @DisplayName("listActivities throws ResourceNotFoundException when version does not exist")
        void listActivities_throws_whenVersionNotFound() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(false);
            assertThrows(ResourceNotFoundException.class,
                    () -> service.listActivities(VERSION_ID));
        }

        @Test
        @DisplayName("listApiActivities throws ResourceNotFoundException when version does not exist")
        void listApiActivities_throws_whenVersionNotFound() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(false);
            assertThrows(ResourceNotFoundException.class,
                    () -> service.listApiActivities(VERSION_ID));
        }
    }

    // ---------------------------------------------------------------
    // listActivities
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("listActivities")
    class ListActivitiesTests {

        @Test
        @DisplayName("plain activity has null apiHandler")
        void plainActivity_hasNullApiHandler() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("CS-SEL", "CS");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            // No connector.id → extPropRepo returns empty or only camunda:property entries
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(prop("stage", "CS"), prop("activity", "SEL")));

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertEquals(1, result.size());
            assertNull(result.getFirst().getApiHandler());
        }

        @Test
        @DisplayName("activity without element has null apiHandler")
        void activityWithoutElement_hasNullApiHandler() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithoutElement("CS-SEL");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertEquals(1, result.size());
            assertNull(result.getFirst().getApiHandler());
            verifyNoInteractions(extPropRepo);
        }

        @Test
        @DisplayName("activity with empty props has null apiHandler")
        void emptyProps_hasNullApiHandler() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("SC-PMT_AUTH", "SC");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of());

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertNull(result.getFirst().getApiHandler());
        }

        @Test
        @DisplayName("connector activity has populated apiHandler with inputMappings and outputMappings")
        void connectorActivity_hasPopulatedApiHandler() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("SC-PMT_AUTH", "SC");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connector.id",            "http-connector"),
                            prop("connector.input.url",     "https://api.pagamentos.com/v1/authorize"),
                            prop("connector.input.method",  "POST"),
                            prop("connector.input.headers", "application/json"),
                            prop("connector.input.payload", "JSON.stringify({...})"),
                            prop("connector.output.txn_id", "var r = S(response).prop('id').value(); r;")));

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertEquals(1, result.size());
            ApiHandlerResponse api = result.getFirst().getApiHandler();
            assertNotNull(api);
            assertEquals("http-connector",                        api.getConnectorId());
            assertEquals("https://api.pagamentos.com/v1/authorize", api.getEndpoint());
            assertEquals("POST",                                   api.getMethod());
            assertEquals(0,                                        api.getRetries());
            assertNotNull(api.getTaskHeaders());
            assertTrue(api.getTaskHeaders().isEmpty());

            // inputMappings: headers and payload (url and method are extracted separately)
            assertEquals(2, api.getInputMappings().size());
            assertEquals("headers",          api.getInputMappings().get(0).getKey());
            assertEquals("application/json", api.getInputMappings().get(0).getValue());
            assertEquals("payload",          api.getInputMappings().get(1).getKey());

            // outputMappings
            assertEquals(1, api.getOutputMappings().size());
            assertEquals("txn_id", api.getOutputMappings().getFirst().getKey());
        }

        @Test
        @DisplayName("connector activity with no inputMappings has null inputMappings list")
        void noInputMappings_returnsNull() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("SC-PMT_AUTH", "SC");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connector.id",           "http-connector"),
                            prop("connector.input.url",    "https://api.example.com"),
                            prop("connector.input.method", "GET")));

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            ApiHandlerResponse api = result.getFirst().getApiHandler();
            assertNotNull(api);
            assertNull(api.getInputMappings());
            assertNull(api.getOutputMappings());
        }

        @Test
        @DisplayName("activityCode derived from abbreviation with stageCode prefix")
        void activityCode_derivedFromAbbreviation() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("CS-SEL", "CS");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            when(extPropRepo.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertEquals("SEL", result.getFirst().getActivityCode());
            assertEquals("CS",  result.getFirst().getStageCode());
        }

        @Test
        @DisplayName("activityCode falls back to abbreviation when prefix does not match")
        void activityCode_fallsBack_whenPrefixMismatch() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = ProcessActivityEntity.builder()
                    .abbreviation("CS-SEL").stageCode("XX")
                    .name("Test").element(null).conclusions(List.of()).build();
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));

            List<ActivityNodeResponse> result = service.listActivities(VERSION_ID);

            assertEquals("CS-SEL", result.getFirst().getActivityCode());
        }
    }

    // ---------------------------------------------------------------
    // listApiActivities
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("listApiActivities")
    class ListApiActivitiesTests {

        @Test
        @DisplayName("returns only connector activities, excludes plain tasks")
        void returnsOnlyConnectorActivities() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);

            ProcessActivityEntity plain   = activityWithoutElement("CS-SEL");
            ProcessActivityEntity apiAct  = activityWithElement("SC-PMT_AUTH", "SC");

            when(activityRepo.findByVersion_VersionId(VERSION_ID))
                    .thenReturn(List.of(plain, apiAct));
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(
                            prop("connector.id",           "http-connector"),
                            prop("connector.input.url",    "https://api.example.com"),
                            prop("connector.input.method", "POST")));

            List<ActivityNodeResponse> result = service.listApiActivities(VERSION_ID);

            assertEquals(1, result.size());
            assertEquals("SC-PMT_AUTH", result.getFirst().getAbbreviation());
            assertNotNull(result.getFirst().getApiHandler());
        }

        @Test
        @DisplayName("returns empty list when no connector activities exist")
        void returnsEmpty_whenNoConnectorActivities() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity plain = activityWithoutElement("CS-SEL");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(plain));

            List<ActivityNodeResponse> result = service.listApiActivities(VERSION_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("connector.id blank is treated as plain task")
        void blankConnectorId_treatedAsPlainTask() {
            when(versionRepo.existsById(VERSION_ID)).thenReturn(true);
            ProcessActivityEntity activity = activityWithElement("SC-PMT_AUTH", "SC");
            when(activityRepo.findByVersion_VersionId(VERSION_ID)).thenReturn(List.of(activity));
            when(extPropRepo.findByOwnerTypeAndOwnerId("ELEMENT", ELEMENT_ID))
                    .thenReturn(List.of(prop("connector.id", "   ")));

            List<ActivityNodeResponse> result = service.listApiActivities(VERSION_ID);

            assertTrue(result.isEmpty());
        }
    }
}