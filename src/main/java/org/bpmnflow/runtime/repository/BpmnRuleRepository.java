package org.bpmnflow.runtime.repository;

import org.bpmnflow.model.RuleType;
import org.bpmnflow.runtime.model.entity.ProcessRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BpmnRuleRepository extends JpaRepository<ProcessRuleEntity, Long> {

    List<ProcessRuleEntity> findByVersion_VersionIdAndRuleType(Long versionId, RuleType ruleType);

    List<ProcessRuleEntity> findByVersion_VersionIdAndSourceActivity_ActivityId(Long versionId, Long sourceActivityId);

    @Query("""
            SELECT r FROM ProcessRuleEntity r
            WHERE r.version.versionId = :versionId
            AND r.sourceActivity.activityId = :activityId
            AND (r.conclusionCode = :conclusionCode OR r.conclusionCode IS NULL)
            """)
    List<ProcessRuleEntity> findMatchingRulesWithConclusion(
            @Param("versionId") Long versionId,
            @Param("activityId") Long activityId,
            @Param("conclusionCode") String conclusionCode);

    @Query("""
            SELECT r FROM ProcessRuleEntity r
            WHERE r.version.versionId = :versionId
            AND r.sourceActivity.activityId = :activityId
            AND r.conclusionCode IS NULL
            """)
    List<ProcessRuleEntity> findMatchingRulesWithoutConclusion(
            @Param("versionId") Long versionId,
            @Param("activityId") Long activityId);
}