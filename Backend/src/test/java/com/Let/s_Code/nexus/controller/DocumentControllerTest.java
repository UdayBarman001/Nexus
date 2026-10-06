package com.Let.s_Code.nexus.controller;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.exception.AccessDeniedApiException;
import com.Let.s_Code.nexus.exception.ResourceNotFoundException;
import com.Let.s_Code.nexus.service.CurrentUserProvider;
import com.Let.s_Code.nexus.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.Let.s_Code.nexus.config.SecurityConfig;
import com.Let.s_Code.nexus.config.ApplicationConfig;
import com.Let.s_Code.nexus.config.JwtAuthenticationFilter;

@WebMvcTest(
        controllers = DocumentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, ApplicationConfig.class, JwtAuthenticationFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DocumentService documentService;
    @MockBean private CurrentUserProvider currentUserProvider;

    private final User owner = User.builder().id(1L).email("owner@example.com").role(Role.USER).build();

    @Test
    void uploadingAFileReturns202Accepted() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);

        Document pending = Document.builder()
                .id(UUID.randomUUID())
                .filename("report.pdf")
                .processingStatus(Document.DocumentStatus.PENDING.name())
                .uploader(owner)
                .build();
        when(documentService.upload(any(), eq(owner))).thenReturn(pending);

        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "dummy content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file).with(user("owner@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processingStatus").value("PENDING"))
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void uploadingWithoutAFileReturns400() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload").file(emptyFile).with(user("owner@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingDocumentsReturns200WithAPage() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);

        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .filename("a.pdf")
                .processingStatus(Document.DocumentStatus.EMBEDDED.name())
                .uploader(owner)
                .build();
        when(documentService.listForUser(eq(owner), any(), any()))
                .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/documents").with(user("owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].filename").value("a.pdf"));
    }

    @Test
    void gettingAMissingDocumentReturns404() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);
        UUID missingId = UUID.randomUUID();
        when(documentService.getForUser(eq(missingId), eq(owner)))
                .thenThrow(new ResourceNotFoundException("Document not found: " + missingId));

        mockMvc.perform(get("/api/documents/" + missingId).with(user("owner@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void gettingSomeoneElsesDocumentReturns403() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);
        UUID otherId = UUID.randomUUID();
        when(documentService.getForUser(eq(otherId), eq(owner)))
                .thenThrow(new AccessDeniedApiException("You do not have access to this document."));

        mockMvc.perform(get("/api/documents/" + otherId).with(user("owner@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingADocumentReturns204() throws Exception {
        when(currentUserProvider.resolve(any())).thenReturn(owner);
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/" + id).with(user("owner@example.com")))
                .andExpect(status().isNoContent());
    }
}
