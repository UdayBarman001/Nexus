package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.Entity.Document;
import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Entity.User;
import com.Let.s_Code.nexus.Repository.DocumentRepository;
import com.Let.s_Code.nexus.exception.AccessDeniedApiException;
import com.Let.s_Code.nexus.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentIngestionService documentIngestionService;
    @Mock private VectorizationService vectorizationService;
    @Mock private FileStorageService fileStorageService;

    private DocumentService documentService;

    private User owner;
    private User otherUser;
    private User admin;
    private Document document;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, documentIngestionService, vectorizationService, fileStorageService);

        owner = User.builder().id(1L).email("owner@example.com").role(Role.USER).build();
        otherUser = User.builder().id(2L).email("other@example.com").role(Role.USER).build();
        admin = User.builder().id(3L).email("admin@example.com").role(Role.ADMIN).build();

        document = Document.builder()
                .id(UUID.randomUUID())
                .filename("report.pdf")
                .s3Url("s3://nexus-documents/some-key")
                .processingStatus(Document.DocumentStatus.EMBEDDED.name())
                .uploader(owner)
                .build();
    }

    @Test
    void ownerCanAccessTheirOwnDocument() {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        Document result = documentService.getForUser(document.getId(), owner);

        assertEquals(document.getId(), result.getId());
    }

    @Test
    void adminCanAccessAnyDocument() {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        Document result = documentService.getForUser(document.getId(), admin);

        assertEquals(document.getId(), result.getId());
    }

    @Test
    void nonOwnerNonAdminIsDeniedAccess() {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedApiException.class, () -> documentService.getForUser(document.getId(), otherUser));
    }

    @Test
    void missingDocumentRaisesNotFound() {
        UUID randomId = UUID.randomUUID();
        when(documentRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.getForUser(randomId, owner));
    }

    @Test
    void deletingADocumentCleansUpVectorsAndFile() {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        documentService.delete(document.getId(), owner);

        verify(vectorizationService).deleteByDocumentId(document.getId().toString());
        verify(fileStorageService).deleteFile(document.getS3Url());
        verify(documentRepository).delete(document);
    }

    @Test
    void nonOwnerCannotDeleteSomeoneElsesDocument() {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedApiException.class, () -> documentService.delete(document.getId(), otherUser));

        verify(documentRepository, never()).delete(any());
        verify(vectorizationService, never()).deleteByDocumentId(any());
    }

    @Test
    void listForUserClampsAnOversizedPageRequest() {
        Pageable huge = PageRequest.of(0, 999_999);
        when(documentRepository.findByUploaderId(eq(owner.getId()), argThat(p -> p.getPageSize() == 100)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        documentService.listForUser(owner, huge);

        verify(documentRepository).findByUploaderId(eq(owner.getId()), argThat(p -> p.getPageSize() == 100));
    }

    @Test
    void adminListingUsesFindAllNotFindByUploader() {
        when(documentRepository.findAll(any(Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());

        documentService.listForUser(admin, PageRequest.of(0, 10));

        verify(documentRepository).findAll(any(Pageable.class));
        verify(documentRepository, never()).findByUploaderId(anyLong(), any());
    }
}
