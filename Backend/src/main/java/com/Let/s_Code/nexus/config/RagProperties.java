package com.Let.s_Code.nexus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Centralized, tunable knobs for the RAG pipeline — chunking, retrieval, and chat history. */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Chat chat = new Chat();

    @Data
    public static class Chunk {
        private int size = 800;
        private int overlap = 100;
    }

    @Data
    public static class Retrieval {
        private int topK = 5;
        private double similarityThreshold = 0.5;
    }

    @Data
    public static class Chat {
        private int historyWindow = 6;
    }
}
