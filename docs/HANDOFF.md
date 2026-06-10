# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-10

## Current Status

**Phase: 0 — Setup (in progress) — architecture + CEO strategy reviews COMPLETE**

Both planning gates cleared:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18), Codex outside voice, 6 TODOs
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions accepted, Codex outside voice, 3 new TODOs

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md` for the full CEO plan.

Spring Boot project has NOT been initialized yet (T1 in the task list below).
Docker Compose does NOT exist yet (T2).

**Running `/plan-eng-review` fresh pass** next — to pick up the 4 CEO review scope additions
(Swagger fraud scenarios, Railway deploy, ADRs, blog post) into the engineering review baseline.

## Last Agent Action

Claude Code (2026-06-10): Completed `/plan-ceo-review` SELECTIVE EXPANSION mode — walked all 11
review sections, ran Codex (gpt-5.5) as outside voice. Key decisions locked:
- Upstash Kafka for Railway deploy (SASL/PLAIN, not Railway-managed)
- Full-stack Railway: React admin SPA + Spring Boot on one service (classpath:/static/)
- Demo auth: V8__demo_seed.sql creates demo@ledgerbridge.io read-only admin user
- Demo seed: timestamp matrix per scenario (not count-based)
- Blog publish: after Phase 7.5 ships (live URL exists), not Phase 4 day
- Per-scenario Testcontainers tests required as Phase 4 quality gate
Updated: PHASES.md (Phase 7.5 + Phase 8 additions), TODOS.md (+3 items),
docs/designs/portfolio-strategy.md (promoted CEO plan to repo).

## What's Next

1. ~~Run `/plan-eng-review` on the full project spec before writing any code.~~ ✅ Done 2026-06-05.
2. ~~Run `/plan-ceo-review` to lock portfolio strategy.~~ ✅ Done 2026-06-10.
3. **Run `/plan-eng-review` fresh pass** to pick up CEO review scope additions into eng architecture.
4. Initialize Spring Boot 3.x project via Maven with all declared dependencies (T1).
5. Set up Docker Compose with PostgreSQL 16 + Kafka Bitnami (T2).
6. Configure Flyway, SpringDoc, Logback + correlation-ID MDC scaffold (T3–T5).
7. Write the V1 schema migration — remember the `transaction` → `ledger_transaction`
   rename (TODOS.md item, avoids the SQL reserved-word footgun) — and composite
   indexes per D18 (T6), then JPA entities with Hypersistence Utils JSONB
   mapping per D5 (T7).

## Module Ownership / Status

| Module | Status | Owner |
|---|---|---|
| auth | Not started | — |
| account | Not started | — |
| transaction | Not started | — |
| risk | Not started | — |
| audit | Not started | — |
| notification | Not started | — |
| common | Not started | — |
| frontend | Not started | — |

## Architecture Notes

Modular monolith. Package: `com.ledgerbridge`. See AI_CONTEXT.md for full architecture.

## Open Questions / Decisions Pending

- All 22 architecture/code-quality/test/performance/tension questions from
  `/plan-eng-review` are resolved (see AI_CONTEXT.md). Nothing is dangling.
- 6 design items deferred to specific later phases — tracked in `TODOS.md`,
  not open architecture questions: fraud-validation strategy & `ledger_transaction`
  rename (before Phase 1), API idempotency keys & correlation IDs (before
  Phase 3), baseline-poisoning mitigation (before Phase 4), risk-engine
  Prometheus metrics (alongside Phase 7).
- Two failure-mode gaps flagged but not yet designed: refresh-token
  reuse/rotation policy (decide before Phase 2), and `GraphPatternRule`
  traversal bounds (decide before Phase 4 — same root issue Codex flagged
  as "needs to be its own mini-system").
