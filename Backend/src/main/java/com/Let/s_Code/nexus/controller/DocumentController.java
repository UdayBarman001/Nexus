package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.dto.DocumentResponse;
import com.Let.s_Code.nexus.service.CurrentUserProvider;
import com.Let.s_Code.nexus.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Tag(name = "Documents", description = "Upload, list, download, and delete documents in the knowledge base")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Upload a document. Text extraction and embedding happen asynchronously.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was provided, or the file is empty.");
        }

        User uploader = currentUserProvider.resolve(authentication);
        log.info("Upload request received for file: {} from {}", file.getOriginalFilename(), uploader.getEmail());

        Document document = documentService.upload(file, uploader);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.fromEntity(document));
    }

    @Operation(summary = "List documents you own (admins see every document)")
    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Document.DocumentStatus status,
            Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        Page<Document> documents = documentService.listForUser(
                user, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(documents.map(DocumentResponse::fromEntity));
    }

    @Operation(summary = "Get document statistics for the current user (counts by status and total vector chunks)")
    @GetMapping("/stats")
    public ResponseEntity<com.Let.s_Code.nexus.dto.DocumentStatsResponse> getDocumentStats(Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        return ResponseEntity.ok(documentService.getStatsForUser(user));
    }

    @Operation(summary = "Get metadata (and processing status) for a single document")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id, Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        return ResponseEntity.ok(DocumentResponse.fromEntity(documentService.getForUser(id, user)));
    }

    @Operation(summary = "Download the original file bytes")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id, Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        Document doc = documentService.getForUser(id, user);
        byte[] bytes = documentService.downloadBytes(doc);

        String encodedName = java.net.URLEncoder.encode(doc.getFilename(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(doc.getContentType() != null ? MediaType.parseMediaType(doc.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(bytes);
    }

    @Operation(summary = "Delete a document (removes the file, its metadata, and its embedded chunks)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        documentService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
