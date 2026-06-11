# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-11

## Active Task

**Phase 2 — Auth + Account Module** (not yet started)

### Phase 1 Complete ✅
- [x] **TODOS gate:** rename `transaction` → `ledger_transaction` — done 2026-06-11
- [x] **TODOS gate:** design fraud-scenario validation matrix — done 2026-06-11 (`docs/RISK_ENGINE_TEST_MATRIX.md`)
- [x] Write Flyway migrations V1–V6 + composite indexes (D18)
- [x] Define JPA entities (NUMERIC(19,4), UUID PKs, JSONB + Hypersistence D5) — 11 entities + 7 enums
- [x] Write V7 seed data migration (5 scenario users, 80 transactions, 5 CustomerRiskProfiles)
- [x] Switch `ddl-auto=validate`, verify migrations run clean
- [x] Add Testcontainers scaffold (`SchemaIntegrationTest`, `BaseIntegrationTest`) — 4/4 tests passing

### Phase 2 — Not Yet Started
- [ ] **TODOS gate:** decide refresh-token reuse/rotation policy (replay-detection behavior — see HANDOFF.md)
- [ ] Implement User entity, Spring Security config, JWT service
- [ ] Implement AuthController: register, login, refresh, logout
- [ ] Implement AccountService + AccountController
- [ ] Write unit tests for auth and account services

### Blocked On
- Nothing. Phase 2 is ready to start. Resolve TODOS gate (refresh-token rotation policy) first.
