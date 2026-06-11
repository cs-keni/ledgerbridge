-- V2: Bank accounts

CREATE TABLE account (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20)    NOT NULL UNIQUE,
    user_id        UUID           NOT NULL REFERENCES app_user (id),
    type           VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    balance        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'USD',
    opened_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    closed_at      TIMESTAMP
);

CREATE INDEX idx_account_user ON account (user_id, status);
