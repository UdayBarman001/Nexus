package com.Let.s_Code.nexus.integration;

import com.Let.s_Code.nexus.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The one true "does the whole application actually wire together" test.
 *
 * Everything else in this test suite is a fast, isolated unit or MockMvc slice test that runs
 * in milliseconds with no external dependencies. This one is different on purpose: it boots
 * the real Spring context against a real Postgres+pgvector database via Testcontainers, so it
 * verifies things unit tests structurally cannot — that Flyway migrations actually apply
 * cleanly, that every @Configuration class produces a valid bean graph, and that JPA entity
 * mappings match the real schema.
 *
 * REQUIRES DOCKER. If Docker isn't available (e.g. this sandbox, or a CI runner without it),
 * this test will fail to start a container — that is expected and is a Docker-availability
 * problem, not a code problem. Run it locally with Docker Desktop / Docker Engine running, or
 * in CI with Testcontainers-Docker support enabled.
 *
 * We deliberately do NOT spin up live Ollama or MinIO containers here: Spring AI's Ollama
 * client and the AWS S3 SDK client are both lazy — they only make network calls when actually
 * invoked, not at bean construction — with one exception, which is why S3Client is mocked
 * below: FileStorageService.ensureBucketExists() runs at startup via @PostConstruct and would
 * otherwise try to reach a real MinIO instance that doesn't exist in this test.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class NexusApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("nexus_test")
            .withUsername("nexus_test")
            .withPassword("nexus_test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // pgvector's CREATE EXTENSION needs superuser-ish privileges the test container's
        // default user already has, and initialize-schema needs a real connection — both are
        // satisfied by the container above. JWT secret just needs to be a valid base64 string.
        registry.add("jwt.secret", () -> "AAudHgc8scwtJnTUFRCEMQWHvJhWmVRf3KHS0nP4kxf");

        // Point Ollama/MinIO at harmless placeholder URLs. Nothing in a plain context-load test
        // actually calls them (see class javadoc), so these just need to be syntactically valid.
        registry.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
        registry.add("minio.url", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "test");
        registry.add("minio.secret-key", () -> "test");
        registry.add("minio.bucket-name", () -> "nexus-test-bucket");
    }

    // Replaces the real S3Client bean so FileStorageService's @PostConstruct bucket check
    // hits a harmless mock instead of trying to reach a nonexistent MinIO at localhost:9000.
    @MockBean
    private S3Client s3Client;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsAndKeyBeansAreWired() {
        assertNotNull(applicationContext);

        // Spot-check a few beans from different layers (config, service, AI integration) rather
        // than just trusting "no exception was thrown" — a silently-missing bean that Spring
        // happened not to need yet is exactly the kind of thing a bare contextLoads() can miss.
        assertNotNull(applicationContext.getBean(RagProperties.class));
        assertNotNull(applicationContext.getBean("documentProcessingExecutor"));
        assertNotNull(applicationContext.getBean(org.springframework.ai.vectorstore.VectorStore.class));
        assertNotNull(applicationContext.getBean(com.Let.s_Code.nexus.Repository.UserRepository.class));
    }
}
