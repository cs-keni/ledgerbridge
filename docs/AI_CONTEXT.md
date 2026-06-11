# AI_CONTEXT.md — LedgerBridge

> **Read this first.** All architectural and implementation decisions live here.
> Last updated: 2026-06-10 (Phase 0 complete)

---

## Project State

**Phase 0 COMPLETE. Phase 1 is next.**

| Gate | Status | Date |
|---|---|---|
| `/plan-eng-review` (original) | ✅ CLEAR | 2026-06-05 |
| `/plan-ceo-review` | ✅ CLEAR | 2026-06-10 |
| `/plan-eng-review` (CEO additions) | ✅ CLEAR | 2026-06-10 |
| `/plan-design-review` | ✅ CLEAR | 2026-06-10 |
| Phase 0 implementation | ✅ SHIPPED | 2026-06-10 |

Portfolio strategy: `docs/designs/portfolio-strategy.md`
Design system: `DESIGN.md`

---

## What This Project Is

LedgerBridge is a banking transaction and risk monitoring system built as a portfolio project for Kenny Nguyen (CS grad, University of Oregon 2025) targeting fintech/enterprise backend roles at Wells Fargo, Citi, Capital One, Chase/JPMorgan, and US Bank.

The key differentiator is the **statistical risk detection engine** — not threshold rules, but Z-score anomaly detection, sliding-window velocity analysis, behavioral baselining, and graph pattern detection. This must read like production fintech code to a senior engineer.

The **live Railway demo** (Phase 7.5) is a visual portfolio centerpiece. A recruiter visits a URL, logs in with demo credentials, and immediately sees the risk engine in action. The demo loop: trigger a fraud scenario in Swagger UI → watch the alert appear in the React admin dashboard in real time via SSE. No account creation required.

---

## Stack (exact versions)

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Security | Spring Security + JWT | JJWT 0.12.5 |
| ORM | Spring Data JPA + Hibernate | managed by Boot |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | managed by Boot |
| Message Broker | Apache Kafka (Bitnami KRaft image) | 3.7 |
| Kafka Client | Spring Kafka | managed by Boot |
| Frontend | React 18 + TypeScript + Tailwind CSS + React Query + Zustand | Phase 6 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.5.0 |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers | TC BOM 1.19.8 |
| Containerization | Docker + Docker Compose | — |
| CI/CD | GitHub Actions | Phase 7 |
| Build Tool | Maven | 3.9.6 (wrapper) |
| Logging | SLF4J + Logback + logstash-logback-encoder | 7.4 |
| JSONB mapping | Hypersistence Utils | 3.7.7 |
| Virtual threads | Spring Boot `spring.threads.virtual.enabled=true` | Java 21 |

---

## Architecture: Modular Monolith

Clean module boundaries, designed as if extractable to microservices. Package root: `com.ledgerbridge`.

```
com.ledgerbridge/
├── account/
│   ├── controller/   AccountController
│   ├── service/      AccountService
│   ├── repository/   AccountRepository
│   ├── dto/
│   └── model/        Account
├── transaction/
│   ├── controller/   TransactionController
│   ├── service/      TransactionService
│   ├── repository/   TransactionRepository
│   ├── kafka/        TransactionEventProducer
│   ├── dto/
│   └── model/        LedgerTransaction, TransactionEvent  ← table: ledger_transaction (not `transaction`)
├── risk/
│   ├── consumer/     TransactionRiskConsumer
│   ├── engine/       RiskEngine
│   ├── rules/        AmountAnomalyRule, VelocityRule, BehavioralBaselineRule, GraphPatternRule
│   ├── service/      AlertService
│   ├── repository/   AlertRepository, CustomerProfileRepository
│   ├── dto/
│   └── model/        RiskAlert, CustomerRiskProfile
├── audit/
│   ├── service/      AuditService
│   ├── repository/   AuditLogRepository
│   └── model/        AuditLog
├── notification/
│   ├── service/      NotificationService
│   └── model/        Notification
├── auth/
│   ├── controller/   AuthController
│   ├── service/      AuthService, JwtService
│   └── model/        User, Role  ← Role includes DEMO_ACTOR (see decisions)
└── common/
    ├── config/       SecurityConfig, KafkaConfig, OpenApiConfig ← exists
    ├── exception/    GlobalExceptionHandler, ErrorResponse record
    ├── filter/       CorrelationIdFilter ← exists
    └── audit/        AuditAspect, @AuditLog annotation
```

**What exists right now (Phase 0):**
- `common/filter/CorrelationIdFilter.java` ✅
- `common/config/OpenApiConfig.java` ✅
- `LedgerBridgeApplication.java` ✅

