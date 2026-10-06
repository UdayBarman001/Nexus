package com.Let.s_Code.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatsResponse {
    private long totalDocuments;
    private long embeddedDocuments;
    private long processingDocuments;
    private long pendingDocuments;
    private long failedDocuments;
    private long totalChunks;
}
