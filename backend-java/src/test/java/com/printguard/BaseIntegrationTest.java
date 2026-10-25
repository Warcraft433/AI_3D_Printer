package com.printguard;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Production-grade abstract base integration test using Testcontainers.
 * Automatically provisions an ephemeral PostgreSQL container with Flyway migrations
 * in environments where Docker is available (e.g., CI/CD runners), while providing
 * graceful fallback for environments without an active Docker daemon.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BaseIntegrationTest.class);

    protected static PostgreSQLContainer<?> postgresContainer;
    protected static boolean isDockerRunning = false;

    static {
        try {
            isDockerRunning = DockerClientFactory.instance().isDockerAvailable();
            if (isDockerRunning) {
                postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("printguard_test")
                        .withUsername("printguard_test_user")
                        .withPassword("test_secret_pass");
                postgresContainer.start();
                log.info("Testcontainers: Started ephemeral PostgreSQL container at {}", postgresContainer.getJdbcUrl());
            } else {
                log.info("Testcontainers: Docker daemon not detected on host. Falling back to embedded test datasource.");
            }
        } catch (Throwable t) {
            log.warn("Testcontainers initialization notice: {}", t.getMessage());
            isDockerRunning = false;
        }
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        if (isDockerRunning && postgresContainer != null && postgresContainer.isRunning()) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgresContainer::getUsername);
            registry.add("spring.datasource.password", postgresContainer::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
        }
    }

    @AfterAll
    static void tearDownAll() {
        // Container cleanup is automatically handled by Ryuk, but explicit stop can be called
    }
}