---

## Event Flow

```
User → TransactionService → [DB commit] → @TransactionalEventListener(AFTER_COMMIT)
                                               → TransactionEventProducer → Kafka "transaction-events"
                                                          (keyed by userId — D3)
                                                                         ↓
                                                            TransactionRiskConsumer
                                                            (@RetryableTopic, idempotency-key dedupe)
                                                                         ↓
                                                                    RiskEngine
                                                            (4 rules, weighted score)
                                                                         ↓
                                                    [score ≥ 0.4] → RiskAlert → SSE registry → Admin
                                                    [score < 0.4, not alerted] → update CustomerRiskProfile baseline
                                                    [alerted txns] → skip baseline update (poisoning guard — see TODOS.md)
```

---

## All Locked Architecture Decisions

### From original `/plan-eng-review` (2026-06-05) — D2–D18

1. **D2 — Kafka consumer idempotency keys**: Dedupes redelivered messages. Does NOT cover the DB-commit-succeeds-but-Kafka-publish-fails window (no Outbox — see TODOS.md). Accepted gap.
2. **D3 — Kafka messages keyed by `userId`**: Keeps one customer's events ordered for risk-profile consistency. Ordering is approximate during `@RetryableTopic` retries (retry may land on a different partition).
3. **D4 — DB-backed refresh token table**: 7-day TTL, revocable. Reuse-detection policy: TBD (before Phase 2 — see TODOS.md).
4. **D5 — PostgreSQL JSONB + Hypersistence Utils**: For `CustomerRiskProfile`'s typed JSON fields (typicalTransactionHours, typicalMerchantCategories, typicalCounterparties).
5. **D6 — Welford's online algorithm over a bounded recent-N window**: Numerically stable rolling mean/variance. Replaced originally-considered EWMA. Resists drift from old data. Bounded window mitigates (but does not eliminate) baseline poisoning — see TODOS.md for the open mitigation decision.
6. **D7 — `@RetryableTopic`**: 4 retries with backoff, then dead-letter topic. For Kafka consumer error handling.
7. **D8 — SSE via servlet `SseEmitter` + connection registry**: NOT Spring WebFlux/Flux. Reversed in this review (Codex flagged: running both reactive and servlet stacks for one endpoint adds complexity without payoff).
8. **D9 — `RiskRuleResult` record**: Standard return type for all `RiskRule` implementations — score + contributing factors.
9. **D10 — `@TransactionalEventListener(phase = AFTER_COMMIT)`**: Kafka publishing never fires before DB transaction lands.
10. **D11 + D15 — `@AuditLog` annotation**: Renamed from `@Audited` to avoid silent collision with Hibernate Envers' `@Audited`. Always fires (including on failure). Has explicit `outcome` field.
11. **D12 — `ErrorResponse` record**: Standard error contract for all API error responses.
12. **D13 — Pessimistic row locking + fixed lock ordering**: On transfers. Prevents overdrafts and deadlocks.
13. **D14 — Lower-bound-inclusive score boundaries**: `z ≥ 2.0` means "moderate," not "just under high."
14. **D16 — `profile.typicalCounterparties` reuse**: BehavioralBaselineRule reuses the same JSONB field (no separate counterparty-tracking structure).
15. **D17 — Single conditional-aggregation query for velocity**: Batch the 1h/24h/7d window counts in one query.
16. **D18 — Composite indexes via explicit Flyway migration**: Not via JPA @Index. Added in the same migration as the entity DDL.

### From `/plan-ceo-review` (2026-06-10) — Scope expansions accepted

- **Phase 4 addition**: 5 labeled fraud-scenario examples in OpenAPI spec (`@Operation`/`@ApiResponse`): Normal deposit (negative control), Velocity spike, Large amount to new counterparty, Fan-out pattern, Round-trip. Each has a per-scenario Testcontainers integration test asserting score/severity/alert-created.
- **Phase 7.5 (new)**: Railway deploy — React SPA compiled via Maven Frontend Plugin into `classpath:/static/`, served by Spring Boot. Upstash Kafka free tier (SASL/PLAIN). Demo seed user: `demo@ledgerbridge.io`, role `DEMO_ACTOR`. See Phase 7.5 in PHASES.md.
- **Phase 8 additions**: 10-15 ADRs in `docs/adr/`, technical blog post (draft in Phase 4, publish after Phase 7.5 ships).

### From `/plan-eng-review` fresh pass (2026-06-10) — D1–D10 (CEO additions)

