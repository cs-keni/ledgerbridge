# LedgerBridge — Implementation Phases

> Live task board. Mark tasks complete in real time as we progress.
> Commit convention: `Add Phase N: [what changed] — [why it matters]`

---

## Phase 0 — Setup
- [x] Create GitHub repo `ledgerbridge`
- [ ] Initialize Spring Boot 3.x project with all dependencies (Maven)
- [ ] Set up Docker Compose with PostgreSQL + Kafka
- [ ] Configure Flyway for migrations
- [ ] Set up SpringDoc OpenAPI (Swagger UI)
- [ ] Configure Logback for structured JSON logging
- [x] Create docs/: AI_CONTEXT.md, HANDOFF.md, ENGINEERING_LOG.md, CURRENT_TASK.md
- [x] Create PHASES.md
- [x] Run `/plan-eng-review` before proceeding to Phase 1 — **complete 2026-06-05**: 18 decisions locked (D2–D18, see AI_CONTEXT.md), Codex outside-voice review run, 6 follow-up items written to `TODOS.md`

## Phase 1 — Domain + Database
- [ ] **TODOS gate:** rename `transaction` table → `ledger_transaction` (avoids SQL reserved-word collision; free now, costly after migrations ship — see TODOS.md)
- [ ] **TODOS gate:** design fraud-scenario validation matrix — labeled seed-data scenarios with *expected score ranges*, not just "patterns that exist" (drives Phase 4 test assertions; see TODOS.md and `docs/RISK_ENGINE_TEST_MATRIX.md`)
- [ ] Define all JPA entities with proper types (NUMERIC for money, UUID for IDs, JSONB + Hypersistence Utils for CustomerRiskProfile per D5)
- [ ] Write Flyway migrations V1–V6, including composite indexes per D18
- [ ] Write seed data migration V7 (5 users, 10 accounts, 50+ transactions across various patterns)
- [ ] Seed data includes: high-velocity customer, unusual-amount customer, fan-out pattern, normal baselines — each labeled with expected risk-score range (see fraud-validation gate above)
- [ ] Confirm migrations run clean

## Phase 2 — Auth + Account Module
- [ ] **TODOS gate:** decide refresh-token reuse/rotation policy (revoke whole token family on replay-detection? — flagged as underspecified in D4 during failure-mode review)
- [ ] Implement User entity, Spring Security config, JWT service
- [ ] Implement AuthController: register, login, refresh (DB-backed refresh token table per D4), logout
- [ ] Implement AccountService + AccountController
- [ ] Write unit tests for auth and account services

