-- V4: Risk engine tables — customer risk profiles and alerts

-- One profile per customer. Stores Welford's online algorithm state (decision D6)
-- and behavioral baselines as JSONB (decision D5, Hypersistence Utils).
CREATE TABLE customer_risk_profile (
    id                          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID           NOT NULL UNIQUE REFERENCES app_user (id),

    -- Welford's online algorithm state for rolling amount statistics
    transaction_count           INTEGER        NOT NULL DEFAULT 0,
    amount_mean                 NUMERIC(19, 4),
    amount_m2                   NUMERIC(30, 8),   -- sum of squared deviations

    -- Velocity baselines (rolling 30-day averages)
    avg_transactions_per_hour   DOUBLE PRECISION NOT NULL DEFAULT 0,
    avg_transactions_per_day    DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Behavioral baselines as JSONB (Hypersistence Utils, decision D5)
    -- hour (string key) → frequency: {"9": 0.15, "14": 0.22}
    typical_transaction_hours   JSONB          NOT NULL DEFAULT '{}',
    -- MCC (string key) → frequency: {"5411": 0.30, "5812": 0.20}
    typical_merchant_categories JSONB          NOT NULL DEFAULT '{}',
    -- set of known counterparty account ID strings: ["uuid1", "uuid2"]
    typical_counterparties      JSONB          NOT NULL DEFAULT '[]',

    -- Current risk state
    current_risk_score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    risk_tier                   VARCHAR(10)    NOT NULL DEFAULT 'LOW',
    last_updated                TIMESTAMP      NOT NULL DEFAULT NOW(),
    total_transactions_analyzed INTEGER        NOT NULL DEFAULT 0
);

CREATE TABLE risk_alert (
    id                   UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_number         VARCHAR(30)      NOT NULL UNIQUE,
    transaction_id       UUID             NOT NULL REFERENCES ledger_transaction (id),
    user_id              UUID             NOT NULL REFERENCES app_user (id),
    alert_type           VARCHAR(30)      NOT NULL,
    severity             VARCHAR(10)      NOT NULL,
    status               VARCHAR(20)      NOT NULL DEFAULT 'OPEN',
    risk_score           DOUBLE PRECISION NOT NULL,
    -- JSON breakdown: which rules fired, individual scores, thresholds
    rule_details         JSONB            NOT NULL DEFAULT '{}',
    reviewed_by_admin_id UUID             REFERENCES app_user (id),
    admin_notes          TEXT,
    created_at           TIMESTAMP        NOT NULL DEFAULT NOW(),
    reviewed_at          TIMESTAMP
);

CREATE INDEX idx_risk_alert_user_created  ON risk_alert (user_id, created_at DESC);
CREATE INDEX idx_risk_alert_status_created ON risk_alert (status, created_at DESC);
CREATE INDEX idx_risk_alert_txn           ON risk_alert (transaction_id);
