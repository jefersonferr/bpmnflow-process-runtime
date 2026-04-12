package org.bpmnflow.runtime.repository;
import org.bpmnflow.runtime.model.entity.ProcessActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface BpmnActivityRepository extends JpaRepository<ProcessActivityEntity, Long> {
    List<ProcessActivityEntity> findByVersion_VersionId(Long versionId);
    Optional<ProcessActivityEntity> findByVersion_VersionIdAndAbbreviation(Long versionId, String abbreviation);
}
