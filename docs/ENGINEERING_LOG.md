# ENGINEERING_LOG.md — LedgerBridge

> Log every change: date, what changed, why. Add date header if missing.

---

## 2026-06-05

### Session Start
- **Agent**: Claude Code
- **Task**: Phase 0 setup — project initialization

### Changes
- Initialized git repo at `/mnt/c/dev/ledgerbridge`
- Connected remote: `git@github.com:cs-keni/ledgerbridge.git`
- Renamed default branch to `main`
- Created `PHASES.md` with all 8 phases and task checkboxes
- Created `docs/AI_CONTEXT.md` — full stack, architecture, event flow, key design decisions
- Created `docs/HANDOFF.md` — current status, module ownership, what's next
- Created `docs/ENGINEERING_LOG.md` (this file)
- Created `docs/CURRENT_TASK.md` — active task tracking
- Stored LedgerBridge project context in gbrain for cross-session continuity

### Next
Spring Boot project initialization, Docker Compose setup, Flyway config, Logback config, SpringDoc setup. Then run `/plan-eng-review` before Phase 1.

---

### Session 2 — `/plan-eng-review` (architecture lock)
- **Agent**: Claude Code
- **Task**: Run the required Phase-1 architecture gate on the full project spec

### Changes
- Walked all 4 review sections (Architecture, Code Quality, Tests, Performance)
  via interactive AskUserQuestion decision briefs — locked **17 decisions
  (D2–D18)**, each with explicit pros/cons/recommendation. Full rationale in
  `docs/AI_CONTEXT.md` "Key Design Decisions" and gbrain page
  `ledgerbridge-risk-engine-design`.
- Notable locks: idempotency keys over Transactional Outbox (D2); Kafka
  messages keyed by `userId` (D3); DB-backed refresh-token table (D4); JSONB +
  Hypersistence Utils (D5); **Welford's online algorithm over a bounded
  recent-N window** replacing the originally-considered EWMA (D6); 
  `@RetryableTopic` for DLT (D7); `RiskRuleResult` record (D9);
  `@TransactionalEventListener(AFTER_COMMIT)` (D10); renamed `@Audited` →
  **`@AuditLog`** to avoid a silent collision with Hibernate Envers' own
  `@Audited` annotation (D11); standard `ErrorResponse` record (D12);
  pessimistic row locking with fixed lock ordering (D13); lower-bound-inclusive
  score boundaries (D14); always-log-with-outcome audit policy (D15).
- Ran Codex (gpt-5.5) as an independent "outside voice" — surfaced 20
  findings. Four created genuine cross-model tensions with decisions already
  made; resolved all four via dedicated AskUserQuestion calls, including
  **one reversal**: D8 (SSE transport) flipped from Spring WebFlux/Flux to
  **servlet `SseEmitter` + connection registry** — Codex correctly flagged
  that running both reactive and servlet stacks for a single endpoint added
  real complexity with no payoff for this scope.
- Wrote `TODOS.md` — 6 deferred design items surfaced by the outside-voice
  review, each gated to a specific phase: fraud-scenario validation strategy
  & `transaction`→`ledger_transaction` rename (before Phase 1), API-level
  idempotency keys & correlation-ID propagation (before Phase 3),
  baseline-poisoning mitigation (before Phase 4), risk-engine-specific
  Prometheus metrics (alongside Phase 7).
- Produced a failure-mode audit across 8 new codepaths — flagged **2 critical
  gaps** (the DB-commit/Kafka-publish dual-write boundary with no Outbox to
  cover it; unbounded graph traversal in `GraphPatternRule`) and **1
  underspecified item** (refresh-token reuse/rotation policy — D4 picked the
  storage mechanism but never decided replay-detection behavior).
- Updated `docs/AI_CONTEXT.md`, `docs/HANDOFF.md`, `docs/CURRENT_TASK.md`, and
  `PHASES.md` to reflect all finalized decisions and TODOS-gated checkpoints
  inline at the phases where they apply.
- Generated `tasks-eng-review-20260606-030335.jsonl` (T1–T9) covering the
  remainder of Phase 0 + the start of Phase 1.
- Logged review completion via `gstack-review-log` (status `issues_open` —
  reflecting the 6 open TODOs, not unresolved architecture questions; all 22
  decision points from this review ARE resolved).

### Next
Phase 1 is unblocked. Execute T1–T9: Spring Boot init, Docker Compose,
Flyway/SpringDoc/Logback config, then the V1 schema migration (apply the
`ledger_transaction` rename + D18 composite indexes here — cheapest point to
do it), JPA entities with JSONB mapping, and the Testcontainers scaffold.
