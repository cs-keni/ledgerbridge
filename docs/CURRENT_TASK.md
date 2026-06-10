# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-10

## Active Task

**Phase 0 — Setup**

All 3 planning gates cleared. Beginning Phase 0 implementation.

### Done
- [x] Git initialized, remote connected (`git@github.com:cs-keni/ledgerbridge.git`)
- [x] PHASES.md created
- [x] docs/ scaffolding created (AI_CONTEXT, HANDOFF, ENGINEERING_LOG, CURRENT_TASK)
- [x] gbrain project context stored
- [x] `/plan-eng-review` complete (2026-06-05) — 18 decisions locked (D2–D18), outside-voice review run, 6 TODOs written
- [x] `/plan-ceo-review` complete (2026-06-10) — SELECTIVE EXPANSION, 4 scope additions accepted, Codex outside voice, 3 new TODOs
- [x] `/plan-eng-review` fresh pass (2026-06-10) — CEO additions locked (D1–D10), Codex outside voice, 10 decisions resolved, 1 critical gap addressed (DemoDataRefreshComponent)

### In Progress (T1–T9 in `tasks-eng-review-20260606-030335.jsonl`)
- [ ] Spring Boot 3.x Maven project initialization (T1)
- [ ] Docker Compose (PostgreSQL 16 + Kafka Bitnami) (T2)
- [ ] Flyway configuration (T3)
- [ ] SpringDoc OpenAPI (Swagger UI) (T4)
- [ ] Logback structured JSON logging + correlation-ID MDC scaffold (T5)

### Blocked On
- Nothing. All planning gates cleared — proceed with T1–T9.

## Next Task

Phase 1 — Domain + Database: V1 schema migration (remember the
`transaction` → `ledger_transaction` rename from TODOS.md, plus D18 composite
indexes), JPA entities with Hypersistence Utils JSONB mapping (D5), and
Testcontainers scaffold (T6–T9).
