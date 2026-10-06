package com.Let.s_Code.nexus.dto;

import com.Let.s_Code.nexus.Entity.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatMessageResponse {
    private UUID id;
    private String senderType;
    private String messageBody;
    private LocalDateTime createdAt;

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .senderType(message.getSenderType().name())
                .messageBody(message.getMessageBody())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
