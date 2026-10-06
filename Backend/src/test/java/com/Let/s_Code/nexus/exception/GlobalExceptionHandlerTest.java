package com.Let.s_Code.nexus.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every branch here matters: this is the layer that decides what a client actually sees when
 * something goes wrong, and getting the status code wrong (e.g. leaking a 500 for something
 * that's really a 404 or 409) misleads API consumers and retry logic alike.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/documents/123");
    }

    @Test
    void resourceNotFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Document not found"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Document not found", response.getBody().getMessage());
        assertEquals("/api/documents/123", response.getBody().getPath());
    }

    @Test
    void duplicateResourceMapsTo409() {
        ResponseEntity<ErrorResponse> response = handler.handleDuplicate(
                new DuplicateResourceException("Email already registered"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(
                new AccessDeniedApiException("Not your document"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Role must be USER or ADMIN"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void fileStorageErrorMapsTo502AndDoesNotLeakInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleFileStorage(
                new FileStorageException("MinIO connection refused", new RuntimeException("connect timed out")), request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("File storage is currently unavailable. Please try again shortly.", response.getBody().getMessage());
    }

    @Test
    void unhandledExceptionMapsTo500AndDoesNotLeakInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new NullPointerException("some internal null somewhere"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred. Please try again later.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("NullPointerException"));
    }

    @Test
    void everyErrorResponseIncludesATimestamp() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("x"), request);

        assertNotNull(response.getBody().getTimestamp());
    }
}
