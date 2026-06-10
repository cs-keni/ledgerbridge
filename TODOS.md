# TODOS.md — LedgerBridge

> Deferred design decisions surfaced during `/plan-eng-review` (2026-06-05).
> Each item must be resolved (designed + decided) before the phase listed —
> not bolted on afterward. Check off when the design is locked, not when the
> code lands (code completion is tracked in PHASES.md).

## Before Phase 1 (seed data)

- [ ] **Fraud-scenario validation strategy** — Design a labeled synthetic-scenario
  test matrix: each seed-data pattern (high-velocity, unusual-amount, fan-out,
  normal baseline) gets an *expected score range*, not just "a pattern that
  exists." Phase 4 tests assert against these expected outcomes. This is what
  makes the 0.25/0.30/0.20/0.25 weights and 0.4 alert threshold defensible
  rather than arbitrary — and becomes the backbone of the Phase 8 README
  "Risk Engine Explanation" section.
  - Source: Codex outside-voice review, finding "no validation strategy for
    risk engine accuracy"

## Before Phase 1 (schema)

- [ ] **Rename `transaction` table to `ledger_transaction`** — Avoid the SQL
  reserved/contextual keyword collision (`START TRANSACTION`, isolation-level
  syntax). Free to do now (table doesn't exist yet); costly after Phase 1
  ships (migration + entity + every raw query + every doc reference). Update
  `@Table(name = "ledger_transaction")` on the entity and all references in
  `ledgerbridge.md`'s domain model description for consistency.
  - Source: Codex outside-voice review, finding "`transaction` is a SQL
    reserved word"

## Before Phase 3 (transaction/transfer endpoints)

- [ ] **API-level idempotency keys** — Require an `Idempotency-Key` header
  (client-generated UUID) on `POST /transactions` and `POST /transfers`;
  server stores `(key, request_hash, response_body, expiry)` and returns the
  cached response on retry instead of re-executing the transfer. This is
  DISTINCT from D2 (Kafka consumer-side idempotency, which prevents duplicate
  *alerts* from redelivery) — this prevents duplicate *transfers* from
  double-clicks or client-side network retries, the textbook payments-API
  pattern (Stripe/Square). Needs a small supporting table + TTL/cleanup
  decision; pairs naturally with D13's pessimistic-locking transaction
  boundary.
  - Source: Codex outside-voice review, finding "no idempotency at the
    API/command layer (separate from consumer-side dedup)"

- [ ] **Correlation IDs / trace propagation** — Generate a correlation ID at
  the transaction-submission boundary, propagate via Kafka message headers
  and MDC (Mapped Diagnostic Context), and carry it into risk-engine
  evaluation, alert creation, audit-log entries, and structured JSON log
  output (Logback). Must land starting Phase 3 — retrofitting across
  transaction/risk/audit/notification modules later is a much bigger lift.
  Makes the async event-driven pipeline (after-commit publish, retry/DLT)
  debuggable with one grep instead of manual reconstruction.
  - Source: Codex outside-voice review, finding "no correlation ID across
    the async transaction → risk → alert → audit chain"

## Before Phase 4 (risk engine implementation)

- [ ] **Baseline poisoning mitigation** — Decide and document a policy so a
  fraudster's early transactions don't get folded into BehavioralBaselineRule's
  "normal" profile via Welford's update, silently raising the bar for what
  counts as suspicious for that customer. Leading option: exclude
  alert-triggering transactions (score ≥ 0.4) from baseline updates, or hold
  baseline updates until a transaction clears review. Affects the Kafka
  consumer's evaluation order (score first, conditionally update baseline vs.
  always update then re-score). Settle alongside D6 (Welford's bounded-window
  decision).
  - Source: Codex outside-voice review, finding "behavioral baseline has no
    poisoning resistance — adversarial transactions normalize the profile"

## Before Phase 6 (frontend)

- [ ] **Run `/plan-design-review` before Phase 6** — The React admin dashboard (risk
  score gauge, SSE alert feed, transaction table with score column) is the live
  demo's visual centerpiece and the portfolio screenshot that goes on LinkedIn.
  Design system, interaction states (loading/empty/error/success/partial for each
  feature), animation direction, and the risk score gauge visual design should be
  locked before implementation begins. A senior engineer reviewing a polished UI
  demo reacts differently than one reviewing a functional but generic dashboard.
  - Priority: P2
  - Effort: S (~30 min gstack review)
  - Source: CEO review Section 11 — /plan-design-review recommendation for UI-heavy
    phases per SELECTIVE EXPANSION guidelines

## Before Phase 7.5 (Railway deploy)

- [ ] **Validate JVM memory flags for Railway free tier** — Confirm that
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
