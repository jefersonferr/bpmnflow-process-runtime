package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnSequenceFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BpmnSequenceFlowRepository extends JpaRepository<BpmnSequenceFlowEntity, Long> {

    List<BpmnSequenceFlowEntity> findByVersion_VersionId(Long versionId);
}
