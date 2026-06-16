# HANDOFF.md — LedgerBridge

> Update this whenever architecture, module ownership, or component structure changes.
> Last updated: 2026-06-16

## Current Status

**Phase: 8 — Portfolio Integration (in progress)**

All phases shipped:
- `/plan-eng-review` ✅ Done 2026-06-05: 18 decisions locked (D2–D18)
- `/plan-ceo-review` ✅ Done 2026-06-10: 4 scope expansions, D1–D10 locked
- `/plan-design-review` ✅ Done 2026-06-10: `DESIGN.md` created, 11 decisions locked
- Phase 0–7 ✅ All shipped (see ENGINEERING_LOG.md for per-session detail)
- Phase 7.5 ✅ Live at `https://ledgerbridge-i0c5.onrender.com` (Supabase + Render)
- Phase 8 in progress: README done; ADRs, blog post, ePortfolio, final /review + /qa remaining

**Portfolio strategy locked** — see `docs/designs/portfolio-strategy.md`
**Design system locked** — see `DESIGN.md` (SSE uses fetch+ReadableStream, not EventSource)

## Last Agent Action

Claude Code (2026-06-16): Phase 8 — README.md created (commit `cdcafd6`).

- `README.md` (NEW): Mermaid architecture diagram, live demo badge + URL, demo credentials, risk engine explanation (weights + escalation tiers), fraud scenario table, full API surface, known limitations, ADR index, project structure map.
- `docs/CURRENT_TASK.md`: updated to reflect Phase 8 in progress.
- `docs/ENGINEERING_LOG.md`: Session 30 entry added.
- `PHASES.md`: README checkbox marked complete.

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

**Phase 8 remaining:**
1. `docs/adr/` — 15 Architecture Decision Records (ADR-001 through ADR-015)
2. Technical blog post draft
3. ePortfolio entry (`src/data/projects.js`)
4. Final `/review` + `/qa` pass before demo screenshots + video

## Module Ownership / Status

| Module | Status | Files |
|---|---|---|
| auth | ✅ Complete | JwtService, AuthService, AuthController, UserPrincipal, JwtAuthenticationFilter, SecurityConfig |
| account | ✅ Complete | AccountService, AccountController |
| transaction | ✅ Complete | TransactionService, TransactionController, TransactionEventProducer, IdempotencyService |
| risk | ✅ Complete | RiskEngine, AmountAnomalyRule, VelocityRule, BehavioralBaselineRule, GraphPatternRule, TransactionRiskConsumer, AlertService, CustomerRiskProfileService, AlertController, SseAlertService |
| audit | ✅ Complete | AuditAspect (@AuditLog AOP), AuditService, AuditController |
| notification | ✅ Complete | NotificationService, NotificationController |
| common | ✅ Complete | CorrelationIdFilter, OpenApiConfig, SecurityConfig, KafkaConfig, GlobalExceptionHandler, AppException, IdempotencyService, SpaFallbackController, DemoDataRefreshComponent |
| frontend | ✅ Complete | React 18 SPA — AlertsPage, AlertDetailPanel, RiskGauge, DashboardPage, TransferPage, AuditLogPage, AccountsPage, LoginPage, RegisterPage, useAlertStream (SSE), authStore, sseStore |

## Architecture Notes

Modular monolith. Package: `com.ledgerbridge`. See `AI_CONTEXT.md` for full architecture + all locked decisions.

**Critical naming**: the transaction table is `ledger_transaction` (not `transaction` — SQL reserved word). Entity: `@Table(name = "ledger_transaction")`. Migration: `V3__create_ledger_transaction.sql`.

**Idempotency**: `IdempotencyService.store()` uses `Propagation.REQUIRES_NEW` so the idempotency record commits even if the caller's transaction rolls back. Silent on `DataIntegrityViolationException` (concurrent duplicate request; first write wins).

**Kafka publish**: `@TransactionalEventListener(AFTER_COMMIT)` ensures Kafka message is sent AFTER DB commit (D10). If Kafka send fails, DB is already committed — accepted gap (D2). Key = userId for per-user ordering (D3).

**Transfer deadlock prevention**: `AccountRepository.findByIdWithLock()` with `@Lock(PESSIMISTIC_WRITE)`. Lock acquisition order = ascending UUID comparison. Fixed in `TransactionService.transfer()`.

## Open Design Work

No architectural decisions pending. All TODOS gates resolved through Phase 7.5.

Known accepted tradeoffs (document in README, no code change required):
- No Transactional Outbox — Kafka publish can fail silently after DB commit. Documented in README.
- SSE drops on Render reverse proxy — client reconnects with backoff; polling keeps alert counts fresh. Documented in README.
- Approximate Kafka ordering on retry — idempotency keys prevent duplicates but not ordering.
