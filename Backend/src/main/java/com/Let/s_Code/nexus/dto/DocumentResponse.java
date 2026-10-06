package com.Let.s_Code.nexus.dto;

import com.Let.s_Code.nexus.Entity.Document;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {
    private UUID id;
    private String filename;
    private String contentType;
    private Long fileSizeBytes;
    private String processingStatus;
    private String failureReason;
    private int chunkCount;
    private String uploaderEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResponse fromEntity(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .filename(doc.getFilename())
                .contentType(doc.getContentType())
                .fileSizeBytes(doc.getFileSizeBytes())
                .processingStatus(doc.getProcessingStatus())
                .failureReason(doc.getFailureReason())
                .chunkCount(doc.getChunkCount())
                .uploaderEmail(doc.getUploader() != null ? doc.getUploader().getEmail() : null)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
