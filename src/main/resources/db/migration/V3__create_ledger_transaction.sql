-- V3: Ledger transactions
-- Table is named ledger_transaction because TRANSACTION is a SQL reserved/contextual
-- keyword (START TRANSACTION, isolation-level syntax). Decision locked in TODOS.md.

CREATE TABLE ledger_transaction (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_number      VARCHAR(30)    NOT NULL UNIQUE,
    account_id              UUID           NOT NULL REFERENCES account (id),
    counterparty_account_id UUID           REFERENCES account (id),
    type                    VARCHAR(30)    NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)     NOT NULL DEFAULT 'USD',
    status                  VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    merchant_category       VARCHAR(10),
    description             TEXT,
    initiated_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    ip_address              VARCHAR(45),
    device_fingerprint      VARCHAR(255)
);

-- Composite indexes required by VelocityRule (single conditional-aggregation query,
-- decision D17) and GraphPatternRule traversal (decision D18).
CREATE INDEX idx_ledger_txn_account_time
    ON ledger_transaction (account_id, initiated_at DESC);

CREATE INDEX idx_ledger_txn_counterparty_time
    ON ledger_transaction (counterparty_account_id, initiated_at DESC)
    WHERE counterparty_account_id IS NOT NULL;

CREATE INDEX idx_ledger_txn_status
    ON ledger_transaction (account_id, status);
