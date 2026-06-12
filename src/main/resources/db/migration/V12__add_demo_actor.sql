-- Add DEMO_ACTOR user for Phase 6 local testing and portfolio demo
-- Password: "password" (BCrypt hash same as seed users in V7)
INSERT INTO app_user (id, email, password_hash, first_name, last_name, role, enabled)
VALUES ('f0000006-0000-0000-0000-000000000006',
        'demo@ledgerbridge.io',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
        'Demo', 'User', 'DEMO_ACTOR', TRUE);
