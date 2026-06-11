-- V5: Append-only audit log
-- No FK on user_id: the log must never be prevented from recording an event
-- due to a cascade or user deletion. Append-only — no UPDATE/DELETE on this table.

CREATE TABLE audit_log (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type    VARCHAR(100) NOT NULL,
    entity_id      UUID,
    action         VARCHAR(50)  NOT NULL,
    user_id        UUID,                    -- nullable (system actions have no user)
    old_values     JSONB,
    new_values     JSONB,
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(100),
    occurred_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_entity  ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_log_user    ON audit_log (user_id, occurred_at DESC)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_log_time    ON audit_log (occurred_at DESC);
