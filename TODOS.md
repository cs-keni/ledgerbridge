# TODOS.md — LedgerBridge

> Engineering planning doc — deferred design decisions from `/plan-eng-review` (2026-06-05).
> **Project is portfolio-complete as of 2026-06-20.** Open items below are known
> limitations intentionally deferred for portfolio scope. See README Known Limitations.

## Before Phase 1 (seed data)

- [x] **Fraud-scenario validation strategy** — **complete 2026-06-11**: see
  `docs/RISK_ENGINE_TEST_MATRIX.md`. 5 scenarios (S1 Normal, S2 Velocity spike,
  S3 Large+new counterparty, S4 Fan-out, S5 Round-trip) with full score math,
  seed customer UUIDs, and per-scenario Phase 4 test assertions. New design
  decision locked: multi-rule escalation tier (≥3 rules raw ≥0.6 → floor 0.80
  CRITICAL). This is distinct from the existing single-rule escalation (≥0.8 →
  floor 0.65 HIGH). See `ledgerbridge.md` RiskEngine section and
  `AI_CONTEXT.md` for updated scoring spec.
  - Source: Codex outside-voice review, finding "no validation strategy for
    risk engine accuracy"

## Before Phase 1 (schema)

- [x] **Rename `transaction` table to `ledger_transaction`** — **complete
  2026-06-11**: decision locked and propagated to all docs. Entity is
  `LedgerTransaction.java` with `@Table(name = "ledger_transaction")`.
  Migration file is `V3__create_ledger_transaction.sql`. Updated in:
  `ledgerbridge.md` (entity name, module structure, Flyway migration list),
  `AI_CONTEXT.md` (already had it — confirmed consistent). No schema changes
  needed yet (table doesn't exist until Phase 1 migration runs).
  - Source: Codex outside-voice review, finding "`transaction` is a SQL
    reserved word"

## Before Phase 2 (auth)

- [x] **Refresh-token reuse/rotation policy** — **complete 2026-06-11**: Token
  family rotation locked. Tokens share a `family_id UUID` (generated per login).
  Normal refresh: revoke old token, issue new token in same family. Replay
  detection (revoked token presented): revoke ALL tokens in that family → forced
  re-login + security event logged. TTL: 7 days. Schema addition: `family_id UUID
  NOT NULL` on `refresh_token` table. This is the Auth0 / OAuth 2.0 Security BCP
  standard; appropriate for a fintech portfolio targeting Wells Fargo/Citi/JPMorgan.
  - Source: D4 from original `/plan-eng-review` — storage mechanism locked, replay
    policy flagged as underspecified

## Before Phase 3 (transaction/transfer endpoints)

- [x] **API-level idempotency keys** — **complete 2026-06-11**: `IdempotencyKey` entity + `IdempotencyKeyRepository` + `IdempotencyService`. Stripe pattern: `(idem_key, user_id)` unique index; SHA-256 hash of JSON-serialized request; 422 on hash mismatch; 24h TTL; `REQUIRES_NEW` propagation prevents idempotency store from rolling back with the caller; silent on duplicate-key race. V9 migration creates `idempotency_key` table. `TransactionController` accepts optional `Idempotency-Key` header on all 3 write endpoints.

- [x] **Correlation IDs / trace propagation** — **complete 2026-06-11**: `correlation_id VARCHAR(36)` added to `ledger_transaction` via V9; `LedgerTransaction.correlationId` field; `TransactionService` reads from MDC (`CorrelationIdFilter` sets `correlationId` key from `X-Correlation-ID` header on every request); `TransactionEvent` carries `correlationId`; `TransactionEventProducer` sets `X-Correlation-ID` Kafka message header.

## Before Phase 4 (risk engine implementation)

- [x] **Baseline poisoning mitigation** — **complete 2026-06-11**: Score-conditional
  update (D19). Evaluation order in `TransactionRiskConsumer`: compute score first →
  if score ≥ 0.4, create alert + SKIP `CustomerRiskProfile` update; if score < 0.4,
  no alert + UPDATE profile (Welford's stats + typicalCounterparties). "Known
  counterparty" threshold: **first appearance** (one non-alerted transaction adds the
  counterparty to typicalCounterparties). `typicalCounterparties` max: **50 entries**;
  evict oldest on overflow. Tradeoff accepted: adversary staying below 0.4 can still
  slowly poison the baseline; mitigated by Welford's bounded recent-N window (damage is
  limited to the sliding window, not all-time history).

