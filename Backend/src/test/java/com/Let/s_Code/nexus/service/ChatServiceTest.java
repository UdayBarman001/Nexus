package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.ChatMessage;
import com.Let.s_Code.nexus.Entity.ChatSession;
import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.ChatMessageRepository;
import com.Let.s_Code.nexus.Repository.ChatSessionRepository;
import com.Let.s_Code.nexus.config.RagProperties;
import com.Let.s_Code.nexus.dto.ChatRequest;
import com.Let.s_Code.nexus.dto.ChatResponse;
import com.Let.s_Code.nexus.exception.AccessDeniedApiException;
import com.Let.s_Code.nexus.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    // Deep stubs let us mock the fluent chatClient.prompt(...).call().content() chain
    // without hand-rolling every intermediate interface in the builder.
    private final ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);

    private ChatService chatService;
    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        chatService = new ChatService(vectorStore, chatClient, chatSessionRepository, chatMessageRepository, ragProperties);

        user = User.builder().id(1L).email("user@example.com").role(Role.USER).build();
        admin = User.builder().id(2L).email("admin@example.com").role(Role.ADMIN).build();

        lenient().when(chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        lenient().when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class)).call().content())
                .thenReturn("This is the AI's answer.");
    }

    @Test
    void newQuestionWithNoSessionIdCreatesANewSession() {
        ChatSession newSession = ChatSession.builder().id(UUID.randomUUID()).user(user).build();
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(newSession);

        ChatRequest request = new ChatRequest();
        request.setQuery("What is our refund policy?");

        ChatResponse response = chatService.askQuestion(request, user);

        assertEquals(newSession.getId(), response.getSessionId());
        assertEquals("This is the AI's answer.", response.getAnswer());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class)); // user turn + assistant turn
    }

    @Test
    void continuingAnExistingSessionReusesIt() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = ChatSession.builder().id(sessionId).user(user).title("Earlier chat").build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(existing);

        ChatRequest request = new ChatRequest();
        request.setQuery("Follow-up question");
        request.setSessionId(sessionId);

        ChatResponse response = chatService.askQuestion(request, user);

        assertEquals(sessionId, response.getSessionId());
        verify(chatSessionRepository, never()).save(argThat(s -> s.getId() == null));
    }

    @Test
    void userCannotContinueAnotherUsersSession() {
        UUID sessionId = UUID.randomUUID();
        User someoneElse = User.builder().id(99L).email("someone-else@example.com").role(Role.USER).build();
        ChatSession existing = ChatSession.builder().id(sessionId).user(someoneElse).build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));

        ChatRequest request = new ChatRequest();
        request.setQuery("Trying to read someone else's chat");
        request.setSessionId(sessionId);

        assertThrows(AccessDeniedApiException.class, () -> chatService.askQuestion(request, user));
    }

    @Test
    void adminCanContinueAnyUsersSession() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = ChatSession.builder().id(sessionId).user(user).build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(existing);

        ChatRequest request = new ChatRequest();
        request.setQuery("Admin peeking into a user session");
        request.setSessionId(sessionId);

        assertDoesNotThrow(() -> chatService.askQuestion(request, admin));
    }

    @Test
    void referencingANonExistentSessionRaisesNotFound() {
        UUID missingId = UUID.randomUUID();
        when(chatSessionRepository.findById(missingId)).thenReturn(Optional.empty());

        ChatRequest request = new ChatRequest();
        request.setQuery("Anything");
        request.setSessionId(missingId);

        assertThrows(ResourceNotFoundException.class, () -> chatService.askQuestion(request, user));
    }

    @Test
    void deleteSessionDeletesWhenOwner() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = ChatSession.builder().id(sessionId).user(user).build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));

        chatService.deleteSession(sessionId, user);

        verify(chatSessionRepository).delete(existing);
    }

    @Test
    void deleteSessionRejectsNonOwner() {
        UUID sessionId = UUID.randomUUID();
        User other = User.builder().id(99L).email("other@example.com").role(Role.USER).build();
        ChatSession existing = ChatSession.builder().id(sessionId).user(other).build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedApiException.class, () -> chatService.deleteSession(sessionId, user));
        verify(chatSessionRepository, never()).delete(any());
    }

    @Test
    void renameSessionUpdatesTitle() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = ChatSession.builder().id(sessionId).user(user).title("Old Title").build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        ChatSession result = chatService.renameSession(sessionId, "New Title", user);

        assertEquals("New Title", result.getTitle());
        verify(chatSessionRepository).save(existing);
    }

    @Test
    void getSessionMessagesAllowsAdminAccessToOtherUsersSession() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = ChatSession.builder().id(sessionId).user(user).build();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());

        List<ChatMessage> messages = chatService.getSessionMessages(sessionId, admin);

        assertNotNull(messages);
        verify(chatMessageRepository).findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
