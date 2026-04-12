package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.WfInstanceVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WfInstanceVariableRepository
        extends JpaRepository<WfInstanceVariableEntity, Long> {

    List<WfInstanceVariableEntity> findByInstance_InstanceId(Long instanceId);

    Optional<WfInstanceVariableEntity> findByInstance_InstanceIdAndVariableKey(
            Long instanceId, String variableKey);
}