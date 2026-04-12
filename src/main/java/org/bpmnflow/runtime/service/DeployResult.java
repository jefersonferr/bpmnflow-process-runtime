package org.bpmnflow.runtime.service;

import lombok.Builder;
import lombok.Getter;
import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;

import java.util.List;

/**
 * Result returned by {@link BpmnDeployService} after a successful deploy.
 *
 * <p>Carries the persisted version entity alongside the actual row counts
 * from each repository — avoiding reliance on lazy collections that remain
 * empty after a direct {@code save()} call.</p>
 */
@Getter
@Builder
public class DeployResult {

    private final BpmnProcessVersionEntity version;

    private final int participantCount;
    private final int laneCount;
    private final int elementCount;
    private final int sequenceFlowCount;
    private final int stageCount;
    private final int activityCount;
    private final int ruleCount;
    private final int inconsistencyCount;
    private final List<String> inconsistencies;
}
