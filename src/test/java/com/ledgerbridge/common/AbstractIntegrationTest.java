package com.ledgerbridge.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real PostgreSQL database.
 *
 * Uses @ServiceConnection (Spring Boot 3.1+) to auto-wire the container's
 * JDBC URL, username, and password — no @DynamicPropertySource boilerplate needed.
 *
 * Kafka is not started here. Tests that need Kafka should extend a dedicated
 * KafkaIntegrationTest base class (added in Phase 3).
 *
 * The static @Container + @Testcontainers combination means a single PostgreSQL
 * container is shared across all tests in a test class, reducing startup overhead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.bootstrap-servers=localhost:9999")
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ledgerbridge_test")
                    .withUsername("test")
                    .withPassword("test");
}
