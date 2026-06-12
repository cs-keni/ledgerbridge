-- V11: Add outcome column to audit_log for D15 (always-fire aspect with failure capture)
ALTER TABLE audit_log ADD COLUMN outcome VARCHAR(100);
