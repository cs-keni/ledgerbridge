# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-11

## Active Task

**Phase 4 — Risk Engine** (not yet started)

### Phase 3 Complete ✅
- [x] **TODOS gate:** API-level idempotency keys — done 2026-06-11
- [x] **TODOS gate:** correlation-ID / trace propagation — done 2026-06-11
- [x] V9 migration — `idempotency_key` table + `correlation_id` on `ledger_transaction`
- [x] `IdempotencyService` (Stripe pattern, REQUIRES_NEW, 24h TTL)
- [x] `TransactionService` (deposit, withdraw, transfer with D13 pessimistic locking)
- [x] `TransactionEventProducer` (@TransactionalEventListener AFTER_COMMIT, userId key, X-Correlation-ID header)
- [x] `TransactionController` (POST deposit/withdraw/transfer + Idempotency-Key header; GET /{id} + paged list)
- [x] Unit tests: 15/15 TransactionServiceTest passing
- [x] Integration tests: 2/2 TransactionIntegrationTest passing (embedded Kafka)
- [x] **Total tests: 38/38 passing**

### Phase 4 — TODOS Gates Cleared ✅
- [x] **TODOS gate:** baseline-poisoning mitigation — done 2026-06-11 (D19)
- [x] **TODOS gate:** GraphPatternRule traversal bounds — done 2026-06-11 (D20)
- [ ] Implement CustomerRiskProfile update logic (Welford's algorithm, D6/D19)
- [ ] Implement AmountAnomalyRule, VelocityRule, BehavioralBaselineRule, GraphPatternRule (D20)
- [ ] Implement RiskEngine (weighted score aggregation, D9/D14)
- [ ] Implement TransactionRiskConsumer (Kafka consumer, @RetryableTopic D7, idempotency D2, score-conditional baseline update D19)
- [ ] Unit + integration tests per fraud scenario matrix (S1–S5)

### Blocked On
- Nothing. Ready to implement Phase 4.
