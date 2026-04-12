package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BpmnConfigRepository extends JpaRepository<BpmnConfigEntity, Long> {
    Optional<BpmnConfigEntity> findByConfigHash(String configHash);
}