- [x] **GraphPatternRule traversal bounds** — **complete 2026-06-11** (D20). Formally
  locked from test-matrix definitions:
  - **Max hops**: 1 (direct counterparty only). Multi-hop deferred — needs recursive
    CTEs or graph DB, out of scope for Phase 4.
  - **Fan-out**: ≥5 distinct new recipients in 24h → raw 0.8
  - **Fan-in**: ≥5 distinct new senders in 24h → raw 0.7
  - **Round-trip**: same amount TRANSFER sent to counterparty X and returned from X
    within 2h → raw 0.6. Amount match is exact (NUMERIC(19,4), no floating point).
  - **"New" = NOT IN** `CustomerRiskProfile.typicalCounterparties` at scoring time.
  - **Query cap**: 100 counterparties per window (prevents pathological scan on
    high-volume accounts).
  - `typicalCounterparties` max size: **50 entries** (shared with D19).

## Before Phase 6 (frontend)

- [x] **Run `/plan-design-review` before Phase 6** — **complete 2026-06-10**: `DESIGN.md` created, 11 decisions locked. See `DESIGN.md` for full design system.
  Design system, interaction states (loading/empty/error/success/partial for each
  feature), animation direction, and the risk score gauge visual design locked.
  Key decisions: sidebar + workspace layout, Risk Alerts as DEMO_ACTOR default route,
  radial arc risk gauge with 0.4 threshold marker + rule contribution bars, filled dark
  chip severity badges, WCAG 2.1 AA a11y standard, demo-aware empty state with Swagger
  link, SSE reconnecting indicator, 'Try a Demo Scenario →' in-app Swagger CTA.

## Known Limitations (intentionally deferred for portfolio scope)

- [ ] **Multi-tab refresh token replay (BroadcastChannel coordination)** — Two
  React tabs silently refreshing with the same stored refresh token cause
  `AuthService.refresh()` to detect a replay and revoke the entire token family —
  logging both tabs out. Fix: `BroadcastChannel` cross-tab mutex in `authStore.ts`
  so only one tab calls `/api/auth/refresh` at a time. Other tabs listen for the
  new token and update their Zustand state from the broadcast.
  - Priority: P3
  - Effort: S (~50 min, frontend only)
  - Source: Codex outside-voice review (Phase 6 /plan-eng-review 2026-06-12):
    "Two tabs can silently refresh with the same stored token; the loser presents a
    revoked token and AuthService revokes the whole family"
  - **Note:** Phase 6 ships without this fix. Document as known limitation in
    README. A recruiter opening the demo in two tabs simultaneously will be logged
    out of both — acceptable for portfolio scope.

## N/A — Railway-specific (deployed to Render/Supabase instead)

- [x] **Validate JVM memory flags for Railway free tier** — Confirm that
  `-Xmx200m -Xms64m -XX:+UseSerialGC` is sufficient for Spring Boot + Upstash
  Kafka consumer + PostgreSQL connection pool + Flyway + Spring Actuator to start
  and handle 5 concurrent fraud-scenario requests without OOM on Railway's ~512MB
  container. Run locally with those JVM flags before deploying. Adjust flag values
  in Dockerfile if needed. An OOM-crashing demo URL is worse than no demo URL.
  - Priority: P2
  - Effort: S (~15 min)
  - Source: CEO review Section 7 / Codex outside-voice finding on unproven JVM
    constraint

