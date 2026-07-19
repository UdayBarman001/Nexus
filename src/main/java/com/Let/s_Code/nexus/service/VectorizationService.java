package com.Let.s_Code.nexus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorizationService {

    // Spring AI's unified interface for Vector Databases (we are using pgvector)
    private final VectorStore vectorStore;

    /**
     * Takes raw text from an uploaded file, splits it into chunks,
     * embeds those chunks, and stores them in the Vector Database.
     */
    public void processAndStore(String rawText, String databaseDocumentId, String filename) {
        log.info("Starting AI vectorization for document: {}", filename);

        try {
            // 1. CHUNKING: AI models have context limits. We must split the document into smaller pieces.
            TokenTextSplitter splitter = new TokenTextSplitter();

            // Create a Spring AI Document (Note: This is org.springframework.ai.document.Document)
            // We attach metadata so the AI knows exactly where this text came from.
            Document baseDocument = new Document(rawText, Map.of(
                    "documentId", databaseDocumentId,
                    "filename", filename
            ));

            // Split the document into an array of smaller chunks
            List<Document> chunks = splitter.apply(List.of(baseDocument));
            log.info("Split document '{}' into {} chunks.", filename, chunks.size());

            // 2 & 3. EMBEDDING & STORING:
            // This single line is the magic of Spring AI. It takes our chunks, sends them
            // to the configured Embedding Model to get the math vectors, and saves them to pgvector.
            vectorStore.add(chunks);

            log.info("Successfully embedded and stored document in the Vector Vault.");

        } catch (Exception e) {
            log.error("Failed to vectorize document: {}", filename, e);
            throw new RuntimeException("Vectorization pipeline failed", e);
        }
    }
}
