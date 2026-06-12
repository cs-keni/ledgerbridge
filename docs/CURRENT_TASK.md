# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-12

## Active Task

**Phase 6 — /plan-design-review complete (2026-06-12) — next: /qa**

10 design fixes applied (6.5 → 9/10). Frontend ready for end-to-end QA run.
Start backend: `docker-compose up -d` → `./mvnw spring-boot:run`
Frontend: `cd frontend && npm run dev`

---

**Phase 5 — Admin + Audit ✅ COMPLETE (2026-06-12)**

### Shipped
- [x] `@AuditLog` annotation + `AuditAspect` (service layer, fires always including failures, outcome field — D11/D15)
- [x] `AuditService` (REQUIRES_NEW propagation) + `AuditController` (GET /api/admin/audit-log)
- [x] `SseAlertService` (SseEmitter registry, onTimeout/onCompletion/onError cleanup — D8/D2)
- [x] `AlertController` (GET list, GET by id, PATCH /review, GET /stream SSE)
- [x] `AlertService.reviewAlert()` with `@AuditLog(ALERT_REVIEWED)` + `SseAlertService` broadcast on createAlert
- [x] `NotificationService` + `NotificationController` (GET list, unread-count, PATCH read)
- [x] `CustomerRiskProfileService.saveRiskScore()` — TODOS.md Phase 5 item (currentRiskScore + riskTier persisted after every evaluation)
- [x] `TransactionRiskConsumer` calls saveRiskScore after every evaluation
- [x] `V11__add_audit_outcome.sql` — outcome column on audit_log
- [x] AuditAspect unit tests: 3 tests (success outcome, failure outcome, entityType attribute)
- [x] **Total tests: 89/89 passing**

### Up Next
Phase 6 — Frontend (React 18 + TypeScript + Tailwind)

---

## Phase 4 — Risk Engine ✅ COMPLETE (2026-06-12)

### Shipped
- [x] `RiskRule` interface + `RiskRuleResult` record
- [x] `AmountAnomalyRule` (Welford's z-score, MIN_HISTORY_COUNT=2)
- [x] `VelocityRule` (1h/1d/7d conditional-aggregation query, native SQL)
- [x] `BehavioralBaselineRule` (hour, MCC, new-counterparty signals)
- [x] `GraphPatternRule` (fan-out ≥5, fan-in ≥5, round-trip 2h, native SQL)
- [x] `RiskEngine` (weighted scoring 0.25/0.30/0.20/0.25, tier-1/tier-2 escalation)
- [x] `CustomerRiskProfileService` (Welford's update, D19 score-conditional baseline, T4/T5 fixes)
- [x] `AlertService` + `RiskAlertResponse` DTO (T3: DIV catch + `findByTransactionId`)
- [x] `TransactionRiskConsumer` (@RetryableTopic, @DltHandler, T10 full idempotency via `ProcessedTransactionEvent`)
- [x] `ProcessedTransactionEvent` entity + `ProcessedTransactionEventRepository` (T10)
- [x] `V10__risk_alert_unique_txn_and_processed_events.sql` (T3 UNIQUE + T10 table)
- [x] `KafkaConfig` retry/DLT topic beans (NEW CRITICAL)
- [x] Unit tests: 36/36 passing (AmountAnomaly 7, Velocity 6, Behavioral 9, GraphPattern 7, RiskEngine 7)
- [x] Integration tests: 5/5 fraud scenarios passing (S1–S5)
- [x] **Total tests: 84/84 passing**
- [x] `/plan-eng-review` gate: 11 issues (T1–T11), all P1 fixes implemented
- [x] `/review` gate: 8 critical fixes, 84/84 tests confirmed

### P2 Deferred (not blocking Phase 5)
- [x] T7: 7-day velocity scoring — weekSpike detection, floor on dailyThreshold — **complete 2026-06-12**
- [x] T8: EWMA inter-arrival velocity baseline in updateProfile() — **complete 2026-06-12**
- [x] T9: Fan-in semantic fix — countDistinctSendersSince (inbound) replaces countDistinctNewCounterpartiesSince (outbound) — **complete 2026-06-12**
- T11: Unit tests for TransactionRiskConsumer + CustomerRiskProfileService Welford logic
- OpenAPI `@Operation`/`@ApiResponse` examples for 5 fraud scenarios

### Up Next
Phase 5 — Admin + Audit (AuditAspect, AuditService, SSE alerts dashboard)