1. **D1 — Maven Frontend Plugin** (`frontend-maven-plugin`): `mvn package` compiles both React and Java. Railway Dockerfile stays a single Java stage. No separate Node stage.
2. **D2 (CEO) + D8 — `SpaFallbackController`**: Catches `/**`, serves `classpath:static/index.html`. Excludes: `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/webjars/**`. Critical: Swagger paths must be in the exclusion list or Swagger UI breaks.
3. **D3 (CEO) — NormalDeposit is a precision test**: Assert score **< 0.4** + **no alert created**. It is NOT a sensitivity test. Proves false-positive resistance.
4. **D4 (CEO) — `DemoDataRefreshComponent`**: `@Component`, `@Profile("demo")`, `ApplicationReadyEvent`. Updates V8 seed transaction timestamps to `NOW() + fixed offsets` so velocity windows never go stale on a long-running Railway deploy. Must guard against DB not being ready on startup (retry or `@DependsOn`).
5. **D5 (CEO) — V8 demo seed profile-gated**: Located at `resources/db/demo/V8__demo_seed.sql`. Loaded only when `SPRING_PROFILES_ACTIVE=demo` (via `application-demo.properties` adding `classpath:db/demo` to `spring.flyway.locations`).
6. **D6 (CEO) — `DEMO_ACTOR` role**: POST `/api/transactions` + GET `/api/admin/**`. NOT read-only, NOT full admin. Lets the demo user trigger fraud scenarios and see alerts.
7. **D7 (CEO) — Railway health check**: `/actuator/health/liveness` — NOT `/actuator/health`. Reason: using the composite health endpoint causes Railway deploy flapping when Kafka readiness check fails during restart. Liveness probe stays green as long as the JVM is alive.
8. **D9 (CEO) — Milestone Rule confirmed**: Submit job applications to Wells Fargo, Capital One, Citi, and JPMorgan the day Phase 4 ships. Do not wait for Phase 8.
9. **D10 (CEO) — ADR count confirmed**: 10-15, each must cover alternatives considered + consequences. See Phase 8 in PHASES.md for the full list of decisions to cover.

### From `/plan-design-review` (2026-06-10) — Design system (see DESIGN.md for full spec)

- **Layout**: 160px sidebar (`#161616`) + main content area (`#111111`). No card mosaic.
- **Default route** (DEMO_ACTOR post-login): `/admin/alerts` — risk engine is the first thing a recruiter sees.
- **Alert queue**: Sortable table (44px rows), NOT stacked cards (cards = hard portfolio rejection risk).
- **RiskGauge**: 270° radial arc, 0.0–1.0 scale, decimal display (`0.73` not `73%`), 0.4 threshold dotted marker, 4 rule contribution mini bars, spring 600ms animation.
- **SeverityBadge**: Dark chip (`#1e1e1e` bg, `#3a3a3a` border) + 6px semantic color dot. Datadog/PagerDuty style. NOT Bootstrap pastel, NOT colored left-border.
- **SSE states**: Green 8px pulsing dot (connected), orange pulsing dot + "Reconnecting..." tooltip (disconnected).
- **WCAG 2.1 AA**: All text ≥ 4.5:1. `#888888` only at ≥14px or uppercase. Keyboard nav on alert table (Tab/Enter/Esc). ARIA landmarks.
- **Demo loop visible**: `Try a Demo Scenario →` button in alert queue header → `/swagger-ui.html`. Demo-aware empty state: "No risk alerts yet. Trigger a fraud scenario in Swagger UI →".

---

## Risk Engine

### Weights + Threshold
```
AmountAnomalyRule    × 0.25
VelocityRule         × 0.30
BehavioralBaselineRule × 0.20
GraphPatternRule     × 0.25

Escalation: if any single rule ≥ 0.8 → minimum total score = 0.65
Alert threshold: total ≥ 0.4
```

### Severity Mapping
```
score ≥ 0.8 → CRITICAL
score ≥ 0.6 → HIGH
score ≥ 0.4 → MEDIUM
score <  0.4 → (no alert)
```

### Rule Interfaces
All rules implement `RiskRule` → `RiskRuleResult` record (score + contributing factors). New rules plug in without modifying `RiskEngine`.

---

## Database

### Critical naming: `ledger_transaction` NOT `transaction`
The table is named `ledger_transaction` (not `transaction`) because `TRANSACTION` is a SQL reserved/contextual keyword. The JPA entity uses `@Table(name = "ledger_transaction")`. Migration is `V3__create_ledger_transaction.sql`.

