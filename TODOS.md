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
