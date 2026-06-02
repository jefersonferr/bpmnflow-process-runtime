package org.bpmnflow.runtime.service.deploy;

import org.bpmnflow.runtime.model.entity.BpmnExtensionPropertyEntity;
import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;
import org.bpmnflow.runtime.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StructuralPersistor persistConnectorProperties} —
 * the branch added to support {@code <camunda:connector>} extraction.
 */
@DisplayName("StructuralPersistor – connector property persistence")
@ExtendWith(MockitoExtension.class)
class StructuralPersistorConnectorTest {

    @Mock BpmnParticipantRepository       participantRepo;
    @Mock BpmnLaneRepository              laneRepo;
    @Mock BpmnElementRepository           elementRepo;
    @Mock BpmnSequenceFlowRepository      sequenceFlowRepo;
    @Mock BpmnExtensionPropertyRepository extPropRepo;

    StructuralPersistor persistor;

    private static final String NS =
            "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
                    "xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"";

    @BeforeEach
    void setUp() {
        persistor = new StructuralPersistor(
                participantRepo, laneRepo, elementRepo,
                sequenceFlowRepo, extPropRepo);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Parse with namespace awareness — required for getElementsByTagNameNS. */
    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private BpmnProcessVersionEntity version() {
        BpmnProcessVersionEntity v = new BpmnProcessVersionEntity();
        v.setVersionId(1L);
        return v;
    }

    /** Stub element save to return an entity with a predictable elementId. */
    private void stubElementSave(Long elementId) {
        when(elementRepo.save(any())).thenAnswer(inv -> {
            var entity = inv.<org.bpmnflow.runtime.model.entity.BpmnElementEntity>getArgument(0);
            entity.setElementId(elementId);
            return entity;
        });
    }

    /** Stub extPropRepo so idempotency check always says "not present". */
    private void stubPropNotPresent() {
        when(extPropRepo.findByOwnerTypeAndOwnerIdAndPropertyName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(extPropRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------
    // Branch: no <camunda:connector> → no connector.* props saved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("task without camunda:connector saves no connector.* props")
    void noConnector_savesNoConnectorProps() throws Exception {
        String bpmn = """
            <bpmn:definitions %s>
              <bpmn:process id="P1">
                <bpmn:task id="T1" name="Plain Task">
                  <bpmn:extensionElements>
                    <camunda:properties>
                      <camunda:property name="stage" value="CS"/>
                    </camunda:properties>
                  </bpmn:extensionElements>
                </bpmn:task>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(NS);

        stubElementSave(10L);
        stubPropNotPresent();
        when(laneRepo.findByVersion_VersionId(any())).thenReturn(List.of());

        persistor.persist(version(), parse(bpmn));

        ArgumentCaptor<BpmnExtensionPropertyEntity> cap =
                ArgumentCaptor.forClass(BpmnExtensionPropertyEntity.class);
        verify(extPropRepo, atLeastOnce()).save(cap.capture());

        boolean anyConnector = cap.getAllValues().stream()
                .anyMatch(p -> p.getPropertyName().startsWith("connector."));
        assertFalse(anyConnector, "No connector.* props should be saved for a plain task");
    }

    // ---------------------------------------------------------------
    // Branch: <camunda:connector> without <camunda:connectorId> → skip
    // ---------------------------------------------------------------

    @Test
    @DisplayName("connector without connectorId saves no connector.* props")
    void connectorWithoutConnectorId_savesNothing() throws Exception {
        String bpmn = """
            <bpmn:definitions %s>
              <bpmn:process id="P1">
                <bpmn:serviceTask id="ST1" name="API Task">
                  <bpmn:extensionElements>
                    <camunda:properties>
                      <camunda:property name="stage" value="SC"/>
                    </camunda:properties>
                    <camunda:connector>
                      <camunda:inputOutput>
                        <camunda:inputParameter name="url">https://example.com</camunda:inputParameter>
                      </camunda:inputOutput>
                      <!-- intentionally missing <camunda:connectorId> -->
                    </camunda:connector>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(NS);

        stubElementSave(10L);
        stubPropNotPresent();
        when(laneRepo.findByVersion_VersionId(any())).thenReturn(List.of());

        persistor.persist(version(), parse(bpmn));

        ArgumentCaptor<BpmnExtensionPropertyEntity> cap =
                ArgumentCaptor.forClass(BpmnExtensionPropertyEntity.class);
        // At least the camunda:property "stage" is saved
        verify(extPropRepo, atLeastOnce()).save(cap.capture());

        boolean anyConnector = cap.getAllValues().stream()
                .anyMatch(p -> p.getPropertyName().startsWith("connector."));
        assertFalse(anyConnector, "No connector.* props should be saved without connectorId");
    }

    // ---------------------------------------------------------------
    // Branch: full connector → all connector.* props saved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("full connector saves connector.id, connector.input.*, connector.output.*")
    void fullConnector_savesAllConnectorProps() throws Exception {
        String bpmn = """
            <bpmn:definitions %s>
              <bpmn:process id="P1">
                <bpmn:serviceTask id="ST1" name="API Task">
                  <bpmn:extensionElements>
                    <camunda:connector>
                      <camunda:inputOutput>
                        <camunda:inputParameter name="url">https://api.example.com</camunda:inputParameter>
                        <camunda:inputParameter name="method">POST</camunda:inputParameter>
                        <camunda:inputParameter name="payload">{"key":"value"}</camunda:inputParameter>
                        <camunda:outputParameter name="txn_id">$.id</camunda:outputParameter>
                        <camunda:outputParameter name="status">$.status</camunda:outputParameter>
                      </camunda:inputOutput>
                      <camunda:connectorId>http-connector</camunda:connectorId>
                    </camunda:connector>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(NS);

        stubElementSave(10L);
        stubPropNotPresent();
        when(laneRepo.findByVersion_VersionId(any())).thenReturn(List.of());

        persistor.persist(version(), parse(bpmn));

        ArgumentCaptor<BpmnExtensionPropertyEntity> cap =
                ArgumentCaptor.forClass(BpmnExtensionPropertyEntity.class);
        verify(extPropRepo, atLeastOnce()).save(cap.capture());

        List<String> names = cap.getAllValues().stream()
                .map(BpmnExtensionPropertyEntity::getPropertyName)
                .toList();

        assertTrue(names.contains("connector.id"),            "connector.id");
        assertTrue(names.contains("connector.input.url"),     "connector.input.url");
        assertTrue(names.contains("connector.input.method"),  "connector.input.method");
        assertTrue(names.contains("connector.input.payload"), "connector.input.payload");
        assertTrue(names.contains("connector.output.txn_id"), "connector.output.txn_id");
        assertTrue(names.contains("connector.output.status"), "connector.output.status");

        String endpoint = cap.getAllValues().stream()
                .filter(p -> "connector.input.url".equals(p.getPropertyName()))
                .map(BpmnExtensionPropertyEntity::getPropertyValue)
                .findFirst().orElse(null);
        assertNotNull(endpoint);
        assertTrue(endpoint.contains("api.example.com"));
    }

    // ---------------------------------------------------------------
    // Branch: prop already exists → save not called for that prop
    // ---------------------------------------------------------------

    @Test
    @DisplayName("existing connector.id prop is skipped (idempotency)")
    void idempotency_existingPropSkipped() throws Exception {
        String bpmn = """
            <bpmn:definitions %s>
              <bpmn:process id="P1">
                <bpmn:serviceTask id="ST1" name="API Task">
                  <bpmn:extensionElements>
                    <camunda:connector>
                      <camunda:inputOutput>
                        <camunda:inputParameter name="url">https://api.example.com</camunda:inputParameter>
                        <camunda:inputParameter name="method">GET</camunda:inputParameter>
                      </camunda:inputOutput>
                      <camunda:connectorId>http-connector</camunda:connectorId>
                    </camunda:connector>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(NS);

        // All props already exist → save should never be called
        when(laneRepo.findByVersion_VersionId(any())).thenReturn(List.of());
        when(elementRepo.save(any())).thenAnswer(inv -> {
            var entity = inv.<org.bpmnflow.runtime.model.entity.BpmnElementEntity>getArgument(0);
            entity.setElementId(10L);
            return entity;
        });
        when(extPropRepo.findByOwnerTypeAndOwnerIdAndPropertyName(any(), any(), any()))
                .thenReturn(Optional.of(BpmnExtensionPropertyEntity.builder().build()));

        persistor.persist(version(), parse(bpmn));

        verify(extPropRepo, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Branch: inputParameter with blank name is skipped
    // ---------------------------------------------------------------

    @Test
    @DisplayName("inputParameter with blank name is skipped, valid ones are saved")
    void blankInputParamName_isSkipped() throws Exception {
        String bpmn = """
            <bpmn:definitions %s>
              <bpmn:process id="P1">
                <bpmn:serviceTask id="ST1" name="API Task">
                  <bpmn:extensionElements>
                    <camunda:connector>
                      <camunda:inputOutput>
                        <camunda:inputParameter name="">should be skipped</camunda:inputParameter>
                        <camunda:inputParameter name="url">https://api.example.com</camunda:inputParameter>
                      </camunda:inputOutput>
                      <camunda:connectorId>http-connector</camunda:connectorId>
                    </camunda:connector>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(NS);

        stubElementSave(10L);
        stubPropNotPresent();
        when(laneRepo.findByVersion_VersionId(any())).thenReturn(List.of());

        persistor.persist(version(), parse(bpmn));

        ArgumentCaptor<BpmnExtensionPropertyEntity> cap =
                ArgumentCaptor.forClass(BpmnExtensionPropertyEntity.class);
        verify(extPropRepo, atLeastOnce()).save(cap.capture());

        // No prop with blank key
        boolean blankSaved = cap.getAllValues().stream()
                .anyMatch(p -> p.getPropertyName().equals("connector.input."));
        assertFalse(blankSaved, "blank-name inputParameter must be skipped");

        // The url param must still be saved
        boolean urlSaved = cap.getAllValues().stream()
                .anyMatch(p -> "connector.input.url".equals(p.getPropertyName()));
        assertTrue(urlSaved, "connector.input.url must be saved");
    }
}