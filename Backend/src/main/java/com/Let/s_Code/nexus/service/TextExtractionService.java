package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.exception.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Wraps Apache Tika so we get real, format-aware text extraction (PDF, DOCX, PPTX, XLSX,
 * HTML, RTF, plain text, ...) instead of naively decoding raw file bytes as UTF-8, which
 * silently produces garbage for anything that isn't already plain text.
 */
@Slf4j
@Service
public class TextExtractionService {

    // -1 disables Tika's default content length cap so large documents aren't silently truncated.
    private final Tika tika = new Tika();

    public TextExtractionService() {
        tika.setMaxStringLength(-1);
    }

    public String extractText(MultipartFile file) {
        try (var stream = file.getInputStream()) {
            String text = tika.parseToString(stream);
            if (text == null || text.isBlank()) {
                throw new DocumentProcessingException(
                        "No extractable text was found in '" + file.getOriginalFilename() + "'. " +
                        "The file may be empty, image-only, or an unsupported format.");
            }
            return text;
        } catch (IOException e) {
            log.error("I/O error reading file '{}' for text extraction", file.getOriginalFilename(), e);
            throw new DocumentProcessingException("Could not read uploaded file: " + file.getOriginalFilename(), e);
        } catch (TikaException e) {
            log.error("Tika failed to parse file '{}'", file.getOriginalFilename(), e);
            throw new DocumentProcessingException(
                    "Could not extract text from '" + file.getOriginalFilename() + "'. The format may be unsupported or the file corrupted.", e);
        }
    }
}
