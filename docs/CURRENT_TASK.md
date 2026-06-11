# CURRENT_TASK.md — LedgerBridge

> Reflects the single active task. Update when starting or finishing a task.
> Last updated: 2026-06-11

## Active Task

**Phase 3 — Transaction Module + Kafka** (not yet started)

### Phase 2 Complete ✅
- [x] **TODOS gate:** refresh-token rotation policy — done 2026-06-11
- [x] `JwtService`, `UserPrincipal`, `UserDetailsServiceImpl`, `JwtAuthenticationFilter`, `SecurityConfig`
- [x] `AuthService` + `AuthController` (register, login, refresh with family rotation, logout)
- [x] `AccountService` + `AccountController` (create, list, get, close)
- [x] V8 migration — `family_id UUID` on `refresh_token`
- [x] Unit tests: 21/21 passing (AuthServiceTest 12, AccountServiceTest 9)

### Phase 3 — Not Yet Started
- [ ] **TODOS gate:** API-level idempotency keys on POST /transactions + /transfers
- [ ] **TODOS gate:** correlation-ID / trace propagation through Kafka headers + MDC
- [ ] Implement TransactionService (deposit, withdrawal, transfer)
- [ ] Implement TransactionEventProducer (@TransactionalEventListener AFTER_COMMIT)
- [ ] Implement TransactionController
- [ ] Unit tests for TransactionService; integration test: deposit → Kafka event

### Blocked On
- Nothing. Phase 3 TODOS gates need to be resolved first before implementation.
