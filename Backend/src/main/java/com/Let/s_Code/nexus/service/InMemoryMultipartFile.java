package com.Let.s_Code.nexus.service;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A minimal in-memory MultipartFile so file bytes captured during a request can be replayed
 * inside an @Async background task, after the original request's temp file is gone.
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content != null ? content : new byte[0];
    }

    @Override
    @NonNull
    public String getName() {
        return filename != null ? filename : "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return content;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull java.io.File dest) throws IOException, IllegalStateException {
        throw new UnsupportedOperationException("transferTo is not supported for in-memory replay files");
    }
}
