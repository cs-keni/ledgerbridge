# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-11

## Current Status

**Phase: 4 — Risk Engine ✅ COMPLETE — Phase 5 (Admin + Audit) is next**

All planning gates cleared:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18), Codex outside voice, 6 TODOs
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions accepted, Codex outside voice, 3 new TODOs
- `/plan-eng-review` (CEO additions) ✅ Done 2026-06-10: 9 issues found, all resolved, D1–D10 locked
- `/plan-design-review` ✅ Done 2026-06-10: `DESIGN.md` created, 11 decisions locked, score 2/10 → 8/10
- `/plan-eng-review` (Phase 4) ✅ Done 2026-06-11: 11 issues (T1–T11), all P1 fixes approved
- `/review` (Phase 4) ✅ Done 2026-06-12: 8 critical fixes shipped, 84/84 tests passing

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md` for the full CEO plan.
**Design system locked** — see `DESIGN.md` for all Phase 6 component specs.

Phase 4 complete: Risk Engine with 4 rules (AmountAnomaly/Velocity/Behavioral/GraphPattern), weighted scoring + tier-1/tier-2 escalation, Kafka consumer with @RetryableTopic + DLT, D19 score-conditional baseline update, full consumer idempotency via `ProcessedTransactionEvent`, 84/84 tests passing.

## Last Agent Action

Claude Code (2026-06-12): Phase 4 /review gate — 8 critical fixes. Edits + new files:
- `TransactionRepository.java` — T1: `AND type = 'TRANSFER_DEBIT'` in `existsRoundTrip`; T2: `countDistinctNewCounterpartiesSince` → native SQL + LIMIT 100
- `GraphPatternRule.java` — T6: sentinel UUID guard for empty `knownCounterparties` (prevents Hibernate 6 `IllegalArgumentException` on `NOT IN ()`)
- `CustomerRiskProfileService.java` — T5: hour freq initial value `1.0` → `1.0/newCount`; T4: DIV catch + re-fetch in `getOrCreate()`
- `AlertService.java` — T3: DIV catch + re-fetch in `createAlert()` via new `findByTransactionId()`
- `RiskAlertRepository.java` — T3: added `Optional<RiskAlert> findByTransactionId(UUID)`
- `risk/model/ProcessedTransactionEvent.java` (NEW) — T10: idempotency entity
- `risk/repository/ProcessedTransactionEventRepository.java` (NEW) — T10: `existsByTransactionId()`
- `TransactionRiskConsumer.java` — T10: rewrote to use `ProcessedTransactionEventRepository`; now guards ALL deliveries, not just alerted ones
- `KafkaConfig.java` — NEW CRITICAL: added retry-0/retry-1/DLT topic beans (required when `autoCreateTopics=false`)
- `V10__risk_alert_unique_txn_and_processed_events.sql` (NEW) — T3: UNIQUE on `risk_alert.transaction_id`; T10: `processed_transaction_event` table
- `AmountAnomalyRuleTest.java` / `RiskEngineTest.java` — pinned `LocalDateTime` to deterministic values
- **84/84 tests passing**

## Previous Agent Action

Claude Code (2026-06-11): Completed Phase 4. New files:
- `risk/rules/{RiskRule, RiskRuleResult, AmountAnomalyRule, VelocityRule, BehavioralBaselineRule, GraphPatternRule}.java` — 4-rule detection with Welford's z-score, velocity windows, behavioral signals, fan-out/fan-in/round-trip graph patterns
- `risk/engine/{RiskEngine, RiskScoringResult}.java` — weighted sum (0.25/0.30/0.20/0.25), tier-1 (any raw ≥0.8 → floor 0.65), tier-2 (≥3 rules ≥0.6 → floor 0.80)
- `risk/service/{AlertService, CustomerRiskProfileService}.java` — alert persistence, Welford's update, LRU counterparty list (max 50), D19 score-conditional update
- `risk/consumer/TransactionRiskConsumer.java` — @RetryableTopic (3 attempts, 2x backoff), @DltHandler, D2 idempotency via existsByTransactionId
- `risk/dto/RiskAlertResponse.java` — REST response DTO
- `test/risk/{AmountAnomalyRuleTest, VelocityRuleTest, BehavioralBaselineRuleTest, GraphPatternRuleTest, RiskEngineTest, RiskScenarioIntegrationTest}.java` — 84/84 passing
- Edits: `RiskAlertRepository.java` (existsByTransactionId), `TransactionRepository.java` (native SQL for velocity/round-trip queries), `pom.xml` (Surefire -Duser.timezone=UTC)
- **Key infrastructure fix:** `hibernate.jdbc.time_zone=UTC` shifts LocalDateTime JPQL/native params by JVM-to-UTC offset. Fix: test JVM runs in UTC (Surefire argLine) — production-correct and eliminates all offset skew.
- **84/84 tests passing**

## Previous Agent Action

Claude Code (2026-06-11): Completed Phase 3. New files:
- `V9__phase3_idempotency_and_correlation.sql` — `idempotency_key` table + `correlation_id` on `ledger_transaction`
- `common/idempotency/IdempotencyKey.java` + `IdempotencyKeyRepository.java` + `IdempotencyService.java` — Stripe-pattern: SHA-256 hash, 24h TTL, 422 on hash mismatch, REQUIRES_NEW propagation
- `transaction/event/TransactionEvent.java` + `TransactionCompletedEvent.java` — Spring application event
- `common/config/KafkaConfig.java` — `transaction-events` topic (3 partitions)
- `transaction/kafka/TransactionEventProducer.java` — `@TransactionalEventListener(AFTER_COMMIT)`, userId Kafka key, X-Correlation-ID header
- `transaction/service/TransactionService.java` — deposit/withdraw/transfer (D13 pessimistic lock + fixed UUID ordering), ownership checks, AppException semantics
- `transaction/controller/TransactionController.java` — POST deposit/withdraw/transfer with optional `Idempotency-Key`; GET /{id} + paged list
- `transaction/dto/{TransactionRequest, TransferRequest, TransactionResponse}.java`
- `test/common/KafkaIntegrationTest.java` — base class (PostgreSQL Testcontainer + @EmbeddedKafka)
- `test/transaction/TransactionServiceTest.java` — 15 Mockito unit tests
- `test/transaction/TransactionIntegrationTest.java` — 2 integration tests (deposit + withdraw → Kafka event verified)
- Edits: `LedgerTransaction.java` (correlationId field), `AccountRepository.java` (findByIdWithLock)
- **38/38 tests passing**

## Previous Agent Action

Claude Code (2026-06-11): Completed Phase 2. JWT auth (token family rotation replay detection), Spring Security (stateless), AuthController, AccountService/AccountController, V8 migration, 21/21 unit tests passing.

## Previous Agent Action

Claude Code (2026-06-11): Completed Phase 1. All entities, migrations, and integration tests written and passing.

## Previous Agent Action (2026-06-10): Phase 0 scaffold

Claude Code (2026-06-10): Completed Phase 0 scaffold. See ENGINEERING_LOG.md Session 7 for full detail.

## What's Next

**Phase 4 — Risk Engine (Core Differentiator)**

Two TODOS gates must be locked before implementation:
1. **Baseline-poisoning mitigation** — Exclude alert-triggering transactions from Welford's update? Hold until review clears? Affects evaluation order in the Kafka consumer.
2. **GraphPatternRule traversal bounds** — Max hops, time window, counterparty-count ceiling. Critical gap from failure-mode review; must be a mini-design before writing the rule.

Then implement (in order):
1. `CustomerRiskProfile` update logic (Welford's online algorithm, bounded recent-N window per D6)
2. `AmountAnomalyRule` (Z-score)
3. `VelocityRule` (sliding window, single conditional-aggregation query per D17)
4. `BehavioralBaselineRule` (time-of-day, MCC, counterparties per D16)
5. `GraphPatternRule` (fan-in/fan-out/round-trip — within locked traversal bounds)
6. `RiskEngine` (weighted score via `RiskRuleResult` records per D9, alert creation, thresholds per D14)
7. `TransactionRiskConsumer` (Kafka consumer, `@RetryableTopic` per D7, idempotency dedupe per D2)
8. Unit tests per fraud scenario matrix; integration test: transaction → Kafka → risk consumer → alert

## Module Ownership / Status

| Module | Status | Files |
|---|---|---|
| auth | ✅ Complete | JwtService, AuthService, AuthController, UserPrincipal, JwtAuthenticationFilter, SecurityConfig |
| account | ✅ Complete | AccountService, AccountController |
| transaction | ✅ Complete | TransactionService, TransactionController, TransactionEventProducer, IdempotencyService |
| risk | Not started | — |
| audit | Not started | — |
| notification | Not started | — |
| common | Active | CorrelationIdFilter, OpenApiConfig, SecurityConfig, KafkaConfig, GlobalExceptionHandler, AppException |
| frontend | Not started | — |

## Architecture Notes

Modular monolith. Package: `com.ledgerbridge`. See `AI_CONTEXT.md` for full architecture + all locked decisions.

**Critical naming**: the transaction table is `ledger_transaction` (not `transaction` — SQL reserved word). Entity: `@Table(name = "ledger_transaction")`. Migration: `V3__create_ledger_transaction.sql`.

**Idempotency**: `IdempotencyService.store()` uses `Propagation.REQUIRES_NEW` so the idempotency record commits even if the caller's transaction rolls back. Silent on `DataIntegrityViolationException` (concurrent duplicate request; first write wins).

**Kafka publish**: `@TransactionalEventListener(AFTER_COMMIT)` ensures Kafka message is sent AFTER DB commit (D10). If Kafka send fails, DB is already committed — accepted gap (D2). Key = userId for per-user ordering (D3).

**Transfer deadlock prevention**: `AccountRepository.findByIdWithLock()` with `@Lock(PESSIMISTIC_WRITE)`. Lock acquisition order = ascending UUID comparison. Fixed in `TransactionService.transfer()`.

## Open Design Work (Decisions Pending)

| Item | Gate | Notes |
|---|---|---|
| Baseline poisoning mitigation | Before Phase 4 | Exclude alerted txns from Welford's update? Hold until review? |
| `GraphPatternRule` traversal bounds | Before Phase 4 | Max hops, time window, counterparty-count ceiling — mini-design required |
| Risk-engine Prometheus metrics | Alongside Phase 7 | Instrument during Phase 4 so metrics land with the rule code |
| JVM flag validation | Before Phase 7.5 | Confirm -Xmx200m -Xms64m -XX:+UseSerialGC fits Railway 512MB |
| V8 demo seed timestamp matrix | Before Phase 7.5 | Depends on Phase 4 fraud-scenario validation matrix |
| Upstash Kafka SASL/PLAIN spike | Before Phase 7.5 | Prove connectivity before building Railway deploy around it |

See `TODOS.md` for full detail on each item.
