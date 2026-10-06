package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorizationService {

    // Spring AI's unified interface for Vector Databases (we are using pgvector)
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * Takes extracted text from an uploaded document, splits it into overlapping chunks,
     * embeds those chunks via the configured embedding model, and stores them in pgvector.
     *
     * retryFor = Exception.class is broader than ideal — a narrower type (e.g. a transient-only
     * network/timeout exception from the Ollama client) would avoid pointlessly retrying a
     * genuinely bad request. Left broad deliberately rather than guessing at Spring AI's
     * internal exception hierarchy without being able to verify it against a real build in this
     * environment — worth tightening once you can compile-check against the actual dependency.
     *
     * @return the number of chunks that were embedded and stored.
     */
    public int processAndStore(String rawText, String databaseDocumentId, String filename) {
        return processAndStore(rawText, databaseDocumentId, filename, null, false);
    }

    public int processAndStore(String rawText, String databaseDocumentId, String filename, Long uploaderId) {
        return processAndStore(rawText, databaseDocumentId, filename, uploaderId, false);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public int processAndStore(String rawText, String databaseDocumentId, String filename, Long uploaderId, boolean shared) {
        log.info("Starting AI vectorization for document: {} (uploaderId: {}, shared: {})", filename, uploaderId, shared);

        // Spring AI's TokenTextSplitter chunks by token count; it does not support sliding-window
        // overlap, so rag.chunk.overlap is not applied here (kept in config as a documented no-op
        // for now — swap in a custom overlapping splitter later if retrieval quality calls for it).
        TokenTextSplitter splitter = new TokenTextSplitter(
                ragProperties.getChunk().getSize(), // chunkSize (tokens)
                350,                                 // minChunkSizeChars
                5,                                   // minChunkLengthToEmbed
                10000,                                // maxNumChunks
                true                                  // keepSeparator
        );

        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("documentId", databaseDocumentId);
        metadata.put("filename", filename);
        if (uploaderId != null) {
            metadata.put("uploaderId", uploaderId.toString());
        }
        metadata.put("shared", shared);

        Document baseDocument = new Document(rawText, metadata);

        List<Document> chunks = splitter.apply(List.of(baseDocument));
        log.info("Split document '{}' into {} chunks.", filename, chunks.size());

        // Embeds each chunk via the configured embedding model and writes it to pgvector.
        vectorStore.add(chunks);

        log.info("Successfully embedded and stored {} chunks for document '{}'.", chunks.size(), filename);
        return chunks.size();
    }

    /** Removes every embedded chunk belonging to a document — called when the document is deleted. */
    public void deleteByDocumentId(String databaseDocumentId) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("documentId", databaseDocumentId).build());
            log.info("Removed vector chunks for document {}", databaseDocumentId);
        } catch (Exception e) {
            // Non-fatal: the document row is still deleted; orphaned vectors are a cleanup
            // concern, not a reason to fail the user's delete request.
            log.warn("Could not remove vector chunks for document {}: {}", databaseDocumentId, e.getMessage());
        }
    }
}
