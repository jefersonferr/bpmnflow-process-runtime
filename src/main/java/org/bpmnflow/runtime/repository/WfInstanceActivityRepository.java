package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.bpmnflow.runtime.model.entity.WfInstanceActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WfInstanceActivityRepository extends JpaRepository<WfInstanceActivityEntity, Long> {
    Optional<WfInstanceActivityEntity> findByInstance_InstanceIdAndStatus(Long instanceId, ActivityStepStatus status);
}
