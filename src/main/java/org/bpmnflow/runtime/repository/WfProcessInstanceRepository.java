package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.dto.WorkflowSummaryProjection;
import org.bpmnflow.runtime.model.entity.InstanceStatus;
import org.bpmnflow.runtime.model.entity.WfProcessInstanceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WfProcessInstanceRepository extends JpaRepository<WfProcessInstanceEntity, Long> {

    // -----------------------------------------------------------------------
    // Projection queries for listing — single query, no JOIN FETCH, no graph load
    //
    // Returns WorkflowSummaryProjection (Spring Data interface proxy).
    // The LEFT JOIN on instanceActivities filters by status = 'ACTIVE', bringing
    // at most one row per instance — no Cartesian products, no in-memory dedup.
    //
    // Aliases in the SELECT clause must match the getter names in the projection
    // interface (e.g. instanceId -> getInstanceId(), processKey -> getProcessKey()).
    // -----------------------------------------------------------------------

    @Query("""
            SELECT
                i.instanceId                    AS instanceId,
                i.externalId                    AS externalId,
                i.status                        AS instanceStatus,
                i.processStatus                 AS processStatus,
                v.versionId                     AS versionId,
                v.versionNumber                 AS versionNumber,
                p.processKey                    AS processKey,
                p.name                          AS processName,
                act.abbreviation                AS currentActivityAbbreviation,
                act.name                        AS currentActivityName,
                i.createdAt                     AS createdAt,
                i.updatedAt                     AS updatedAt,
                i.completedAt                   AS completedAt
            FROM WfProcessInstanceEntity i
            JOIN i.version v
            JOIN v.process p
            LEFT JOIN i.instanceActivities a ON a.status = 'ACTIVE'
            LEFT JOIN a.activity act
            WHERE i.status = :status
            ORDER BY i.createdAt DESC
            """)
    List<WorkflowSummaryProjection> findSummaryByStatus(
            @Param("status") InstanceStatus status,
            Pageable pageable);

    @Query("""
            SELECT
                i.instanceId                    AS instanceId,
                i.externalId                    AS externalId,
                i.status                        AS instanceStatus,
                i.processStatus                 AS processStatus,
                v.versionId                     AS versionId,
                v.versionNumber                 AS versionNumber,
                p.processKey                    AS processKey,
                p.name                          AS processName,
                act.abbreviation                AS currentActivityAbbreviation,
                act.name                        AS currentActivityName,
                i.createdAt                     AS createdAt,
                i.updatedAt                     AS updatedAt,
                i.completedAt                   AS completedAt
            FROM WfProcessInstanceEntity i
            JOIN i.version v
            JOIN v.process p
            LEFT JOIN i.instanceActivities a ON a.status = 'ACTIVE'
            LEFT JOIN a.activity act
            ORDER BY i.createdAt DESC
            """)
    List<WorkflowSummaryProjection> findAllSummary(Pageable pageable);

    @Query("""
            SELECT
                i.instanceId                    AS instanceId,
                i.externalId                    AS externalId,
                i.status                        AS instanceStatus,
                i.processStatus                 AS processStatus,
                v.versionId                     AS versionId,
                v.versionNumber                 AS versionNumber,
                p.processKey                    AS processKey,
                p.name                          AS processName,
                act.abbreviation                AS currentActivityAbbreviation,
                act.name                        AS currentActivityName,
                i.createdAt                     AS createdAt,
                i.updatedAt                     AS updatedAt,
                i.completedAt                   AS completedAt
            FROM WfProcessInstanceEntity i
            JOIN i.version v
            JOIN v.process p
            LEFT JOIN i.instanceActivities a ON a.status = 'ACTIVE'
            LEFT JOIN a.activity act
            WHERE p.processKey = :processKey
            ORDER BY i.createdAt DESC
            """)
    List<WorkflowSummaryProjection> findSummaryByProcessKey(
            @Param("processKey") String processKey,
            Pageable pageable);

    @Query("""
            SELECT
                i.instanceId                    AS instanceId,
                i.externalId                    AS externalId,
                i.status                        AS instanceStatus,
                i.processStatus                 AS processStatus,
                v.versionId                     AS versionId,
                v.versionNumber                 AS versionNumber,
                p.processKey                    AS processKey,
                p.name                          AS processName,
                act.abbreviation                AS currentActivityAbbreviation,
                act.name                        AS currentActivityName,
                i.createdAt                     AS createdAt,
                i.updatedAt                     AS updatedAt,
                i.completedAt                   AS completedAt
            FROM WfProcessInstanceEntity i
            JOIN i.version v
            JOIN v.process p
            LEFT JOIN i.instanceActivities a ON a.status = 'ACTIVE'
            LEFT JOIN a.activity act
            WHERE p.processKey = :processKey AND i.status = :status
            ORDER BY i.createdAt DESC
            """)
    List<WorkflowSummaryProjection> findSummaryByProcessKeyAndStatus(
            @Param("processKey") String processKey,
            @Param("status") InstanceStatus status,
            Pageable pageable);

    // -----------------------------------------------------------------------
    // Legacy non-paginated queries — kept for backward compatibility
    // -----------------------------------------------------------------------

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