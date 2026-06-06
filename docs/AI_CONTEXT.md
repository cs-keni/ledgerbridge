# AI_CONTEXT.md — LedgerBridge

> Read this before making any architectural or implementation decisions.
> Last updated: 2026-06-05 — architecture locked via /plan-eng-review

## What This Project Is

LedgerBridge is a banking transaction and risk monitoring system built as a portfolio project for Kenny Nguyen (CS grad, University of Oregon 2025) targeting fintech/enterprise backend roles at Wells Fargo, Citi, Capital One, Chase/JPMorgan, and US Bank.

The key differentiator is the **statistical risk detection engine** — not threshold rules, but Z-score anomaly detection, sliding-window velocity analysis, behavioral baselining, and graph pattern detection. This must read like production fintech code to a senior engineer, not a tutorial project.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT (JJWT) |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Message Broker | Apache Kafka (Bitnami image) |
| Kafka Client | Spring Kafka |
| Frontend | React 18 + TypeScript + Tailwind CSS + React Query + Zustand |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Build Tool | Maven |
| Logging | SLF4J + Logback (structured JSON) |

## Architecture: Modular Monolith

Clean module boundaries designed as if extractable to microservices. Modules:
- `account` — account lifecycle, balance management
- `transaction` — transaction/transfer processing, Kafka event publishing
- `risk` — Kafka consumer, risk engine, alert management
- `audit` — append-only audit log, AOP-based auto-logging
- `notification` — alert delivery queue
- `auth` — JWT auth, user management
- `common` — SecurityConfig, KafkaConfig, GlobalExceptionHandler, AuditAspect

## Event Flow

```
User → TransactionService → [DB commit] → @TransactionalEventListener(AFTER_COMMIT)
                                               → TransactionEventProducer → Kafka "transaction-events"
                                                          (keyed by userId — see D3 below)
                                                                         ↓
                                                            TransactionRiskConsumer
                                                            (idempotency-key dedupe, D2)
                                                                         ↓
                                                                    RiskEngine
                                                            (4 rules, weighted score)
                                                                         ↓
                                                    [score ≥ 0.4] → RiskAlert → SSE registry → Admin
                                                    [conditionally] → update CustomerRiskProfile baseline
                                                       (alerted txns excluded — see TODOS.md "baseline poisoning")
```

## Key Design Decisions (locked via `/plan-eng-review`, 2026-06-05)

Foundational (unchanged from initial brief):
1. **NUMERIC(19,4) for all monetary values** — never double/float. Financial precision is non-negotiable.
2. **UUIDs for all primary keys** — avoids ID enumeration attacks, better for distributed patterns.
3. **Audit log has no FK constraints** — append-only, survives entity deletion.
4. **Testcontainers** — integration tests use real PostgreSQL + Kafka, no mocks for infrastructure.
5. **Maven** over Gradle — enterprise Java convention, consistency.

Locked in this review (D2–D18 — see `gbrain` page `ledgerbridge-risk-engine-design` for full rationale):
6. **Idempotency keys on the Kafka consumer** (D2) — not Transactional Outbox; dedupes redelivered messages. *Known accepted gap:* this does NOT cover the DB-commit-succeeds-but-publish-never-fires window (no Outbox); see `TODOS.md`.
7. **Kafka messages keyed by `userId`** (D3) — keeps one customer's events ordered for risk-profile consistency; accept *approximate* ordering during `@RetryableTopic` retries (Tension 2 — retries can land on a different partition).
8. **DB-backed refresh token table**, 7-day, revocable (D4) — logout must actually invalidate sessions; reuse-detection policy still TBD (see Failure Modes in the review).
9. **PostgreSQL JSONB + Hypersistence Utils** for `CustomerRiskProfile`'s typed JSON fields (D5).
10. **Welford's online algorithm over a bounded recent-N window** for rolling mean/variance (D6, refined by Tension 1) — numerically stable, replaces the originally-considered EWMA; resists drift from very old data without the poisoning risk a naive cumulative average has.
11. **`@RetryableTopic`** for retry/DLT handling — 4 retries with backoff, then dead-letter topic (D7).
12. **SSE via servlet `SseEmitter` + a connection registry** (D8, reversed from an initial WebFlux/Flux choice via Tension 3 — outside-voice review flagged that running both reactive and servlet stacks for one endpoint added complexity without payoff).
13. **`RiskRuleResult` record** as the standard return type for all `RiskRule` implementations — score + contributing factors (D9).
14. **`@TransactionalEventListener(phase = AFTER_COMMIT)`** for Kafka publishing — never publish before the DB transaction lands (D10).
15. **`@AuditLog`** (renamed from `@Audited` to avoid a silent collision with Hibernate Envers' `@Audited` — same simple name, different package) — AOP-based audit logging that fires even on failure, with an explicit `outcome` field (D11, D15).
16. **Standard `ErrorResponse` record** for all API error contracts (D12).
17. **Pessimistic row locking (`SELECT FOR UPDATE`) with fixed lock ordering** on transfers — textbook overdraft + deadlock prevention (D13).
18. **Score boundaries are lower-bound inclusive everywhere** — e.g. z ≥ 2.0 means "moderate," not "just under high" (D14).
19. **Behavioral baseline reuses `profile.typicalCounterparties`** rather than a separate counterparty-tracking structure (D16); velocity checks batch into a **single conditional-aggregation query** (D17); **composite indexes added via an explicit migration** for these query patterns (D18).

> **Tradeoffs accepted knowingly** (see "NOT in scope" in the `/plan-eng-review` output): no Transactional Outbox (dual-write gap at the DB/Kafka boundary — needs a README callout as a known limitation); approximate message ordering during retries (mitigated, not eliminated, by idempotency keys).
>
> **Open design work before later phases** — see `TODOS.md`: fraud-scenario validation strategy (before Phase 1 seed data), `transaction` → `ledger_transaction` rename (before Phase 1 schema), API-level idempotency keys + correlation IDs (before Phase 3), baseline-poisoning mitigation + graph-traversal bounds (before Phase 4), risk-engine Prometheus metrics (alongside Phase 7).

## Risk Engine Weights

```
AmountAnomalyRule    × 0.25
VelocityRule         × 0.30
BehavioralBaseline   × 0.20
GraphPatternRule     × 0.25

Escalation: if any single rule ≥ 0.8, minimum total = 0.65
Alert threshold: total ≥ 0.4
```

## GitHub Repo

`git@github.com:cs-keni/ledgerbridge.git`

## Agent Notes

- Both Claude Code and Codex work on this repo. Read HANDOFF.md for last known state.
- Run `/plan-eng-review` before Phase 1 (DB schema), Phase 4 (risk engine), Phase 6 (frontend).
- Run `/review` before marking Phase 4 complete.
- Never use double/float for money — BigDecimal everywhere.
- No business logic in controllers or Kafka consumers.
- All service methods that mutate state are @Transactional.
