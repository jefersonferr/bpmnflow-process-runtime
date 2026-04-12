package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.WfProcessInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WfProcessInstanceRepository extends JpaRepository<WfProcessInstanceEntity, Long> {

    List<WfProcessInstanceEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<WfProcessInstanceEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT i FROM WfProcessInstanceEntity i WHERE i.version.process.processKey = :processKey ORDER BY i.createdAt DESC")
    List<WfProcessInstanceEntity> findByProcessKeyOrderByCreatedAtDesc(@Param("processKey") String processKey);

    @Query("SELECT i FROM WfProcessInstanceEntity i WHERE i.version.process.processKey = :processKey AND i.status = :status ORDER BY i.createdAt DESC")
    List<WfProcessInstanceEntity> findByProcessKeyAndStatusOrderByCreatedAtDesc(@Param("processKey") String processKey, @Param("status") String status);
}
