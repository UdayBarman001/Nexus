
package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.ChatMessage;
import com.Let.s_Code.nexus.Entity.ChatSession;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.dto.ChatMessageResponse;
import com.Let.s_Code.nexus.dto.ChatRequest;
import com.Let.s_Code.nexus.dto.ChatResponse;
import com.Let.s_Code.nexus.dto.ChatSessionResponse;
import com.Let.s_Code.nexus.service.ChatService;
import com.Let.s_Code.nexus.service.CurrentUserProvider;
import com.Let.s_Code.nexus.dto.UpdateSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Chat", description = "RAG-grounded chat and chat history")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Ask Nexus a question, grounded in your uploaded documents")
    @PostMapping({"", "/ask"})
    public ResponseEntity<ChatResponse> askNexus(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        return ResponseEntity.ok(chatService.askQuestion(request, user));
    }

    @Operation(summary = "List your chat sessions, most recently updated first")
    @GetMapping("/sessions")
    public ResponseEntity<Page<ChatSessionResponse>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        Page<ChatSession> sessions = chatService.listSessions(user, PageRequest.of(page, size));
        return ResponseEntity.ok(sessions.map(ChatSessionResponse::fromEntity));
    }

    @Operation(summary = "Get the full message history for one of your chat sessions")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getSessionMessages(
            @PathVariable UUID sessionId, Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        List<ChatMessage> messages = chatService.getSessionMessages(sessionId, user);
        return ResponseEntity.ok(messages.stream().map(ChatMessageResponse::fromEntity).collect(Collectors.toList()));
    }

    @Operation(summary = "Rename a chat session")
    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSessionResponse> renameSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateSessionRequest request,
            Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        ChatSession updated = chatService.renameSession(sessionId, request.getTitle(), user);
        return ResponseEntity.ok(ChatSessionResponse.fromEntity(updated));
    }

    @Operation(summary = "Delete a chat session and all its messages")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        User user = currentUserProvider.resolve(authentication);
        chatService.deleteSession(sessionId, user);
        return ResponseEntity.noContent().build();
    }
}
