package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.UserRepository;
import com.Let.s_Code.nexus.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    // We need this to look up the specific User entity based on the JWT token
    private final UserRepository userRepository;

    /**
     * Endpoint to upload a document and trigger the RAG Vectorization Pipeline.
     */
    @PostMapping("/upload")
    public ResponseEntity<Document> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        log.info("Upload request received for file: {}", file.getOriginalFilename());

        // 1. Extract the user's email from the validated JWT token
        String email = authentication.getName();

        // 2. Fetch the actual Enterprise User from the database
        User uploader = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Security Exception: Authenticated user not found in database."));

        // 3. Hand the file and the user off to the Ingestion Service
        Document processedDocument = documentIngestionService.uploadAndProcessDocument(file, uploader);

        // 4. Return the saved document metadata (including its ID and EMBEDDED status) to the client
        return ResponseEntity.ok(processedDocument);
    }
}