## Phase 3 — Transaction Module + Kafka
- [ ] **TODOS gate:** API-level idempotency keys on POST /transactions and /transfers (`Idempotency-Key` header — distinct from D2's consumer-side dedupe; prevents double-submission from double-clicks/client retries)
- [ ] **TODOS gate:** correlation-ID / trace propagation — generate at submission boundary, thread through Kafka headers + MDC, into risk/audit/SSE/structured logs (must start here — retrofitting later is expensive)
- [ ] Implement TransactionService (deposit, withdrawal, transfer with balance validation, pessimistic locking + fixed lock ordering per D13)
- [ ] Implement TransactionEventProducer (publish to Kafka via `@TransactionalEventListener(AFTER_COMMIT)` per D10, keyed by userId per D3)
- [ ] Implement TransactionController
- [ ] Write unit tests for TransactionService (mock Kafka producer)
- [ ] Write integration test: full deposit flow → Kafka event published

## Phase 4 — Risk Engine (Core Differentiator)
- [ ] **TODOS gate:** decide baseline-poisoning mitigation (e.g. exclude alert-triggering transactions from Welford's baseline updates — see TODOS.md)
- [ ] **TODOS gate:** define `GraphPatternRule` traversal bounds explicitly (max hops, time window, counterparty-count ceiling) — flagged in failure-mode review as a critical gap; treat as its own mini-design before writing the rule
- [ ] Implement CustomerRiskProfile update logic (Welford's online algorithm over a bounded recent-N window per D6/Tension 1 — replaces originally-considered EWMA)
- [ ] Implement AmountAnomalyRule (Z-score)
- [ ] Implement VelocityRule (sliding window counts, single conditional-aggregation query per D17)
- [ ] Implement BehavioralBaselineRule (time-of-day, MCC, new counterparty — reuses `profile.typicalCounterparties` per D16)
- [ ] Implement GraphPatternRule (fan-in, fan-out, round-trip detection — within the bounds decided above)
- [ ] Implement RiskEngine (weighted score aggregation via `RiskRuleResult` records per D9, alert creation, lower-bound-inclusive thresholds per D14)
- [ ] Implement TransactionRiskConsumer (Kafka consumer → RiskEngine, `@RetryableTopic` per D7, idempotency-key dedupe per D2)
- [ ] Unit tests for each rule with edge cases — assert against the labeled scenario matrix from the Phase 1 fraud-validation gate
- [ ] Integration test: transaction → Kafka → risk consumer → alert created
- [ ] **Fraud scenario tests (per-scenario quality gate):** write a Testcontainers integration test for each of the 5 Swagger fraud scenarios:
  - Normal deposit: assert score **< 0.4** and **no alert created** (negative control — proves false-positive resistance)
  - Velocity spike: assert score ≥ 0.4, severity MEDIUM+
  - Large amount to new counterparty: assert score ≥ 0.4, severity HIGH
  - Fan-out pattern: assert score ≥ 0.4, severity HIGH
  - Round-trip: assert score ≥ 0.4, severity CRITICAL
  — (D3, /plan-eng-review 2026-06-10: NormalDeposit is a precision test, not a sensitivity test)
- [ ] Add 5 labeled fraud-scenario examples to OpenAPI spec via `@Operation`/`@ApiResponse` on transaction endpoint (Normal deposit, Velocity spike, Large amount to new counterparty, Fan-out pattern, Round-trip)
- [ ] Run `/plan-eng-review` again (per ledgerbridge.md gate) — by now the TODOS-gated items above need concrete designs
- [ ] Run `/review` before marking Phase 4 complete
- [ ] **MILESTONE RULE: submit applications to Wells Fargo, Capital One, Citi, and JPMorgan the day this phase ships**

## Phase 5 — Admin + Audit
- [ ] Implement AuditAspect (AOP auto-logging with `@AuditLog` — renamed from `@Audited` to avoid a Hibernate Envers collision per D11; always logs, including failures, with an `outcome` field per D15)
- [ ] Implement AuditService + AuditController
- [ ] Implement AlertService + admin alert review flow
- [ ] Implement SSE endpoint via servlet `SseEmitter` + connection registry (per D8/Tension 3 — reversed from an initial WebFlux/Flux choice; needs explicit error/completion cleanup so dropped connections don't leak emitters)
- [ ] Write tests for audit logging

## Phase 6 — Frontend
- [ ] **TODOS gate:** run `/plan-design-review` before starting frontend implementation (lock design system, interaction states, risk score gauge visual design — see TODOS.md)
- [ ] Set up React 18 + TypeScript + Tailwind + React Query + Zustand
- [ ] Build auth pages (login/register)
- [ ] Build Dashboard (account overview, recent transactions)
- [ ] Build Account list + detail with transaction history
- [ ] Build Transfer form
- [ ] Build Admin: Alert queue with risk score visualization
- [ ] Build Admin: Alert detail with rule breakdown display
- [ ] Build Admin: Audit log
- [ ] Connect SSE for real-time alert badge
- [ ] Run `/qa` to verify all flows end-to-end

## Phase 7 — Observability + DevOps
- [ ] **TODOS gate:** design risk-engine-specific Prometheus metrics (`risk_scoring_latency_seconds`, `risk_alerts_created_total` by rule, `risk_consumer_lag`, `risk_dlt_messages_total`, `risk_rule_contribution_distribution`) — generic Actuator metrics alone won't show whether the differentiator works
- [ ] Verify all structured logging in place (correlation IDs from Phase 3 should now be queryable end-to-end)
- [ ] Add Spring Actuator health + metrics endpoints
- [ ] Finalize Docker Compose (all services)
- [ ] Write GitHub Actions CI workflow
- [ ] Add Prometheus + Grafana with at least one dashboard panel for the risk-engine metrics above
- [ ] Write .env.example with all variables documented

## Phase 7.5 — Live Demo Deploy (Railway)
- [ ] **TODOS gate:** validate JVM memory flags locally before deploying (-Xmx200m -Xms64m -XX:+UseSerialGC — see TODOS.md)
- [ ] **TODOS gate:** design V8__demo_seed.sql timestamp matrix for 5 fraud scenarios (see TODOS.md)
- [ ] Write Dockerfile (multi-stage: Maven build → eclipse-temurin:21-jre-alpine) with JVM flag ENV
- [ ] Write `resources/db/demo/V8__demo_seed.sql` (profile-gated: only loads when `SPRING_PROFILES_ACTIVE=demo` via `application-demo.properties` — D5) — demo admin user (demo@ledgerbridge.io, role: DEMO_ACTOR per D6), demo accounts with known fixed UUIDs, 30+ anchor-timestamped prior transactions per scenario
- [ ] Implement `DemoDataRefreshComponent` (@Component, @Profile("demo"), runs on ApplicationReadyEvent) — updates V8 seed transaction timestamps to NOW() + fixed offsets so velocity windows never go stale (D4)
- [ ] Configure Upstash Kafka free tier (SASL/PLAIN) — Spring Kafka SASL config via env vars
- [ ] Set up Railway project: web service (GitHub auto-deploy) + managed PostgreSQL
- [ ] Set all Railway env vars: SPRING_DATASOURCE_*, JWT_SECRET, UPSTASH_KAFKA_* credentials
- [ ] Configure `railway.toml` health check: `/actuator/health/liveness` (NOT `/actuator/health` — DB/Kafka readiness causes Railway deploy flapping; enable probes with `management.endpoint.health.probes.enabled=true` — D7)
- [ ] Configure Maven Frontend Plugin (frontend-maven-plugin) in pom.xml to compile React SPA as part of `mvn package`, output to `src/main/resources/static/` (D1, /plan-eng-review 2026-06-10: keeps Railway Dockerfile to a single Java stage)
- [ ] Add `SpaFallbackController` — catches `/**` (excluding `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/webjars/**`) and serves `classpath:static/index.html` (D2 + D8, /plan-eng-review 2026-06-10 — Swagger paths excluded or Swagger UI breaks)
- [ ] Verify demo: visit live URL → log in with demo credentials (from README) → see pre-loaded alerts → trigger fraud scenario in Swagger → alert appears in React admin dashboard
- [ ] Add live URL to README "Live Demo" section with demo credentials and screenshot

## Phase 8 — Testing + Portfolio Integration
- [ ] Testcontainers integration tests: full transaction→risk flow
- [ ] Verify test coverage on all four risk engine rules
- [ ] Write polished README with Mermaid architecture diagram, live demo URL, demo credentials, risk engine explanation, architecture decisions overview
- [ ] Take screenshots of key pages (risk alert feed, risk score gauge, Swagger fraud scenario — use demo seed data)
- [ ] Write 10-15 Architecture Decision Records in `docs/adr/` covering: UUID PKs, NUMERIC(19,4), Flyway, Kafka userId keying, @TransactionalEventListener, Welford's algorithm, @RetryableTopic, SseEmitter, pessimistic locking + fixed ordering, idempotency keys, correlation IDs, JSONB + Hypersistence, RiskRuleResult record, @AuditLog naming, composite indexes
- [ ] Draft technical blog post (CC drafts from AI_CONTEXT.md, Kenny edits — publish to dev.to after Phase 7.5 live URL exists, share on LinkedIn with live demo URL)
- [ ] Add project to ePortfolio (`src/data/projects.js`)
- [ ] Final `/review` + `/qa` pass
