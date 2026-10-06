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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.time.LocalDateTime;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You are Nexus, an elite Enterprise Knowledge AI.
            Answer the user's question using ONLY the provided Context below and, where relevant,
            the recent conversation history.
            If the answer is not contained in the Context, say "I do not have access to this information."
            Do not make up answers. Be concise and cite which document(s) you drew from when it's useful.

            Context:
            {context}
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RagProperties ragProperties;

    // Deliberately NOT @Transactional at the method level: chatClient.prompt().call() is a
    // network call to the Ollama chat model and can legitimately take several seconds to tens
    // of seconds for a longer generation. Wrapping that in a Spring-managed transaction would
    // hold a pooled DB connection idle for that whole duration — exactly the connection-pool
    // exhaustion risk we avoid elsewhere in the ingestion pipeline. Each DB touch below
    // (session lookup/save, message save) is a single repository call, which already gets its
    // own short implicit transaction from Spring Data — we don't need a wider one here.
    public ChatResponse askQuestion(ChatRequest request, User user) {
        log.info("Received question from {}: {}", user.getEmail(), request.getQuery());

        ChatSession session = resolveSession(request.getSessionId(), user);

        // 1. Retrieve the most relevant document chunks from pgvector.
        // Multi-tenant isolation: non-admin users only retrieve chunks from documents they uploaded.
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder()
                .query(request.getQuery())
                .topK(ragProperties.getRetrieval().getTopK())
                .similarityThreshold(ragProperties.getRetrieval().getSimilarityThreshold());

        if (user.getRole() != Role.ADMIN) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchRequestBuilder.filterExpression(
                    b.or(b.eq("uploaderId", user.getId().toString()), b.eq("shared", true)).build());
        }

        SearchRequest searchRequest = searchRequestBuilder.build();
        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        String context = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        List<String> sources = similarDocuments.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("filename", "unknown")))
                .distinct()
                .collect(Collectors.toList());

        if (context.isBlank()) {
            log.warn("No relevant documents found in vector store for query: {}", request.getQuery());
        }

        // 2. Build short-term conversation history so follow-up questions have context.
        List<Message> history = loadHistoryAsMessages(session.getId());

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("context", context));

        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(systemMessage);
        promptMessages.addAll(history);
        promptMessages.add(new UserMessage(request.getQuery()));

        // 3. Generate the response using the configured chat model (Ollama).
        String aiResponse = chatClient.prompt(new Prompt(promptMessages))
                .call()
                .content();

        // 4. Persist both turns of the conversation.
        saveMessage(session, ChatMessage.SenderType.USER, request.getQuery());
        saveMessage(session, ChatMessage.SenderType.ASSISTANT, aiResponse);

        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(deriveTitle(request.getQuery()));
        }
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        return ChatResponse.builder()
                .sessionId(session.getId())
                .answer(aiResponse)
                .sources(sources)
                .build();
    }

    public org.springframework.data.domain.Page<ChatSession> listSessions(User user, Pageable pageable) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId(), boundPageSize(pageable));
    }

    /** Caps page size so a caller can't force an unbounded/huge query (e.g. ?size=999999). */
    private Pageable boundPageSize(Pageable pageable) {
        final int MAX_PAGE_SIZE = 100;
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }

    public List<ChatMessage> getSessionMessages(UUID sessionId, User user) {
        ChatSession session = resolveSession(sessionId, user);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
    }

    public void deleteSession(UUID sessionId, User user) {
        ChatSession session = resolveSession(sessionId, user);
        chatSessionRepository.delete(session);
        log.info("Deleted chat session {} (requested by {})", sessionId, user.getEmail());
    }

    public ChatSession renameSession(UUID sessionId, String newTitle, User user) {
        ChatSession session = resolveSession(sessionId, user);
        session.setTitle(newTitle != null && !newTitle.isBlank() ? newTitle.trim() : "Untitled");
        return chatSessionRepository.save(session);
    }

    private ChatSession resolveSession(UUID sessionId, User user) {
        if (sessionId == null) {
            ChatSession newSession = ChatSession.builder().user(user).build();
            return chatSessionRepository.save(newSession);
        }
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        if (!session.getUser().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedApiException("You do not have access to this chat session.");
        }
        return session;
    }

    private List<Message> loadHistoryAsMessages(UUID sessionId) {
        int window = ragProperties.getChat().getHistoryWindow();
        if (window <= 0) {
            return Collections.emptyList();
        }
        List<ChatMessage> recent = chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "createdAt")));

        return recent.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(m -> m.getSenderType() == ChatMessage.SenderType.USER
                        ? (Message) new UserMessage(m.getMessageBody())
                        : new AssistantMessage(m.getMessageBody()))
                .collect(Collectors.toList());
    }

    private void saveMessage(ChatSession session, ChatMessage.SenderType type, String body) {
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .senderType(type)
                .messageBody(body)
                .build());
    }

    private String deriveTitle(String query) {
        String trimmed = query.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 57) + "...";
    }
}
