-- Add checking + savings accounts for the DEMO_ACTOR user so the Transfer
-- page golden path works end-to-end without Swagger setup.
-- Demo user ID: f0000006-0000-0000-0000-000000000006 (set in V12)

INSERT INTO account (id, account_number, user_id, type, status, balance, opened_at)
VALUES
    ('f0000006-0001-0000-0000-000000000006',
     '000000000001',
     'f0000006-0000-0000-0000-000000000006',
     'CHECKING', 'ACTIVE', 10000.0000,
     NOW() - INTERVAL '90 days'),

    ('f0000006-0002-0000-0000-000000000006',
     '000000000002',
     'f0000006-0000-0000-0000-000000000006',
     'SAVINGS', 'ACTIVE', 25000.0000,
     NOW() - INTERVAL '90 days');

-- Risk profile so the engine has a baseline for demo transactions
INSERT INTO customer_risk_profile (
    id, user_id,
    transaction_count, amount_mean, amount_m2,
    avg_transactions_per_hour, avg_transactions_per_day,
    typical_counterparties, last_updated
) VALUES (
    gen_random_uuid(),
    'f0000006-0000-0000-0000-000000000006',
    20, 500.0, 50000.0,
    0.5, 2.0,
    '[]',
    NOW()
) ON CONFLICT (user_id) DO NOTHING;
