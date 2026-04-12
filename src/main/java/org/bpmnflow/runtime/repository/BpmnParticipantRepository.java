package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpmnParticipantRepository extends JpaRepository<BpmnParticipantEntity, Long> {

    List<BpmnParticipantEntity> findByVersion_VersionId(Long versionId);

    Optional<BpmnParticipantEntity> findByVersion_VersionIdAndBpmnId(Long versionId, String bpmnId);
}
