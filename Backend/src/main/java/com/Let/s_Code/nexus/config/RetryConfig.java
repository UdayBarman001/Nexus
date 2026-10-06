package com.Let.s_Code.nexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/** Enables @Retryable around flaky external calls (Ollama embedding/chat calls, MinIO I/O). */
@Configuration
@EnableRetry
public class RetryConfig {
}
