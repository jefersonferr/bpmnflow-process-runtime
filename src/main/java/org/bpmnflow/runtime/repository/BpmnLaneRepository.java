package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnLaneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpmnLaneRepository extends JpaRepository<BpmnLaneEntity, Long> {

    List<BpmnLaneEntity> findByVersion_VersionId(Long versionId);

    List<BpmnLaneEntity> findByParticipant_ParticipantId(Long participantId);

    Optional<BpmnLaneEntity> findByVersion_VersionIdAndBpmnId(Long versionId, String bpmnId);
}
