package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.ActivityStepStatus;
import org.bpmnflow.runtime.model.entity.WfInstanceActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WfInstanceActivityRepository extends JpaRepository<WfInstanceActivityEntity, Long> {

    Optional<WfInstanceActivityEntity> findByInstance_InstanceIdAndStatus(
            Long instanceId, ActivityStepStatus status);

    /**
     * Returns the active activity step for each instance in the given ID list.
     * Used to resolve the current activity abbreviation for a page of instances
     * in a single batch query, replacing N per-instance queries and eliminating
     * the N+1 problem in listing scenarios.
     */
    @Query("""
            SELECT a FROM WfInstanceActivityEntity a
            JOIN FETCH a.activity act
            WHERE a.instance.instanceId IN :instanceIds
              AND a.status = org.bpmnflow.runtime.model.entity.ActivityStepStatus.ACTIVE
            """)
    List<WfInstanceActivityEntity> findActiveByInstanceIdIn(
            @Param("instanceIds") List<Long> instanceIds);
}