- [ ] **Design V8__demo_seed.sql timestamp matrix** — The demo seed migration
  cannot simply insert 30 transactions at the same timestamp. Welford's bounded
  recent-N window, velocity sliding windows (1h, 24h, 7d), and time-of-day
  behavioral checks all depend on relative timestamps. For each of the 5 Swagger
  fraud scenarios, define INSERT timestamps relative to `NOW() - interval` so
  that the velocity window and baseline are primed correctly when a recruiter
  executes the scenario. Seed goes in `resources/db/demo/` (profile-gated to
  `SPRING_PROFILES_ACTIVE=demo` — D5). Demo user: `demo@ledgerbridge.io` with
  `DEMO_ACTOR` role (POST transactions + GET admin alerts — D6). Seed uses fixed
  UUID anchor IDs. `DemoDataRefreshComponent` (D4) refreshes timestamps at boot
  so velocity windows never go stale on a long-running Railway deploy.
  - Priority: P1
  - Effort: S (~20 min to design, ~30 min CC to implement)
  - Depends on: Phase 4 TODOS gate "Fraud-scenario validation matrix" — the
    timestamp design mirrors the test scenario matrix structure
  - Source: Codex outside-voice finding "30+ transactions is hand-wavy — needs
    ordering and timestamps for velocity/behavioral rules to fire correctly" +
    Codex/plan-eng-review 2026-06-10: D4 (timestamp refresh), D5 (profile-gating),
    D6 (DEMO_ACTOR role)

- [ ] **Upstash Kafka SASL/PLAIN connectivity spike** — Before Phase 7.5 builds
  the Railway deploy around Upstash Kafka, validate the actual SASL/PLAIN
  connection. Create an Upstash account and topic, produce 1 message and consume
  it from a minimal Spring Kafka config (bootstrap server, SASL config, TLS).
  Verify: topic creation works, Spring Kafka SASL config matches Upstash
  requirements, retry topics (via @RetryableTopic naming convention) can be
  created on Upstash, DLT behavior is as expected. Free-tier: 10K msg/day limit —
  confirm no per-message cost concerns with normal demo traffic.
  - Priority: P2
  - Effort: S (~20 min)
  - Source: Codex outside-voice finding (plan-eng-review 2026-06-10): "Railway +
    Upstash Kafka is assumed feasible but not proven"

## Before Phase 5 (admin + audit)

- [x] **Write currentRiskScore and riskTier after every evaluation** — **complete 2026-06-12**: `CustomerRiskProfileService.saveRiskScore()` writes `currentRiskScore` and `riskTier` (LOW/MEDIUM/HIGH/CRITICAL derived from 0.3/0.6/0.8 thresholds) after every evaluation regardless of alert outcome. `TransactionRiskConsumer` calls it after `riskEngine.evaluate()`.

## Alongside Phase 7 (observability)

- [ ] **Risk-engine-specific Prometheus metrics** — Beyond generic Spring Boot
  Actuator metrics (HTTP rates, JVM stats), instrument the risk engine itself:
  `risk_scoring_latency_seconds` (histogram), `risk_alerts_created_total`
  (counter, labeled by triggering rule), `risk_consumer_lag` (gauge),
  `risk_dlt_messages_total` (counter), `risk_rule_contribution_distribution`
  (histogram per rule). Sketch metric names/types/labels before Phase 4 so
  instrumentation hooks land with the rule engine code, not bolted on in
  Phase 7. Pairs with correlation IDs (traces answer "what happened to THIS
  transaction"; metrics answer "how is the SYSTEM performing").
  - Source: Codex outside-voice review, finding "no risk-engine-specific
    observability plan — generic metrics won't show whether the
    differentiator works"

- [ ] **Risk engine DB query caching** — 4 DB queries fire synchronously on every
  risk evaluation (countVelocityWindows, countDistinctNewCounterpartiesSince×2,
  existsRoundTrip). For Railway under load, these will serialize. Consider a
  short-TTL Caffeine cache (5-30s) keyed on userId before the Phase 7.5 Upstash
  Kafka integration. Run without caching first; Prometheus metrics will show
  whether it's actually needed before investing.
  - Priority: P3
  - Effort: S (~30 min)
  - Source: Architecture review finding (plan-eng-review 2026-06-11)
