-- V10: Risk engine data integrity fixes (review gate, 2026-06-11)

-- T3: Enforce exactly-once alert creation per transaction at the DB level.
-- The application-layer existsByTransactionId check has a TOCTOU race under
-- concurrent Kafka retries. This constraint is the authoritative guard.
ALTER TABLE risk_alert
    ADD CONSTRAINT uq_risk_alert_transaction_id UNIQUE (transaction_id);

-- T10: Full Kafka consumer idempotency for clean transactions (score < threshold).
-- The previous check only skipped re-evaluation for alerted transactions.
-- This table tracks every evaluated event regardless of alert outcome,
-- preventing Welford's baseline from double-incrementing on Kafka redeliveries.
CREATE TABLE processed_transaction_event (
    transaction_id UUID      NOT NULL PRIMARY KEY,
    processed_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
