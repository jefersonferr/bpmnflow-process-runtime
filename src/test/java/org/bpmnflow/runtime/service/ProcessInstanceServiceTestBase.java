package org.bpmnflow.runtime.service;

import org.bpmnflow.runtime.model.entity.*;
import org.bpmnflow.runtime.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for ProcessInstanceService unit tests.
 *
 * Provides shared mocks, fixtures and helper methods for all test classes.
 * Each test file extends this class and focuses on a single responsibility.
 *
 * Activity flow (pizza-delivery.bpmn):
 *   CS-SEL → CS-ORD → SC-RCV → [SC-CLM →] CH-BAK → DL-DLV → [DL-PMT → DL-RCP →] CS-EAT → END
 *
 * Conclusions:
 *   SC-RCV / SC-CLM : ORDER_CONFIRMED, NEEDS_ATTENTION
 *   CH-BAK          : READY_FOR_DELIVERY, NOT_READY
 *   DL-DLV          : COLLECT_PAYMENT, ONLY_DELIVERY
 */
@ExtendWith(MockitoExtension.class)
abstract class ProcessInstanceServiceTestBase {

    // ---------------------------------------------------------------
    // Mocks
    // ---------------------------------------------------------------

    @Mock BpmnProcessVersionRepository versionRepo;
    @Mock BpmnRuleRepository           ruleRepo;
    @Mock BpmnActivityRepository       activityRepo;
    @Mock WfProcessInstanceRepository  instanceRepo;
    @Mock WfInstanceActivityRepository instActivityRepo;
    @Mock WfInstanceVariableRepository variableRepo;

    @InjectMocks
    ProcessInstanceService service;

    // ---------------------------------------------------------------
    // Shared constants
    // ---------------------------------------------------------------

    static final Long VERSION_ID  = 1L;
    static final Long INSTANCE_ID = 10L;

    // ---------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------

    BpmnProcessEntity        process;
    BpmnProcessVersionEntity version;

    ProcessActivityEntity actSEL, actORD, actRCV, actCLM,
                          actBAK, actDLV, actPMT, actRCP, actEAT;

    @BeforeEach
    void setUpBase() {
        process = BpmnProcessEntity.builder()
                .processId(1L).processKey("PIZZA_DELIVERY").name("Pizza Delivery").build();

        version = BpmnProcessVersionEntity.builder()
                .versionId(VERSION_ID).process(process).versionNumber(1)
                .status("ACTIVE").valid(true).bpmnXml("<bpmn/>").build();

        actSEL = activity(1L, "CS-SEL", "Select Pizza");
        actORD = activity(2L, "CS-ORD", "Order Pizza");
        actRCV = activity(3L, "SC-RCV", "Receive Order",
                conclusion("ORDER_CONFIRMED", "Order confirmed"),
                conclusion("NEEDS_ATTENTION", "Needs attention"));
        actCLM = activity(4L, "SC-CLM", "Call to Customer",
                conclusion("ORDER_CONFIRMED", "Order confirmed"),
                conclusion("NEEDS_ATTENTION", "Needs attention"));
        actBAK = activity(5L, "CH-BAK", "Bake Pizza",
                conclusion("READY_FOR_DELIVERY", "Ready for delivery"),
                conclusion("NOT_READY", "Not ready yet"));
        actDLV = activity(6L, "DL-DLV", "Deliver Pizza",
                conclusion("COLLECT_PAYMENT", "Collect payment"),
                conclusion("ONLY_DELIVERY", "Only delivery"));
        actPMT = activity(7L, "DL-PMT", "Receive Payment");
        actRCP = activity(8L, "DL-RCP", "Issue Receipt");
        actEAT = activity(9L, "CS-EAT", "Eat Pizza");
    }

    // ---------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------

    ProcessActivityEntity activity(Long id, String abbreviation, String name,
                                   ProcessConclusionEntity... conclusions) {
        return ProcessActivityEntity.builder()
                .activityId(id)
                .version(version)
                .abbreviation(abbreviation)
                .name(name)
                .stageCode(abbreviation.split("-")[0])
                .conclusions(new ArrayList<>(List.of(conclusions)))
                .build();
    }

    ProcessConclusionEntity conclusion(String code, String name) {
        return ProcessConclusionEntity.builder().code(code).name(name).build();
    }

    ProcessRuleEntity rule(String type, ProcessActivityEntity source,
                           ProcessActivityEntity target, String processStatus) {
        return ProcessRuleEntity.builder()
                .ruleId((long) (Math.random() * 1000))
                .version(version)
                .ruleType(type)
                .sourceActivity(source)
                .targetActivity(target)
                .sourceAbbreviation(source != null ? source.getAbbreviation() : null)
                .targetAbbreviation(target != null ? target.getAbbreviation() : null)
                .processStatus(processStatus)
                .build();
    }

    ProcessRuleEntity rule(String type, ProcessActivityEntity source,
                           ProcessActivityEntity target, String conclusionCode,
                           String processStatus) {
        ProcessRuleEntity r = rule(type, source, target, processStatus);
        r.setConclusionCode(conclusionCode);
        return r;
    }

    WfProcessInstanceEntity instance(Long id, String status, String processStatus) {
        return WfProcessInstanceEntity.builder()
                .instanceId(id)
                .version(version)
                .status(status)
                .processStatus(processStatus)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .instanceActivities(new ArrayList<>())
                .variables(new ArrayList<>())
                .build();
    }

    WfInstanceActivityEntity step(WfProcessInstanceEntity instance,
                                  ProcessActivityEntity activity,
                                  int stepNumber, String status) {
        return WfInstanceActivityEntity.builder()
                .instActivityId((long) stepNumber)
                .instance(instance)
                .activity(activity)
                .stepNumber(stepNumber)
                .status(status)
                .startedAt(LocalDateTime.now())
                .build();
    }

    WfProcessInstanceEntity withId(WfProcessInstanceEntity instance, Long id) {
        instance.setInstanceId(id);
        return instance;
    }
}
