package com.Let.s_Code.nexus.exception;

/** Thrown when a requested entity (user, document, chat session, ...) does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
