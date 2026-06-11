package com.ledgerbridge;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — full context load test with Testcontainers is added in Phase 1
 * once Flyway migrations and JPA entities exist.
 */
class LedgerBridgeApplicationTests {

    @Test
    void applicationClassIsPresent() {
        // Verifies the main class compiles and is on the classpath.
        // Replace with @SpringBootTest + Testcontainers in Phase 1.
        var appClass = LedgerBridgeApplication.class;
        assert appClass != null;
    }
}
