package com.Let.s_Code.nexus.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 1024)
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "s3_url", nullable = false, length = 2048)
    private String s3Url; // Where the raw file is safely stored

    @Builder.Default
    @Column(name = "processing_status", nullable = false)
    private String processingStatus = DocumentStatus.PENDING.name(); // PENDING, PROCESSING, EMBEDDED, FAILED

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @Builder.Default
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader; // Tracks exactly who uploaded this file

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DocumentStatus {
        PENDING, PROCESSING, EMBEDDED, FAILED
    }
}
