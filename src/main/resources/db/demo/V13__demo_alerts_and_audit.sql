-- V13: Pre-seed trigger transactions, risk alerts, notifications, and audit log
-- for the portfolio demo. Only runs when SPRING_PROFILES_ACTIVE=demo.
--
-- DemoDataRefreshComponent.java refreshes timestamps on every app startup so
-- alerts always appear "recent" regardless of when the Supabase project was created.

-- ── Trigger transactions (fixed UUIDs so alerts can reference them) ───────────

INSERT INTO ledger_transaction (
    id, transaction_number, account_id, counterparty_account_id,
    type, amount, currency, status, description, initiated_at, completed_at
) VALUES
    -- Eve: round-trip trigger
    ('ee100001-0000-0000-0000-000000000001',
     'DEMO-TXN-EVE-001',
     'e0000005-0001-0000-0000-000000000005',
     'b0000002-0001-0000-0000-000000000002',
     'TRANSFER_DEBIT', 4800.00, 'USD', 'COMPLETED',
     'Wire transfer to counterparty',
     NOW() - INTERVAL '25 minutes',
     NOW() - INTERVAL '24 minutes'),

    -- Carol: large amount trigger
    ('cc100001-0000-0000-0000-000000000001',
     'DEMO-TXN-CAROL-001',
     'c0000003-0001-0000-0000-000000000003',
     NULL,
     'WITHDRAWAL', 12500.00, 'USD', 'COMPLETED',
     'Large withdrawal',
     NOW() - INTERVAL '1 hour 12 minutes',
     NOW() - INTERVAL '1 hour 11 minutes'),

    -- Bob: velocity trigger (most recent in the burst)
    ('bb100001-0000-0000-0000-000000000001',
     'DEMO-TXN-BOB-001',
     'b0000002-0001-0000-0000-000000000002',
     'a0000001-0001-0000-0000-000000000001',
     'TRANSFER_DEBIT', 115.00, 'USD', 'COMPLETED',
     'Transfer (velocity burst)',
     NOW() - INTERVAL '3 hours',
     NOW() - INTERVAL '2 hours 59 minutes'),

    -- Dave: fan-out trigger
    ('dd100001-0000-0000-0000-000000000001',
     'DEMO-TXN-DAVE-001',
     'd0000004-0001-0000-0000-000000000004',
     'e0000005-0001-0000-0000-000000000005',
     'TRANSFER_DEBIT', 850.00, 'USD', 'COMPLETED',
     'Transfer to 6th distinct counterparty',
     NOW() - INTERVAL '5 hours',
     NOW() - INTERVAL '4 hours 59 minutes'),

    -- Alice: unusual-hour trigger (resolved)
    ('aa100001-0000-0000-0000-000000000001',
     'DEMO-TXN-ALICE-001',
     'a0000001-0001-0000-0000-000000000001',
     NULL,
     'WITHDRAWAL', 95.00, 'USD', 'COMPLETED',
     'Late-night withdrawal (3:14 AM)',
     NOW() - INTERVAL '18 hours',
     NOW() - INTERVAL '17 hours 59 minutes'),

    -- Alice: marginal amount trigger (dismissed)
    ('aa100002-0000-0000-0000-000000000002',
     'DEMO-TXN-ALICE-002',
     'a0000001-0001-0000-0000-000000000001',
     'b0000002-0001-0000-0000-000000000002',
     'TRANSFER_DEBIT', 155.00, 'USD', 'COMPLETED',
     'Transfer slightly above baseline',
     NOW() - INTERVAL '2 days',
     NOW() - INTERVAL '2 days');

-- ── Risk Alerts ───────────────────────────────────────────────────────────────