### Flyway migration plan
```
V1__create_users_and_auth.sql
V2__create_accounts.sql
V3__create_ledger_transaction.sql         ← not "transaction"
V4__create_risk_tables.sql                ← customer_risk_profile, risk_alert
V5__create_audit_log.sql
V6__create_notifications.sql
V7__seed_demo_data.sql                    ← Phase 1 dev seed (labeled scenario patterns)
resources/db/demo/V8__demo_seed.sql       ← demo profile only (timestamps refreshed by DemoDataRefreshComponent)
```

### Money: always `NUMERIC(19,4)`
All monetary values use `NUMERIC(19,4)` in SQL and `BigDecimal` in Java. Never `double`, `float`, or `FLOAT`.

### Composite indexes (D18 — in explicit Flyway migration, not @Index)
Required for VelocityRule queries (`account_id + initiated_at`), BehavioralBaselineRule, and graph traversal queries.

---

## API Surface

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout

GET  /api/accounts
POST /api/accounts
GET  /api/accounts/{id}
GET  /api/accounts/{id}/transactions

POST /api/transactions/deposit        ← DEMO_ACTOR can call this to trigger fraud scenarios
POST /api/transactions/withdrawal
POST /api/transactions/transfer
GET  /api/transactions/{id}

GET  /api/admin/alerts                ← DEMO_ACTOR can access
GET  /api/admin/alerts/{id}
PATCH /api/admin/alerts/{id}/review
GET  /api/admin/alerts/stream         ← SSE endpoint (SseEmitter registry)
GET  /api/admin/customers/{id}/risk-profile

GET  /api/admin/audit-log
GET  /api/user/profile
GET  /api/user/notifications
PATCH /api/user/notifications/{id}/read
```

---

## Running Locally

**Prerequisites**: Java 21, Docker, Maven (or use `./mvnw`)

```bash
# Start infrastructure
docker-compose up -d db kafka

# Run the app (dev profile = human-readable logs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Swagger UI
open http://localhost:8080/swagger-ui.html

# Actuator health
curl http://localhost:8080/actuator/health/liveness
```

**Kafka**: connect from host → `localhost:9094`. Spring app uses `localhost:9094` by default.
**PostgreSQL**: `localhost:5432`, db `ledgerbridge`, user `ledgerbridge`, password from `.env` (default `ledgerbridge` in dev).

**Environment**: copy `.env.example` → `.env` and set `JWT_SECRET` to at least 32 chars.

---

## Open Design Work (TODOS.md — gated by phase)

| Item | Gate | Status |
|---|---|---|
| `ledger_transaction` rename decision | Before Phase 1 | ⬜ Needs design |
| Fraud-scenario validation matrix | Before Phase 1 | ⬜ Needs design |
| Refresh-token reuse/rotation policy | Before Phase 2 | ⬜ Needs design |
| API-level idempotency keys | Before Phase 3 | ⬜ Needs design |
| Correlation IDs / trace propagation | Before Phase 3 | ⬜ Needs design |
| Baseline poisoning mitigation | Before Phase 4 | ⬜ Needs design |
| `GraphPatternRule` traversal bounds | Before Phase 4 | ⬜ Needs design |
| Risk-engine Prometheus metrics | Alongside Phase 7 | ⬜ Needs design |
| JVM flag validation for Railway | Before Phase 7.5 | ⬜ Needs design |
| V8 demo seed timestamp matrix | Before Phase 7.5 | ⬜ Needs design |
| Upstash Kafka SASL/PLAIN spike | Before Phase 7.5 | ⬜ Needs design |

See `TODOS.md` for full details on each item.

---

## Known Accepted Tradeoffs

- **No Transactional Outbox**: DB commit can succeed but Kafka publish can fail (never fires). Accepted gap for scope. Needs a README callout as a known limitation.
- **Approximate message ordering on retries**: `@RetryableTopic` retries can land on a different Kafka partition, breaking userId-based ordering. Mitigated by idempotency keys (D2), not eliminated.

---

## GitHub

`git@github.com:cs-keni/ledgerbridge.git`
Branch: `main`

---

## Agent Working Rules

- Both Claude Code and Codex work on this repo. Always read HANDOFF.md first.
- **Never use `double` or `float` for money.** `BigDecimal` everywhere.
- No business logic in controllers or Kafka consumers (consumers call services, nothing else).
- All service methods that mutate state are `@Transactional`.
- `RiskRule` interface → pluggable rules → no RiskEngine modification for new rules.
- Test coverage required on all four risk rules before Phase 4 ships.
- Run `/review` before marking Phase 4 complete.
- Run `/qa` after Phase 6 frontend ships.
- Run `/plan-eng-review` again before Phase 4 (TODOS gate items need concrete designs by then).
- Update docs after **every code change**. No exceptions.
