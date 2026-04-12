package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnProcessVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessVersionRepository extends JpaRepository<BpmnProcessVersionEntity, Long> {

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM BpmnProcessVersionEntity v WHERE v.process.processId = :processId")
    int findMaxVersionNumber(Long processId);

    Optional<BpmnProcessVersionEntity> findByProcess_ProcessIdAndStatus(Long processId, String status);

    List<BpmnProcessVersionEntity> findByProcess_ProcessIdOrderByVersionNumberDesc(Long processId);
}
