package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final VectorizationService vectorizationService;

    /**
     * Handles the initial upload of a document, saves its metadata,
     * and triggers the AI vectorization pipeline.
     */
    public Document uploadAndProcessDocument(MultipartFile file, User uploader) {
        log.info("Received file upload request: {}", file.getOriginalFilename());

        // 1. Simulate saving the raw file to secure cloud storage (like AWS S3)
        // In a production environment, you would upload the file stream here.
        String simulatedS3Url = "s3://nexus-enterprise-vault/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        // 2. Build the Document Entity with PENDING status
        Document document = Document.builder()
                .filename(file.getOriginalFilename())
                .s3Url(simulatedS3Url)
                .processingStatus("PENDING") // Ready for the AI pipeline
                .uploader(uploader)
                .build();

        // 3. Save the metadata to PostgreSQL
        Document savedDocument = documentRepository.save(document);
        log.info("Document metadata saved securely. ID: {}", savedDocument.getId());

        // 4. Trigger the AI Vectorization Pipeline
        // NOTE: this reads the raw bytes as plain text. For PDFs/DOCX etc. you'll
        // want a real text-extraction step (e.g. Apache Tika) before this call.
        try {
            String rawText = new String(file.getBytes(), StandardCharsets.UTF_8);

            vectorizationService.processAndStore(
                    rawText,
                    savedDocument.getId().toString(),
                    savedDocument.getFilename()
            );

            savedDocument.setProcessingStatus("EMBEDDED");
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes: {}", file.getOriginalFilename(), e);
            savedDocument.setProcessingStatus("FAILED");
        } catch (RuntimeException e) {
            log.error("Vectorization pipeline failed for document: {}", savedDocument.getId(), e);
            savedDocument.setProcessingStatus("FAILED");
        }

        return documentRepository.save(savedDocument);
    }
}