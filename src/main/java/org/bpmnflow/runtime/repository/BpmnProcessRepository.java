package org.bpmnflow.runtime.repository;

import jakarta.persistence.LockModeType;
import org.bpmnflow.runtime.model.entity.BpmnProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessRepository extends JpaRepository<BpmnProcessEntity, Long> {
    Optional<BpmnProcessEntity> findByProcessKey(String processKey);
    List<BpmnProcessEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Locks the process row with PESSIMISTIC_WRITE before version number calculation,
     * preventing concurrent deploys of the same processKey from computing the same
     * nextVersion and violating the uk_proc_ver_num unique constraint.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM BpmnProcessEntity p WHERE p.processKey = :processKey")
    Optional<BpmnProcessEntity> findByProcessKeyForUpdate(String processKey);
}