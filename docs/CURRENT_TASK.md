# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-11

## Active Task

**Phase 4 — Risk Engine COMPLETE ✅**

### Phase 4 Complete ✅
- [x] `RiskRule` interface + `RiskRuleResult` record
- [x] `AmountAnomalyRule` (Welford's z-score, MIN_HISTORY_COUNT=2)
- [x] `VelocityRule` (1h/1d/7d conditional-aggregation query, native SQL)
- [x] `BehavioralBaselineRule` (hour, MCC, new-counterparty signals)
- [x] `GraphPatternRule` (fan-out ≥5, fan-in ≥5, round-trip 2h, native SQL)
- [x] `RiskEngine` (weighted scoring 0.25/0.30/0.20/0.25, tier-1/tier-2 escalation)
- [x] `CustomerRiskProfileService` (Welford's update, D19 score-conditional baseline)
- [x] `AlertService` + `RiskAlertResponse` DTO
- [x] `TransactionRiskConsumer` (@RetryableTopic, @DltHandler, D2 idempotency, D19)
- [x] Unit tests: 36/36 passing (AmountAnomaly 7, Velocity 6, Behavioral 9, GraphPattern 7, RiskEngine 7)
- [x] Integration tests: 5/5 fraud scenarios passing (S1–S5)
- [x] **Total tests: 84/84 passing**
- [x] PHASES.md + ENGINEERING_LOG.md + HANDOFF.md updated
- [x] Commit: `a1b04a9`

### Phase 4 Remaining
- [ ] Add 5 labeled fraud-scenario examples to OpenAPI spec via `@Operation`/`@ApiResponse`
- [ ] Run `/plan-eng-review` (architecture review gate)
- [ ] Run `/review` before marking Phase 4 fully complete
- [ ] **MILESTONE: submit applications to Wells Fargo, Capital One, Citi, JPMorgan**

### Up Next
Phase 5 — Admin + Audit (AuditAspect, AuditService, SSE alerts dashboard)
