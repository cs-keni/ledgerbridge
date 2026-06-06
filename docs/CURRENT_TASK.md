# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-05

## Active Task

**Phase 0 — Setup**

Completing the project scaffolding before any implementation begins.
`/plan-eng-review` is now COMPLETE — architecture locked (see AI_CONTEXT.md),
TODOS.md written, Phase 1 unblocked. Remaining Phase 0 work is pure scaffolding.

### Done
- [x] Git initialized, remote connected (`git@github.com:cs-keni/ledgerbridge.git`)
- [x] PHASES.md created
- [x] docs/ scaffolding created (AI_CONTEXT, HANDOFF, ENGINEERING_LOG, CURRENT_TASK)
- [x] gbrain project context stored
- [x] `/plan-eng-review` complete — 18 decisions locked (D2–D18), outside-voice
      review run (Codex), 4 cross-model tensions resolved (1 reversal: SSE →
      servlet `SseEmitter`), 6 TODOs written to `TODOS.md`, failure-mode audit
      complete (2 critical gaps + 1 underspecified item flagged)

### In Progress (T1–T9 in `tasks-eng-review-20260606-030335.jsonl`)
- [ ] Spring Boot 3.x Maven project initialization (T1)
- [ ] Docker Compose (PostgreSQL 16 + Kafka Bitnami) (T2)
- [ ] Flyway configuration (T3)
- [ ] SpringDoc OpenAPI (Swagger UI) (T4)
- [ ] Logback structured JSON logging + correlation-ID MDC scaffold (T5)

### Blocked On
- Nothing. Architecture review cleared — proceed with T1–T9.

## Next Task

Phase 1 — Domain + Database: V1 schema migration (remember the
`transaction` → `ledger_transaction` rename from TODOS.md, plus D18 composite
indexes), JPA entities with Hypersistence Utils JSONB mapping (D5), and
Testcontainers scaffold (T6–T9).
