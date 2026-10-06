package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.DocumentRepository;
import com.Let.s_Code.nexus.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final VectorizationService vectorizationService;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;

    /**
     * Handles the synchronous half of an upload: stores the raw file in MinIO and records
     * its metadata with status PENDING. Returns immediately — the caller gets the document
     * back right away while embedding happens in the background via {@link #processAsync}.
     *
     * Deliberately NOT @Transactional: fileStorageService.uploadFile() is a slow network call
     * to MinIO. Wrapping it in a Spring transaction would hold a pooled DB connection idle for
     * the whole upload — under concurrent load that exhausts the HikariCP pool fast. The single
     * documentRepository.save() below already runs in its own short-lived transaction (Spring
     * Data repository methods are @Transactional internally), so we get atomicity where it
     * actually matters without paying for it during the network call.
     */
    public Document uploadDocument(MultipartFile file, User uploader) {
        log.info("Received file upload request: {}", file.getOriginalFilename());

        String realS3Url;
        try {
            realS3Url = fileStorageService.uploadFile(file);
        } catch (IOException e) {
            log.error("Failed to upload file to storage: {}", file.getOriginalFilename(), e);
            throw new FileStorageException("File storage upload failed for: " + file.getOriginalFilename(), e);
        }

        Document document = Document.builder()
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .s3Url(realS3Url)
                .processingStatus(Document.DocumentStatus.PENDING.name())
                .uploader(uploader)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document metadata saved. ID: {}", saved.getId());
        return saved;
    }

    /**
     * Runs the (potentially slow) text-extraction + embedding pipeline off the request thread.
     * The MultipartFile's underlying temp file is only guaranteed to exist for the duration of
     * the original request, so we read its bytes before handing off, and re-wrap them here.
     *
     * Deliberately NOT @Transactional at the method level, for the same reason as above but with
     * higher stakes: extraction + embedding (a real call to the Ollama embedding model per chunk)
     * can take anywhere from seconds to minutes for a large document. With up to 8 concurrent
     * async workers (see AsyncConfig), wrapping all of that in one long DB transaction could tie
     * up most of a 10-connection pool for minutes at a time. Instead we take three short,
     * independent saves — each documentRepository.save() call gets its own implicit transaction.
     */
    @Async("documentProcessingExecutor")
    public void processAsync(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("Document {} disappeared before async processing could run", documentId);
            return;
        }

        document.setProcessingStatus(Document.DocumentStatus.PROCESSING.name());
        documentRepository.save(document);

        try {
            byte[] fileBytes = fileStorageService.downloadFile(document.getS3Url());
            InMemoryMultipartFile wrapped = new InMemoryMultipartFile(
                    document.getFilename(), document.getContentType(), fileBytes);
            String extractedText = textExtractionService.extractText(wrapped);
            Long uploaderId = document.getUploader() != null ? document.getUploader().getId() : null;
            boolean shared = document.getUploader() != null && document.getUploader().getRole() == com.Let.s_Code.nexus.Entity.Role.ADMIN;
            int chunkCount = vectorizationService.processAndStore(
                    extractedText, document.getId().toString(), document.getFilename(), uploaderId, shared);

            document.setChunkCount(chunkCount);
            document.setProcessingStatus(Document.DocumentStatus.EMBEDDED.name());
            document.setFailureReason(null);
        } catch (Exception e) {
            log.error("Ingestion pipeline failed for document {}", documentId, e);
            document.setProcessingStatus(Document.DocumentStatus.FAILED.name());
            document.setFailureReason(truncate(e.getMessage(), 1000));
        }

        documentRepository.save(document);
    }

    @Async("documentProcessingExecutor")
    public void processAsync(UUID documentId, byte[] fileBytes, String originalFilename, String contentType) {
        processAsync(documentId);
    }

    private String truncate(String message, int max) {
        if (message == null) return "Unknown error during processing";
        return message.length() <= max ? message : message.substring(0, max);
    }
}
