# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-10

## Current Status

**Phase: 0 — Setup (in progress) — all planning gates CLEAR**

All 3 planning gates cleared:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18), Codex outside voice, 6 TODOs
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions accepted, Codex outside voice, 3 new TODOs
- `/plan-eng-review` (CEO additions) ✅ Done 2026-06-10: 9 issues found, all resolved, D1–D10 locked (Maven Frontend Plugin, SPA fallback + Swagger exclusions, NormalDeposit negative test, DemoDataRefreshComponent, profile-gated seed, DEMO_ACTOR role, liveness probe, Milestone Rule confirmed, ADR count confirmed)

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md` for the full CEO plan.

Spring Boot project has NOT been initialized yet (T1 in Phase 0 task list).
Docker Compose does NOT exist yet (T2).

## Last Agent Action

Claude Code (2026-06-10): Completed `/plan-eng-review` fresh pass on CEO additions — walked all 4
review sections, ran Codex (gpt-5.5) as outside voice, resolved 10 AskUserQuestion decisions (D1–D10).
Key decisions locked in this pass:
- D1: Maven Frontend Plugin (React compiles via `mvn package`, not multi-stage Dockerfile)
- D2+D8: SpaFallbackController excludes /api/**, /actuator/**, /swagger-ui/**, /v3/api-docs/**, /webjars/**
- D3: NormalDeposit scenario test is a negative control (score < 0.4, no alert)
- D4: DemoDataRefreshComponent (profile=demo) refreshes seed timestamps on boot — demo never goes stale
- D5: V8__demo_seed.sql profile-gated to `demo` Spring profile (resources/db/demo/)
- D6: DEMO_ACTOR role — POST /api/transactions + GET /api/admin/** (not read-only, not full admin)
- D7: Railway health check: /actuator/health/liveness (not /actuator/health — prevents Kafka readiness flapping)
- D8: SPA fallback Swagger path exclusion (Swagger paths must be added to exclusion list)
- D9: Milestone Rule stays at Phase 4 ship day (confirmed: risk engine is the differentiator)
- D10: ADR count stays at 10-15 (each must be substantive — alternatives + consequences)
- T1 (TODOS): Upstash Kafka SASL/PLAIN spike before Phase 7.5
Updated: PHASES.md (D1–D8 added to Phase 7.5, D3 corrected in Phase 4), TODOS.md (+2 items: Upstash spike + V8 seed spec updated with D4-D6 decisions), docs/designs/portfolio-strategy.md (GSTACK REVIEW REPORT updated to Eng Review run 2).

## What's Next

1. ~~Run `/plan-eng-review` on the full project spec before writing any code.~~ ✅ Done 2026-06-05.
2. ~~Run `/plan-ceo-review` to lock portfolio strategy.~~ ✅ Done 2026-06-10.
3. ~~Run `/plan-eng-review` fresh pass to pick up CEO review scope additions.~~ ✅ Done 2026-06-10.
4. **Initialize Spring Boot 3.x project via Maven with all declared dependencies (T1).**
5. Set up Docker Compose with PostgreSQL 16 + Kafka Bitnami (T2).
6. Configure Flyway, SpringDoc, Logback + correlation-ID MDC scaffold (T3–T5).
7. Write the V1 schema migration — remember the `transaction` → `ledger_transaction`
   rename (TODOS.md item, avoids the SQL reserved-word footgun) — and composite
   indexes per D18/eng-review (T6), then JPA entities with Hypersistence Utils JSONB
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
