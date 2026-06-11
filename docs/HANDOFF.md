# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-10

## Current Status

**Phase: 0 — Setup COMPLETE — Phase 1 is next**

All 4 planning gates cleared:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18), Codex outside voice, 6 TODOs
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions accepted, Codex outside voice, 3 new TODOs
- `/plan-eng-review` (CEO additions) ✅ Done 2026-06-10: 9 issues found, all resolved, D1–D10 locked (Maven Frontend Plugin, SPA fallback + Swagger exclusions, NormalDeposit negative test, DemoDataRefreshComponent, profile-gated seed, DEMO_ACTOR role, liveness probe, Milestone Rule confirmed, ADR count confirmed)
- `/plan-design-review` ✅ Done 2026-06-10: `DESIGN.md` created, 11 decisions locked, score 2/10 → 8/10

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md` for the full CEO plan.
**Design system locked** — see `DESIGN.md` for all Phase 6 component specs.

Spring Boot project initialized. Docker Compose live. Flyway configured. Phase 0 fully shipped.

## Last Agent Action

Claude Code (2026-06-10): Completed Phase 0 implementation. Created all scaffold files:
- `pom.xml`: Spring Boot 3.3.5, Java 21, all declared deps (JPA, Security, Kafka, JWT/JJWT, Flyway, SpringDoc, Hypersistence, logstash-logback-encoder, Testcontainers)
- `LedgerBridgeApplication.java`, Maven wrapper (`mvnw`/`.mvn/`)
- `common/filter/CorrelationIdFilter.java`: X-Correlation-ID → MDC on every request
- `common/config/OpenApiConfig.java`: Bearer JWT auth scheme in Swagger UI
- `application.properties`: full config (DB, JPA, Flyway, Kafka, Actuator, SpringDoc, JWT); virtual threads enabled
- `application-demo.properties`: adds `classpath:db/demo` Flyway location (demo profile, decision D5)
- `logback-spring.xml`: JSON via logstash-logback-encoder in prod; human-readable in `dev` profile
- `docker-compose.yml`: PostgreSQL 16-alpine + Bitnami Kafka 3.7 KRaft; dual-listener (internal kafka:9092, external localhost:9094); health checks; `api` service scaffolded but commented until Phase 7.5
- `.env.example`, `.gitignore`
- `db/migration/.gitkeep` (Flyway location placeholder)
Compilation verified (project structure valid). Maven wrapper generated.

## Previous Agent Action (2026-06-10): `/plan-design-review` — full 7-pass design review for Phase 6
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

**Phase 1 — Domain + Database**

1. TODOS gate: rename `transaction` table → `ledger_transaction` (TODOS.md — avoids SQL reserved-word collision)
2. TODOS gate: design fraud-scenario validation matrix (TODOS.md — drives Phase 4 test assertions)
3. Write Flyway migrations V1–V6 (users/auth, accounts, ledger_transaction, risk tables, audit_log, notifications + composite indexes per D18)
4. Define all JPA entities with proper types (NUMERIC(19,4), UUID PKs, JSONB + Hypersistence for CustomerRiskProfile — D5)
5. Write seed data migration V7 (labeled scenario data — high-velocity, unusual-amount, fan-out, normal baseline)
6. Switch `spring.jpa.hibernate.ddl-auto=validate` and verify migrations run clean
7. Add Testcontainers scaffold (`@Testcontainers` base class for future integration tests)

## Module Ownership / Status

| Module | Status | Files |
|---|---|---|
| auth | Not started | — |
| account | Not started | — |
| transaction | Not started | — |
| risk | Not started | — |
| audit | Not started | — |
| notification | Not started | — |
| common | **Scaffold only** | `filter/CorrelationIdFilter.java`, `config/OpenApiConfig.java` |
| frontend | Not started | — |

## Architecture Notes

Modular monolith. Package: `com.ledgerbridge`. See `AI_CONTEXT.md` for full architecture + all locked decisions.

**Critical naming**: the transaction table is `ledger_transaction` (not `transaction` — SQL reserved word). Entity annotation: `@Table(name = "ledger_transaction")`. Migration: `V3__create_ledger_transaction.sql`.

## Open Design Work (Decisions Pending)

All architecture questions from the three plan reviews are resolved. What remains are **design-work TODOS** gated to specific phases — not unresolved architecture:

| Item | Gate | Notes |
|---|---|---|
| `ledger_transaction` rename | ~~Before Phase 1~~ | ✅ **Done 2026-06-11** — entity `LedgerTransaction`, migration `V3__create_ledger_transaction.sql` |
| Fraud-scenario validation matrix | ~~Before Phase 1~~ | ✅ **Done 2026-06-11** — `docs/RISK_ENGINE_TEST_MATRIX.md`; multi-rule escalation locked |
| Refresh-token reuse/rotation policy | Before Phase 2 | D4 picked storage; replay-detection behavior undecided |
| API-level idempotency keys | Before Phase 3 | `Idempotency-Key` header on POST /transactions; different from D2 (consumer dedupe) |
| Correlation IDs / trace propagation | Before Phase 3 | CorrelationIdFilter is wired; MDC propagation through Kafka headers needs Phase 3 work |
| Baseline poisoning mitigation | Before Phase 4 | Exclude alerted txns from Welford's update? Hold until review? |
| `GraphPatternRule` traversal bounds | Before Phase 4 | Max hops, time window, counterparty-count ceiling — must be its own mini-design |
| Risk-engine Prometheus metrics | Alongside Phase 7 | Instrument during Phase 4 so metrics land with the rule code |
| JVM flag validation | Before Phase 7.5 | Confirm -Xmx200m -Xms64m -XX:+UseSerialGC fits Railway 512MB |
| V8 demo seed timestamp matrix | Before Phase 7.5 | Depends on Phase 4 fraud-scenario validation matrix |
| Upstash Kafka SASL/PLAIN spike | Before Phase 7.5 | Prove connectivity before building Railway deploy around it |

See `TODOS.md` for full detail on each item.
