# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-05

## Current Status

**Phase: 0 — Setup (in progress) — architecture review COMPLETE, Phase 1 unblocked**

Git repo initialized and connected to `git@github.com:cs-keni/ledgerbridge.git`.
PHASES.md, docs scaffolding, TODOS.md, and project brief are in place.
`/plan-eng-review` ran end-to-end (18 decisions D2–D18 locked, outside-voice
review via Codex surfaced 20 findings → 4 cross-model tensions resolved,
6 follow-up TODOs written to `TODOS.md`). **Architecture is locked — see
AI_CONTEXT.md "Key Design Decisions" for the full finalized list.**
Spring Boot project has NOT been initialized yet (T1 in the task list below).
Docker Compose does NOT exist yet (T2).

## Last Agent Action

Claude Code (2026-06-05): Completed `/plan-eng-review` — walked all 4 review
sections (architecture, code quality, tests, performance), ran Codex as
outside voice, resolved 4 cross-model tensions (one reversal: SSE moved from
WebFlux/Flux to servlet `SseEmitter` + registry), wrote `TODOS.md` with 6
deferred design items gated to specific phases, produced failure-mode audit
(2 critical gaps flagged: dual-write boundary at the DB/Kafka commit, and
unbounded graph-traversal in `GraphPatternRule`), and updated AI_CONTEXT.md
with the finalized decision list. Generated implementation task list
(T1–T9, JSONL at `~/.gstack/projects/cs-keni-ledgerbridge/tasks-eng-review-20260606-030335.jsonl`)
covering the rest of Phase 0 + start of Phase 1.

## What's Next

1. ~~Run `/plan-eng-review` on the full project spec before writing any code.~~ ✅ Done 2026-06-05.
2. Initialize Spring Boot 3.x project via Maven with all declared dependencies (T1).
3. Set up Docker Compose with PostgreSQL 16 + Kafka Bitnami (T2).
4. Configure Flyway, SpringDoc, Logback + correlation-ID MDC scaffold (T3–T5).
5. Write the V1 schema migration — remember the `transaction` → `ledger_transaction`
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
