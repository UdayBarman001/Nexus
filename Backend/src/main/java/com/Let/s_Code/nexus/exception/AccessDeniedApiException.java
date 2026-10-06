package com.Let.s_Code.nexus.exception;

/** Thrown when an authenticated user tries to act on a resource they don't own and aren't an admin. */
public class AccessDeniedApiException extends RuntimeException {
    public AccessDeniedApiException(String message) {
        super(message);
    }
}
