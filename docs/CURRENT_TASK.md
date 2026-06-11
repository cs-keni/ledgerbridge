# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-10

## Active Task

**Phase 1 — Domain + Database**

Phase 0 is complete. Starting Phase 1.

### Phase 0 Complete ✅
- [x] Spring Boot 3.3.5 Maven project initialized
- [x] Docker Compose: PostgreSQL 16 + Bitnami Kafka 3.7 KRaft
- [x] Flyway configured (enabled, location `classpath:db/migration`)
- [x] SpringDoc OpenAPI (Swagger UI with JWT Bearer auth scheme)
- [x] Logback structured JSON (logstash-logback-encoder) + CorrelationIdFilter MDC
- [x] All 4 planning gates cleared

### Phase 1 — In Progress
- [x] **TODOS gate:** rename `transaction` → `ledger_transaction` — done 2026-06-11
- [x] **TODOS gate:** design fraud-scenario validation matrix — done 2026-06-11 (`docs/RISK_ENGINE_TEST_MATRIX.md`)
- [ ] Write Flyway migrations V1–V6 + composite indexes (D18)
- [ ] Define JPA entities (NUMERIC(19,4), UUID PKs, JSONB + Hypersistence D5)
- [ ] Write V7 seed data migration (labeled scenario patterns)
- [ ] Switch `ddl-auto=validate`, verify migrations run clean
- [ ] Add Testcontainers scaffold for Phase 2+ integration tests

### Blocked On
- Nothing. All gates cleared.
