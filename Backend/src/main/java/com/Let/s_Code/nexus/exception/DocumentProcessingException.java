package com.Let.s_Code.nexus.exception;

/** Thrown when text extraction or vectorization of an uploaded document fails. */
public class DocumentProcessingException extends RuntimeException {
    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
    public DocumentProcessingException(String message) {
        super(message);
    }
}
