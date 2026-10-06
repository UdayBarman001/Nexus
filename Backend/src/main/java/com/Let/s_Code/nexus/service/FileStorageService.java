package com.Let.s_Code.nexus.service;

import com.Let.s_Code.nexus.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Ensures the target bucket exists, once, at application startup — not on every upload.
     * A headBucket() round trip on every single request is pure waste once we already know
     * the bucket is there; @PostConstruct runs exactly once after the S3Client bean is ready.
     * If the bucket is deleted out-of-band while the app is running, uploads will start failing
     * fast with a clear S3Exception rather than silently retrying bucket creation per request —
     * an acceptable trade-off for the common case, and a restart resolves it.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("MinIO bucket '{}' already exists.", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' not found, creating it now.", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            // headBucket returns a generic 404 (not always NoSuchBucketException depending on
            // the S3-compatible backend), so fall back to checking the status code before
            // assuming "not found" — anything else (403, network error, ...) should surface
            // clearly at startup instead of being masked by a doomed createBucket attempt.
            if (e.statusCode() == 404) {
                log.info("Bucket '{}' not found (404), creating it now.", bucketName);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } else {
                log.error("Could not verify MinIO bucket '{}' at startup", bucketName, e);
                throw e;
            }
        }
    }

    /**
     * Uploads the file to MinIO and returns the object's storage URL
     * (in the same s3://bucket/key format the rest of the app already expects).
     */
    @Retryable(retryFor = S3Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public String uploadFile(MultipartFile file) throws IOException {
        String objectKey = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("Uploaded file '{}' to MinIO as object key '{}'", file.getOriginalFilename(), objectKey);

        return "s3://" + bucketName + "/" + objectKey;
    }

    /** Deletes the underlying object. Safe to call even if the object no longer exists. */
    public void deleteFile(String s3Url) {
        try {
            String objectKey = extractKey(s3Url);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
            log.info("Deleted object '{}' from MinIO", objectKey);
        } catch (S3Exception e) {
            log.warn("Failed to delete object for url '{}': {}", s3Url, e.getMessage());
            throw new FileStorageException("Failed to delete stored file", e);
        }
    }

    private String extractKey(String s3Url) {
        return s3Url.substring(s3Url.lastIndexOf('/') + 1);
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        // Strip path separators and control characters to prevent object-key traversal.
        return filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
    }

    /**
     * Downloads a file's raw bytes given its stored s3:// URL.
     * Useful later for re-serving the original file to users.
     */
    public byte[] downloadFile(String s3Url) {
        String objectKey = extractKey(s3Url);
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build()
            );
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new FileStorageException("Stored file could not be found: " + objectKey, e);
        } catch (S3Exception e) {
            throw new FileStorageException("Failed to download stored file", e);
        }
    }
}