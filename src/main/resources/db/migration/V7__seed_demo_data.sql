-- V7: Development seed data — 5 labeled fraud-scenario customers
--
-- These 5 customers are the baseline for Phase 4 per-scenario Testcontainers tests.
-- See docs/RISK_ENGINE_TEST_MATRIX.md for full scenario specs and expected score ranges.
--
-- Fixed UUIDs are referenced in TestScenarioIds.java (test constants).
-- All historical transactions are dated > 2 days ago so 1h/24h velocity windows
-- are clean when Phase 4 tests add their trigger transactions on top.
--
-- Plaintext passwords are all "password" (BCrypt cost 10).
-- Phase 2 will replace with proper auth flows.

-- ── Users ────────────────────────────────────────────────────────────────────

INSERT INTO app_user (id, email, password_hash, first_name, last_name, role, enabled)
VALUES
    ('a0000001-0000-0000-0000-000000000001',
     'alice@seed.dev',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
     'Alice', 'Normal', 'USER', TRUE),

    ('b0000002-0000-0000-0000-000000000002',
     'bob@seed.dev',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
     'Bob', 'Velocity', 'USER', TRUE),

    ('c0000003-0000-0000-0000-000000000003',
     'carol@seed.dev',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
     'Carol', 'HighAmount', 'USER', TRUE),

    ('d0000004-0000-0000-0000-000000000004',
     'dave@seed.dev',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
     'Dave', 'FanOut', 'USER', TRUE),

    ('e0000005-0000-0000-0000-000000000005',
     'eve@seed.dev',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
     'Eve', 'RoundTrip', 'USER', TRUE);

-- ── Accounts ─────────────────────────────────────────────────────────────────

INSERT INTO account (id, account_number, user_id, type, status, balance, opened_at)
VALUES
    ('a0000001-0001-0000-0000-000000000001',
     'ACC-0000000001',
     'a0000001-0000-0000-0000-000000000001',
     'CHECKING', 'ACTIVE', 5000.0000,
     NOW() - INTERVAL '90 days'),

    ('b0000002-0001-0000-0000-000000000002',
     'ACC-0000000002',
     'b0000002-0000-0000-0000-000000000002',
     'CHECKING', 'ACTIVE', 3000.0000,
     NOW() - INTERVAL '90 days'),

    ('c0000003-0001-0000-0000-000000000003',
     'ACC-0000000003',
     'c0000003-0000-0000-0000-000000000003',
     'CHECKING', 'ACTIVE', 50000.0000,
     NOW() - INTERVAL '90 days'),

    ('d0000004-0001-0000-0000-000000000004',
     'ACC-0000000004',
     'd0000004-0000-0000-0000-000000000004',
     'CHECKING', 'ACTIVE', 100000.0000,
     NOW() - INTERVAL '90 days'),

    ('e0000005-0001-0000-0000-000000000005',
     'ACC-0000000005',
     'e0000005-0000-0000-0000-000000000005',
     'CHECKING', 'ACTIVE', 25000.0000,
     NOW() - INTERVAL '90 days');

-- ── Historical transactions ───────────────────────────────────────────────────
-- All timestamps are > 2 days ago so 1h/24h velocity windows are clean.

-- S1 Alice: 30 transactions over 30 days, amounts $75–$145, MCCs cycling,
-- counterparties = Bob and Carol (established baseline relationships).
INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, merchant_category, description,
    initiated_at, completed_at
)
SELECT
    gen_random_uuid(),
    'TXN-' || to_char(NOW() - (s + 2) * INTERVAL '1 day', 'YYYYMMDD') || '-A' || lpad(s::text, 7, '0'),
    'a0000001-0001-0000-0000-000000000001'::uuid,
    CASE WHEN s % 2 = 0
        THEN 'b0000002-0001-0000-0000-000000000002'
        ELSE 'c0000003-0001-0000-0000-000000000003' END::uuid,
    'DEPOSIT',
    (75 + (s % 15) * 5)::NUMERIC(19, 4),
    'USD', 'COMPLETED',
    CASE WHEN s % 3 = 0 THEN '5411' WHEN s % 3 = 1 THEN '5541' ELSE '5812' END,
    'Baseline transaction — normal pattern (S1)',
    date_trunc('day', NOW() - (s + 2) * INTERVAL '1 day')
        + make_interval(hours => 9 + (s % 10)),
    date_trunc('day', NOW() - (s + 2) * INTERVAL '1 day')
        + make_interval(hours => 9 + (s % 10)) + INTERVAL '5 minutes'
FROM generate_series(1, 30) AS s;

