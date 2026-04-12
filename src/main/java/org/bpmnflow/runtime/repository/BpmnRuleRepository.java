package org.bpmnflow.runtime.repository;
import org.bpmnflow.runtime.model.entity.ProcessRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BpmnRuleRepository extends JpaRepository<ProcessRuleEntity, Long> {
    List<ProcessRuleEntity> findByVersion_VersionIdAndRuleType(Long versionId, String ruleType);
    List<ProcessRuleEntity> findByVersion_VersionIdAndSourceActivity_ActivityId(Long versionId, Long sourceActivityId);
    List<ProcessRuleEntity> findByVersion_VersionIdAndSourceActivity_ActivityIdAndConclusionCode(Long versionId, Long sourceActivityId, String conclusionCode);
}
