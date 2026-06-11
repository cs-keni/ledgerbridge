# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-10

## Current Status

**Phase: 0 — Setup (in progress) — all planning gates CLEAR (including Design)**

All 4 planning gates cleared:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18), Codex outside voice, 6 TODOs
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions accepted, Codex outside voice, 3 new TODOs
- `/plan-eng-review` (CEO additions) ✅ Done 2026-06-10: 9 issues found, all resolved, D1–D10 locked (Maven Frontend Plugin, SPA fallback + Swagger exclusions, NormalDeposit negative test, DemoDataRefreshComponent, profile-gated seed, DEMO_ACTOR role, liveness probe, Milestone Rule confirmed, ADR count confirmed)
- `/plan-design-review` ✅ Done 2026-06-10: `DESIGN.md` created, 11 decisions locked, score 2/10 → 8/10

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md` for the full CEO plan.
**Design system locked** — see `DESIGN.md` for all Phase 6 component specs.

Spring Boot project has NOT been initialized yet (T1 in Phase 0 task list).
Docker Compose does NOT exist yet (T2).

## Last Agent Action

Claude Code (2026-06-10): Completed `/plan-design-review` — full 7-pass design review for Phase 6
frontend. Ran 11 AskUserQuestion decisions (D1–D11), ran Codex (gpt-5.5) as outside design voice.
Created `DESIGN.md` (comprehensive design system — color tokens, typography, animation system,
component specs). Score raised: 2/10 → 8/10. Key decisions locked:
- D3: Sidebar + workspace layout (sortable alert table, NOT stacked card mosaic — hard rejection risk eliminated)
- D4: DEMO_ACTOR default route → `/admin/alerts` (risk engine is first thing recruiter sees, not a dashboard)
- D5: Demo-aware empty state with Swagger link ("No risk alerts yet. Trigger a fraud scenario in Swagger UI →")
- D6: SSE reconnecting indicator (orange pulsing dot + "Reconnecting..." tooltip — non-intrusive)
- D7: RiskGauge loading: skeleton arc shimmer; null score: `—` + tooltip
- D8: `Try a Demo Scenario →` button in alert queue header → opens /swagger-ui.html in new tab
- D9: SeverityBadge: filled dark chip (#1e1e1e bg, #3a3a3a border) + semantic dot (Datadog/PagerDuty style)
- D10: WCAG 2.1 AA: 4.5:1 contrast, keyboard nav (Tab/Enter/Escape), ARIA landmarks
- D11: RiskGauge: radial arc 270°, 0.4 dotted threshold marker, 4 rule contribution bars, spring 600ms
Updated: DESIGN.md (created), PHASES.md (Phase 6 rewritten with full component specs), TODOS.md
(design gate marked done), docs/designs/portfolio-strategy.md (GSTACK REVIEW REPORT: Design Review
row added — 1 run, CLEAR, 11 decisions).

## What's Next

1. ~~Run `/plan-eng-review` on the full project spec before writing any code.~~ ✅ Done 2026-06-05.
2. ~~Run `/plan-ceo-review` to lock portfolio strategy.~~ ✅ Done 2026-06-10.
3. ~~Run `/plan-eng-review` fresh pass to pick up CEO review scope additions.~~ ✅ Done 2026-06-10.
4. ~~Run `/plan-design-review` before Phase 6 frontend work.~~ ✅ Done 2026-06-10.
5. **Initialize Spring Boot 3.x project via Maven with all declared dependencies (T1).**
6. Set up Docker Compose with PostgreSQL 16 + Kafka Bitnami (T2).
7. Configure Flyway, SpringDoc, Logback + correlation-ID MDC scaffold (T3–T5).
8. Write the V1 schema migration — remember the `transaction` → `ledger_transaction`
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
