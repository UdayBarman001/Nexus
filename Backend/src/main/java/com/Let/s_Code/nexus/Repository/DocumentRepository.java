package com.Let.s_Code.nexus.Repository;

import com.Let.s_Code.nexus.Entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // @EntityGraph forces a single SQL JOIN to pull `uploader` eagerly, in one query.
    // Without it, doc.getUploader().getEmail() in DocumentResponse would either throw
    // LazyInitializationException (session already closed — open-in-view is off) or, if it
    // didn't throw, would fire one extra SELECT per row (classic N+1). Neither is acceptable
    // for a listing endpoint.
    @EntityGraph(attributePaths = "uploader")
    Page<Document> findByUploaderId(Long uploaderId, Pageable pageable);

    @EntityGraph(attributePaths = "uploader")
    Page<Document> findByUploaderIdAndProcessingStatus(Long uploaderId, String processingStatus, Pageable pageable);

    @EntityGraph(attributePaths = "uploader")
    Page<Document> findByProcessingStatus(String processingStatus, Pageable pageable);

    @EntityGraph(attributePaths = "uploader")
    Optional<Document> findByIdAndUploaderId(UUID id, Long uploaderId);

    // Overriding these two default JpaRepository methods with @EntityGraph applies the same
    // eager-fetch fix to the admin "list everything" path and to single-document lookups.
    @Override
    @EntityGraph(attributePaths = "uploader")
    Page<Document> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "uploader")
    Optional<Document> findById(UUID id);

    long countByUploaderId(Long uploaderId);

    @org.springframework.data.jpa.repository.Query("SELECT coalesce(sum(d.chunkCount), 0) FROM Document d WHERE d.uploader.id = :userId")
    long sumChunkCountByUploaderId(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT coalesce(sum(d.chunkCount), 0) FROM Document d")
    long sumTotalChunkCount();

    @org.springframework.data.jpa.repository.Query("SELECT d.processingStatus, count(d) FROM Document d WHERE d.uploader.id = :userId GROUP BY d.processingStatus")
    java.util.List<Object[]> countByProcessingStatusForUploader(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT d.processingStatus, count(d) FROM Document d GROUP BY d.processingStatus")
    java.util.List<Object[]> countByProcessingStatusAll();
}
