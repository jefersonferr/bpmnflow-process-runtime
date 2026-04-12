package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BpmnProcessRepository extends JpaRepository<BpmnProcessEntity, Long> {
    Optional<BpmnProcessEntity> findByProcessKey(String processKey);
    List<BpmnProcessEntity> findAllByOrderByCreatedAtDesc();
}