INSERT INTO risk_alert (
    id, alert_number, transaction_id, user_id,
    alert_type, severity, status,
    risk_score, rule_details,
    reviewed_by_admin_id, admin_notes, reviewed_at, created_at
) VALUES
    -- CRITICAL open: Eve round-trip fraud
    ('ee000001-0000-0000-0000-000000000001',
     'ALERT-DEMO-0001',
     'ee100001-0000-0000-0000-000000000001',
     'e0000005-0000-0000-0000-000000000005',
     'GRAPH_PATTERN', 'CRITICAL', 'OPEN',
     0.91,
     '{"amountAnomaly":0.52,"velocityAnomaly":0.61,"behavioralAnomaly":0.48,"graphPattern":0.95,"fanInCount":3,"fanOutCount":4,"roundTripWindowMinutes":87}',
     NULL, NULL, NULL,
     NOW() - INTERVAL '25 minutes'),

    -- HIGH open: Carol large amount
    ('cc000001-0000-0000-0000-000000000001',
     'ALERT-DEMO-0002',
     'cc100001-0000-0000-0000-000000000001',
     'c0000003-0000-0000-0000-000000000003',
     'AMOUNT_ANOMALY', 'HIGH', 'OPEN',
     0.78,
     '{"amountAnomaly":0.88,"velocityAnomaly":0.12,"behavioralAnomaly":0.20,"graphPattern":0.05,"transactionAmount":12500.00,"baselineMean":200.00,"zScore":4.22}',
     NULL, NULL, NULL,
     NOW() - INTERVAL '1 hour 12 minutes'),

    -- HIGH under review: Bob velocity spike
    ('bb000001-0000-0000-0000-000000000001',
     'ALERT-DEMO-0003',
     'bb100001-0000-0000-0000-000000000001',
     'b0000002-0000-0000-0000-000000000002',
     'VELOCITY_ANOMALY', 'HIGH', 'UNDER_REVIEW',
     0.72,
     '{"amountAnomaly":0.10,"velocityAnomaly":0.85,"behavioralAnomaly":0.30,"graphPattern":0.05,"txnCount1h":8,"dailyBaseline":0.5,"velocityRatio":8.0}',
     'f0000006-0000-0000-0000-000000000006',
     'Reviewing account history — possible account takeover. Escalating to compliance.',
     NOW() - INTERVAL '2 hours 45 minutes',
     NOW() - INTERVAL '3 hours'),

    -- MEDIUM open: Dave fan-out
    ('dd000001-0000-0000-0000-000000000001',
     'ALERT-DEMO-0004',
     'dd100001-0000-0000-0000-000000000001',
     'd0000004-0000-0000-0000-000000000004',
     'GRAPH_PATTERN', 'MEDIUM', 'OPEN',
     0.54,
     '{"amountAnomaly":0.10,"velocityAnomaly":0.20,"behavioralAnomaly":0.15,"graphPattern":0.65,"distinctRecipients":6,"windowHours":4}',
     NULL, NULL, NULL,
     NOW() - INTERVAL '5 hours'),

    -- MEDIUM resolved: Alice behavioral anomaly
    ('aa000001-0000-0000-0000-000000000001',
     'ALERT-DEMO-0005',
     'aa100001-0000-0000-0000-000000000001',
     'a0000001-0000-0000-0000-000000000001',
     'BEHAVIORAL_ANOMALY', 'MEDIUM', 'RESOLVED',
     0.48,
     '{"amountAnomaly":0.05,"velocityAnomaly":0.08,"behavioralAnomaly":0.62,"graphPattern":0.02,"transactionHour":3,"typicalHourRange":[9,18]}',
     'f0000006-0000-0000-0000-000000000006',
     'Confirmed legitimate — customer travelling internationally.',
     NOW() - INTERVAL '17 hours',
     NOW() - INTERVAL '18 hours'),

    -- LOW dismissed: Alice marginal amount
    ('aa000002-0000-0000-0000-000000000002',
     'ALERT-DEMO-0006',
     'aa100002-0000-0000-0000-000000000002',
     'a0000001-0000-0000-0000-000000000001',
     'AMOUNT_ANOMALY', 'LOW', 'DISMISSED',
     0.31,
     '{"amountAnomaly":0.38,"velocityAnomaly":0.05,"behavioralAnomaly":0.10,"graphPattern":0.02,"transactionAmount":155.00,"baselineMean":110.00,"zScore":1.80}',
     'f0000006-0000-0000-0000-000000000006',
     'Within normal variance. Dismissed.',
     NOW() - INTERVAL '1 day 22 hours',
     NOW() - INTERVAL '2 days');

-- ── Notifications ─────────────────────────────────────────────────────────────

INSERT INTO notification (id, user_id, type, title, body, read_at, created_at)
VALUES
    ('0b000001-0000-0000-0000-000000000001',
     'f0000006-0000-0000-0000-000000000006',
     'RISK_ALERT',
     'CRITICAL alert: Eve Santos',
     'Round-trip transfer pattern detected — risk score 0.91. Immediate review required.',
     NULL,
     NOW() - INTERVAL '25 minutes'),

    ('0b000002-0000-0000-0000-000000000002',
     'f0000006-0000-0000-0000-000000000006',
     'RISK_ALERT',
     'HIGH alert: Carol HighAmount',
     'Transaction 4.2σ above customer baseline — $12,500 transfer flagged.',
     NULL,
     NOW() - INTERVAL '1 hour 12 minutes'),

    ('0b000003-0000-0000-0000-000000000003',
     'f0000006-0000-0000-0000-000000000006',
     'RISK_ALERT',
     'Alert resolved: Alice Normal',
     'Behavioral anomaly RESOLVED — customer confirmed travelling internationally.',
     NOW() - INTERVAL '16 hours',
     NOW() - INTERVAL '17 hours 45 minutes');

-- ── Audit Log ─────────────────────────────────────────────────────────────────

INSERT INTO audit_log (
    id, entity_type, entity_id, action, user_id,
    new_values, outcome, occurred_at
) VALUES
    ('0a000001-0000-0000-0000-000000000001',
     'RiskAlert',
     'bb000001-0000-0000-0000-000000000001',
     'ALERT_REVIEWED',
     'f0000006-0000-0000-0000-000000000006',
     '{"alertId":"bb000001-0000-0000-0000-000000000001","newStatus":"UNDER_REVIEW","note":"Reviewing account history — possible account takeover. Escalating to compliance."}',
     'SUCCESS',
     NOW() - INTERVAL '2 hours 45 minutes'),

    ('0a000002-0000-0000-0000-000000000002',
     'RiskAlert',
     'aa000001-0000-0000-0000-000000000001',
     'ALERT_REVIEWED',
     'f0000006-0000-0000-0000-000000000006',
     '{"alertId":"aa000001-0000-0000-0000-000000000001","newStatus":"RESOLVED","note":"Confirmed legitimate — customer travelling internationally."}',
     'SUCCESS',
     NOW() - INTERVAL '17 hours'),

    ('0a000003-0000-0000-0000-000000000003',
     'RiskAlert',
     'aa000002-0000-0000-0000-000000000002',
     'ALERT_REVIEWED',
     'f0000006-0000-0000-0000-000000000006',
     '{"alertId":"aa000002-0000-0000-0000-000000000002","newStatus":"DISMISSED","note":"Within normal variance. Dismissed."}',
     'SUCCESS',
     NOW() - INTERVAL '1 day 22 hours');
