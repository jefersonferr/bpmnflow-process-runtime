package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpmnElementRepository extends JpaRepository<BpmnElementEntity, Long> {

    List<BpmnElementEntity> findByVersion_VersionId(Long versionId);

    Optional<BpmnElementEntity> findByVersion_VersionIdAndBpmnId(Long versionId, String bpmnId);

    List<BpmnElementEntity> findByVersion_VersionIdAndElementType(Long versionId, String elementType);
}
