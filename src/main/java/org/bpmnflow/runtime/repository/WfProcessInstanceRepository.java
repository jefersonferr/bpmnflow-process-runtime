package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.InstanceStatus;
import org.bpmnflow.runtime.model.entity.WfProcessInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WfProcessInstanceRepository extends JpaRepository<WfProcessInstanceEntity, Long> {

    @Query("""
            SELECT DISTINCT i FROM WfProcessInstanceEntity i
            JOIN FETCH i.version v
            JOIN FETCH v.process
            LEFT JOIN FETCH i.instanceActivities a
            LEFT JOIN FETCH a.activity
            WHERE i.status = :status
            ORDER BY i.createdAt DESC
            """)
    List<WfProcessInstanceEntity> findByStatusOrderByCreatedAtDesc(@Param("status") InstanceStatus status);

    @Query("""
            SELECT DISTINCT i FROM WfProcessInstanceEntity i
            JOIN FETCH i.version v
            JOIN FETCH v.process
            LEFT JOIN FETCH i.instanceActivities a
            LEFT JOIN FETCH a.activity
            ORDER BY i.createdAt DESC
            """)
    List<WfProcessInstanceEntity> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT DISTINCT i FROM WfProcessInstanceEntity i
            JOIN FETCH i.version v
            JOIN FETCH v.process p
            LEFT JOIN FETCH i.instanceActivities a
            LEFT JOIN FETCH a.activity
            WHERE p.processKey = :processKey
            ORDER BY i.createdAt DESC
            """)
    List<WfProcessInstanceEntity> findByProcessKeyOrderByCreatedAtDesc(@Param("processKey") String processKey);

    @Query("""
            SELECT DISTINCT i FROM WfProcessInstanceEntity i
            JOIN FETCH i.version v
            JOIN FETCH v.process p
            LEFT JOIN FETCH i.instanceActivities a
            LEFT JOIN FETCH a.activity
            WHERE p.processKey = :processKey AND i.status = :status
            ORDER BY i.createdAt DESC
            """)
    List<WfProcessInstanceEntity> findByProcessKeyAndStatusOrderByCreatedAtDesc(
            @Param("processKey") String processKey,
            @Param("status") InstanceStatus status);
}