-- S2 Bob: 15 transactions over 30 days, amounts $70–$130, weekday business hours,
-- counterparties = Alice, Carol, Dave.
INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, merchant_category, description,
    initiated_at, completed_at
)
SELECT
    gen_random_uuid(),
    'TXN-' || to_char(NOW() - (s * 2 + 2) * INTERVAL '1 day', 'YYYYMMDD') || '-B' || lpad(s::text, 7, '0'),
    'b0000002-0001-0000-0000-000000000002'::uuid,
    CASE s % 3
        WHEN 0 THEN 'a0000001-0001-0000-0000-000000000001'
        WHEN 1 THEN 'c0000003-0001-0000-0000-000000000003'
        ELSE        'd0000004-0001-0000-0000-000000000004' END::uuid,
    'TRANSFER_DEBIT',
    (70 + (s % 7) * 10)::NUMERIC(19, 4),
    'USD', 'COMPLETED',
    CASE WHEN s % 2 = 0 THEN '5411' ELSE '5541' END,
    'Baseline transaction — normal pattern (S2)',
    date_trunc('day', NOW() - (s * 2 + 2) * INTERVAL '1 day')
        + make_interval(hours => 8 + (s % 12)),
    date_trunc('day', NOW() - (s * 2 + 2) * INTERVAL '1 day')
        + make_interval(hours => 8 + (s % 12)) + INTERVAL '3 minutes'
FROM generate_series(1, 15) AS s;

-- S3 Carol: 20 transactions over 30 days, amounts $120–$320 (mean ~$200), wire transfers.
-- Counterparties = Alice, Dave.
INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, merchant_category, description,
    initiated_at, completed_at
)
SELECT
    gen_random_uuid(),
    'TXN-' || to_char(NOW() - (s + 2) * INTERVAL '36 hours', 'YYYYMMDD') || '-C' || lpad(s::text, 7, '0'),
    'c0000003-0001-0000-0000-000000000003'::uuid,
    CASE WHEN s % 2 = 0
        THEN 'a0000001-0001-0000-0000-000000000001'
        ELSE 'd0000004-0001-0000-0000-000000000004' END::uuid,
    'TRANSFER_DEBIT',
    (120 + (s % 13) * 16)::NUMERIC(19, 4),
    'USD', 'COMPLETED', NULL,
    'Baseline wire transfer (S3)',
    date_trunc('day', NOW() - (s + 2) * INTERVAL '36 hours')
        + make_interval(hours => 10 + (s % 8)),
    date_trunc('day', NOW() - (s + 2) * INTERVAL '36 hours')
        + make_interval(hours => 10 + (s % 8)) + INTERVAL '2 minutes'
FROM generate_series(1, 20) AS s;

-- S4 Dave: 10 transactions over 30 days, amounts $700–$1300 (mean $1000), 1 known counterparty.
INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, merchant_category, description,
    initiated_at, completed_at
)
SELECT
    gen_random_uuid(),
    'TXN-' || to_char(NOW() - (s * 3 + 2) * INTERVAL '1 day', 'YYYYMMDD') || '-D' || lpad(s::text, 7, '0'),
    'd0000004-0001-0000-0000-000000000004'::uuid,
    'a0000001-0001-0000-0000-000000000001'::uuid,
    'TRANSFER_DEBIT',
    (700 + (s % 7) * 100)::NUMERIC(19, 4),
    'USD', 'COMPLETED', NULL,
    'Baseline wire transfer (S4)',
    date_trunc('day', NOW() - (s * 3 + 2) * INTERVAL '1 day')
        + make_interval(hours => 9 + (s % 8)),
    date_trunc('day', NOW() - (s * 3 + 2) * INTERVAL '1 day')
        + make_interval(hours => 9 + (s % 8)) + INTERVAL '4 minutes'
FROM generate_series(1, 10) AS s;

-- S5 Eve: 5 transactions over 30 days, amounts $220–$380 (mean $300), 1 known counterparty.
INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, merchant_category, description,
    initiated_at, completed_at
)
SELECT
    gen_random_uuid(),
    'TXN-' || to_char(NOW() - (s * 6 + 2) * INTERVAL '1 day', 'YYYYMMDD') || '-E' || lpad(s::text, 7, '0'),
    'e0000005-0001-0000-0000-000000000005'::uuid,
    'b0000002-0001-0000-0000-000000000002'::uuid,
    'TRANSFER_DEBIT',
    (220 + (s % 9) * 20)::NUMERIC(19, 4),
    'USD', 'COMPLETED', NULL,
    'Baseline wire transfer (S5)',
    date_trunc('day', NOW() - (s * 6 + 2) * INTERVAL '1 day')
        + make_interval(hours => 10 + (s % 7)),
    date_trunc('day', NOW() - (s * 6 + 2) * INTERVAL '1 day')
        + make_interval(hours => 10 + (s % 7)) + INTERVAL '3 minutes'
