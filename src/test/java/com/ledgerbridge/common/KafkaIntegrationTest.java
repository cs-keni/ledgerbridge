package com.ledgerbridge.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need PostgreSQL + Kafka.
 *
 * Uses @ServiceConnection for the PostgreSQL Testcontainer and
 * @EmbeddedKafka (in-process broker) for Kafka — no separate Kafka Docker image needed.
 * The property placeholder wires the embedded broker address into spring.kafka.bootstrap-servers.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"transaction-events"},
        brokerProperties = {"auto.create.topics.enable=true"}
)
public abstract class KafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ledgerbridge_test")
                    .withUsername("test")
                    .withPassword("test");
}
