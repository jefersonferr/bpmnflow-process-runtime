package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.BpmnExtensionPropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BpmnExtensionPropertyRepository extends JpaRepository<BpmnExtensionPropertyEntity, Long> {

    List<BpmnExtensionPropertyEntity> findByVersion_VersionId(Long versionId);

    List<BpmnExtensionPropertyEntity> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);

    Optional<BpmnExtensionPropertyEntity> findByOwnerTypeAndOwnerIdAndPropertyName(
            String ownerType, Long ownerId, String propertyName);
}
