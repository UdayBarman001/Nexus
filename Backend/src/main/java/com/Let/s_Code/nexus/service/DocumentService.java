package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.DocumentRepository;
import com.Let.s_Code.nexus.exception.AccessDeniedApiException;
import com.Let.s_Code.nexus.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentIngestionService documentIngestionService;
    private final VectorizationService vectorizationService;
    private final FileStorageService fileStorageService;

    /** Kicks off upload + background embedding. Returns immediately with status PENDING. */
    public Document upload(MultipartFile file, User uploader) {
        Document saved = documentIngestionService.uploadDocument(file, uploader);
        documentIngestionService.processAsync(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Document> listForUser(User user, Document.DocumentStatus status, Pageable pageable) {
        Pageable bounded = boundPageSize(pageable);
        if (user.getRole() == Role.ADMIN) {
            if (status != null) {
                return documentRepository.findByProcessingStatus(status.name(), bounded);
            }
            return documentRepository.findAll(bounded);
        }
        if (status != null) {
            return documentRepository.findByUploaderIdAndProcessingStatus(user.getId(), status.name(), bounded);
        }
        return documentRepository.findByUploaderId(user.getId(), bounded);
    }

    @Transactional(readOnly = true)
    public Page<Document> listForUser(User user, Pageable pageable) {
        return listForUser(user, null, pageable);
    }

    @Transactional(readOnly = true)
    public Document getForUser(UUID id, User user) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        assertOwnerOrAdmin(doc, user);
        return doc;
    }

    public byte[] download(UUID id, User user) {
        Document doc = getForUser(id, user);
        return fileStorageService.downloadFile(doc.getS3Url());
    }

    /** Use when the caller already fetched (and ownership-checked) the Document, to avoid a duplicate lookup. */
    public byte[] downloadBytes(Document doc) {
        return fileStorageService.downloadFile(doc.getS3Url());
    }

    @Transactional
    public void delete(UUID id, User user) {
        Document doc = getForUser(id, user);
        vectorizationService.deleteByDocumentId(doc.getId().toString());
        fileStorageService.deleteFile(doc.getS3Url());
        documentRepository.delete(doc);
        log.info("Deleted document {} (requested by {})", id, user.getEmail());
    }

    private void assertOwnerOrAdmin(Document doc, User user) {
        boolean isOwner = doc.getUploader() != null && doc.getUploader().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedApiException("You do not have access to this document.");
        }
    }

    @Transactional(readOnly = true)
    public com.Let.s_Code.nexus.dto.DocumentStatsResponse getStatsForUser(User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        long totalDocs = isAdmin ? documentRepository.count() : documentRepository.countByUploaderId(user.getId());
        long totalChunks = isAdmin ? documentRepository.sumTotalChunkCount() : documentRepository.sumChunkCountByUploaderId(user.getId());
        java.util.List<Object[]> statusCounts = isAdmin
                ? documentRepository.countByProcessingStatusAll()
                : documentRepository.countByProcessingStatusForUploader(user.getId());

        long embedded = 0;
        long processing = 0;
        long pending = 0;
        long failed = 0;

        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            if (Document.DocumentStatus.EMBEDDED.name().equalsIgnoreCase(status)) {
                embedded = count;
            } else if (Document.DocumentStatus.PROCESSING.name().equalsIgnoreCase(status)) {
                processing = count;
            } else if (Document.DocumentStatus.PENDING.name().equalsIgnoreCase(status)) {
                pending = count;
            } else if (Document.DocumentStatus.FAILED.name().equalsIgnoreCase(status)) {
                failed = count;
            }
        }

        return com.Let.s_Code.nexus.dto.DocumentStatsResponse.builder()
                .totalDocuments(totalDocs)
                .embeddedDocuments(embedded)
                .processingDocuments(processing)
                .pendingDocuments(pending)
                .failedDocuments(failed)
                .totalChunks(totalChunks)
                .build();
    }

    /** Caps page size so a caller can't force an unbounded/huge query (e.g. ?size=999999). */
    private Pageable boundPageSize(Pageable pageable) {
        final int MAX_PAGE_SIZE = 100;
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
