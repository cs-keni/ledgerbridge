-- D4 (token family rotation): group tokens per login session for replay detection.
-- Replay = revoked token presented → revoke entire family, force re-login.
ALTER TABLE refresh_token ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE refresh_token ALTER COLUMN family_id DROP DEFAULT;

CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
