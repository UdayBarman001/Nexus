package com.Let.s_Code.nexus.exception;

/** Thrown on attempts to create a resource that violates a uniqueness constraint (e.g. email already registered). */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
