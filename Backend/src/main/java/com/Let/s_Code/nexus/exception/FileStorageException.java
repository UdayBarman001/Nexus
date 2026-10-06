package com.Let.s_Code.nexus.exception;

/** Thrown when the object storage (MinIO/S3) layer fails to store or retrieve a file. */
public class FileStorageException extends RuntimeException {
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
    public FileStorageException(String message) {
        super(message);
    }
}
