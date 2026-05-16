package org.bpmnflow.runtime.dto;

import java.time.LocalDateTime;

/**
 * Spring Data projection interface for workflow instance listing.
  * Used by WfProcessInstanceRepository projection queries as the return type.
 * Spring Data generates a proxy implementation at runtime — no constructor
 * needed, no Lombok interaction, no Hibernate constructor resolution issues.
 * Each getter maps to the corresponding alias in the JPQL SELECT clause.
 */
public interface WorkflowSummaryProjection {
    Long getInstanceId();
    String getExternalId();
    String getInstanceStatus();
    String getProcessStatus();
    Long getVersionId();
    Integer getVersionNumber();
    String getProcessKey();
    String getProcessName();
    String getCurrentActivityAbbreviation();
    String getCurrentActivityName();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    LocalDateTime getCompletedAt();
}