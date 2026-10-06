package com.Let.s_Code.nexus.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {

    @NotBlank(message = "Query must not be empty")
    @Size(max = 4000, message = "Query is too long (max 4000 characters)")
    @JsonAlias("question")
    private String query;

    /** Optional — omit to start a new chat session, or pass an existing session's id to continue it. */
    private UUID sessionId;
}
