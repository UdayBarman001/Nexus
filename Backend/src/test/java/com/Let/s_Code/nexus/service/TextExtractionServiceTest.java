package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.exception.DocumentProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TextExtractionServiceTest {

    private final TextExtractionService textExtractionService = new TextExtractionService();

    @Test
    void extractsTextFromAPlainTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "The quarterly revenue grew by twelve percent.".getBytes(StandardCharsets.UTF_8));

        String text = textExtractionService.extractText(file);

        assertTrue(text.contains("quarterly revenue"));
    }

    @Test
    void rejectsAnEmptyFileWithAClearError() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        DocumentProcessingException ex = assertThrows(DocumentProcessingException.class,
                () -> textExtractionService.extractText(file));

        assertTrue(ex.getMessage().contains("empty.txt"));
    }

    @Test
    void extractsTextFromHtml() {
        String html = "<html><body><h1>Policy</h1><p>Refunds are processed within 14 days.</p></body></html>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.html", "text/html", html.getBytes(StandardCharsets.UTF_8));

        String text = textExtractionService.extractText(file);

        assertTrue(text.contains("Refunds are processed within 14 days"));
        // Tika should strip the markup itself, not just leave it embedded in the output.
        assertFalse(text.contains("<h1>"));
    }
}