FROM generate_series(1, 5) AS s;

-- ── CustomerRiskProfile — pre-computed baselines ──────────────────────────────
-- Stats are pre-computed to match the scenario spec in RISK_ENGINE_TEST_MATRIX.md.
-- amount_m2 = stddev^2 * (count - 1) — Welford's M2 accumulator.
-- typical_counterparties mirrors the counterparty_account_id values seeded above.

INSERT INTO customer_risk_profile (
    id, user_id,
    transaction_count, amount_mean, amount_m2,
    avg_transactions_per_hour, avg_transactions_per_day,
    typical_transaction_hours, typical_merchant_categories, typical_counterparties,
    current_risk_score, risk_tier, last_updated, total_transactions_analyzed
)
VALUES
    -- S1 Alice: mean=$110, stddev=$25, 30 txns, 1/day
    ('a0000001-0002-0000-0000-000000000001',
     'a0000001-0000-0000-0000-000000000001',
     30, 110.0000, 18125.00000000,
     0.042, 1.0,
     '{"9":0.10,"10":0.10,"11":0.10,"12":0.10,"13":0.10,"14":0.10,"15":0.10,"16":0.10,"17":0.10,"18":0.10}'::jsonb,
     '{"5411":0.45,"5541":0.35,"5812":0.20}'::jsonb,
     '["b0000002-0001-0000-0000-000000000002","c0000003-0001-0000-0000-000000000003"]'::jsonb,
     0.0, 'LOW', NOW(), 30),

    -- S2 Bob: mean=$100, stddev=$30, 15 txns, 0.5/day
    ('b0000002-0002-0000-0000-000000000002',
     'b0000002-0000-0000-0000-000000000002',
     15, 100.0000, 12600.00000000,
     0.021, 0.5,
     '{"8":0.083,"9":0.083,"10":0.083,"11":0.083,"12":0.083,"13":0.083,"14":0.083,"15":0.083,"16":0.083,"17":0.083,"18":0.083,"19":0.083}'::jsonb,
     '{"5411":0.50,"5541":0.50}'::jsonb,
     '["a0000001-0001-0000-0000-000000000001","c0000003-0001-0000-0000-000000000003","d0000004-0001-0000-0000-000000000004"]'::jsonb,
     0.0, 'LOW', NOW(), 15),

    -- S3 Carol: mean=$200, stddev=$80, 20 txns, 0.67/day
    ('c0000003-0002-0000-0000-000000000003',
     'c0000003-0000-0000-0000-000000000003',
     20, 200.0000, 121600.00000000,
     0.028, 0.67,
     '{"9":0.10,"10":0.10,"11":0.10,"12":0.10,"13":0.10,"14":0.10,"15":0.10,"16":0.10,"17":0.10,"18":0.10}'::jsonb,
     '{}'::jsonb,
     '["a0000001-0001-0000-0000-000000000001","d0000004-0001-0000-0000-000000000004"]'::jsonb,
     0.0, 'LOW', NOW(), 20),

    -- S4 Dave: mean=$1000, stddev=$300, 10 txns, 0.33/day
    ('d0000004-0002-0000-0000-000000000004',
     'd0000004-0000-0000-0000-000000000004',
     10, 1000.0000, 810000.00000000,
     0.014, 0.33,
     '{"9":0.111,"10":0.111,"11":0.111,"12":0.111,"13":0.111,"14":0.111,"15":0.111,"16":0.111,"17":0.111}'::jsonb,
     '{}'::jsonb,
     '["a0000001-0001-0000-0000-000000000001"]'::jsonb,
     0.0, 'LOW', NOW(), 10),

    -- S5 Eve: mean=$300, stddev=$80, 5 txns, 0.17/day
    ('e0000005-0002-0000-0000-000000000005',
     'e0000005-0000-0000-0000-000000000005',
     5, 300.0000, 25600.00000000,
     0.007, 0.17,
     '{"9":0.143,"10":0.143,"11":0.143,"12":0.143,"13":0.143,"14":0.143,"15":0.143}'::jsonb,
     '{}'::jsonb,
     '["b0000002-0001-0000-0000-000000000002"]'::jsonb,
     0.0, 'LOW', NOW(), 5);
