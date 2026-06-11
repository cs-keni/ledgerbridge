-- V9: API-level idempotency key table + correlation_id on ledger_transaction
-- Idempotency keys: 24h TTL, (idem_key, user_id) unique, request_hash for mismatch detection (422)
-- Correlation ID: nullable column on ledger_transaction for SQL-based incident investigation

CREATE TABLE idempotency_key (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    idem_key      VARCHAR(255) NOT NULL,
    user_id       UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    request_hash  VARCHAR(64) NOT NULL,
    response_json TEXT        NOT NULL,
    status_code   INT         NOT NULL,
    expires_at    TIMESTAMP   NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_idempotency_key_user ON idempotency_key (idem_key, user_id);
CREATE INDEX idx_idempotency_expires ON idempotency_key (expires_at);

ALTER TABLE ledger_transaction ADD COLUMN correlation_id VARCHAR(36);
CREATE INDEX idx_ledger_txn_correlation ON ledger_transaction (correlation_id)
    WHERE correlation_id IS NOT NULL;
