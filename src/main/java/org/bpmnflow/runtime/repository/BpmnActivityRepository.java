package org.bpmnflow.runtime.repository;

import org.bpmnflow.runtime.model.entity.ProcessActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BpmnActivityRepository extends JpaRepository<ProcessActivityEntity, Long> {

    List<ProcessActivityEntity> findByVersion_VersionId(Long versionId);

    Optional<ProcessActivityEntity> findByVersion_VersionIdAndAbbreviation(Long versionId, String abbreviation);

    /**
     * Loads the activity with element AND conclusions eagerly via JOIN FETCH.
     * Replaces findById() wherever:
     *   - act.getElement().getBpmnId() is accessed (element proxy)
     *   - act.getConclusions().isEmpty() is accessed (conclusions collection)
     * outside a Hibernate session, to avoid LazyInitializationException.
     * Two separate LEFT JOIN FETCH are needed because Hibernate does not
     * support fetching multiple bags in a single query — splitting into
     * element (ManyToOne) + conclusions (OneToMany) is safe because
     * element is a single object, not a collection.
     */
    @Query("""
        SELECT DISTINCT a FROM ProcessActivityEntity a
        LEFT JOIN FETCH a.element
        LEFT JOIN FETCH a.conclusions
        WHERE a.activityId = :id
        """)
    Optional<ProcessActivityEntity> findByIdWithElement(@Param("id") Long id);
}