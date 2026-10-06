package com.Let.s_Code.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ChatResponse {
    private UUID sessionId;
    private String answer;
    private List<String> sources;
}
