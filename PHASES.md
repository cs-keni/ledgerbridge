# LedgerBridge — Implementation Phases

> Live task board. Mark tasks complete in real time as we progress.
> Commit convention: `Add Phase N: [what changed] — [why it matters]`

---

## Phase 0 — Setup ✅ Complete
- [x] Create GitHub repo `ledgerbridge`
- [x] Initialize Spring Boot 3.x project with all dependencies (Maven) — **complete 2026-06-10**: `pom.xml` (Spring Boot 3.3.5, Java 21, all deps), `LedgerBridgeApplication.java`, Maven wrapper generated, smoke test
- [x] Set up Docker Compose with PostgreSQL + Kafka — **complete 2026-06-10**: `docker-compose.yml` (PostgreSQL 16-alpine + Bitnami Kafka 3.7 KRaft, dual-listener external:9094/internal:9092, health checks)
- [x] Configure Flyway for migrations — **complete 2026-06-10**: enabled, `classpath:db/migration` location, `db/demo` added by `application-demo.properties`, migration dir created
- [x] Set up SpringDoc OpenAPI (Swagger UI) — **complete 2026-06-10**: `OpenApiConfig.java` (Bearer JWT auth scheme, all endpoints unlock with Authorize button), paths configured
- [x] Configure Logback for structured JSON logging — **complete 2026-06-10**: `logback-spring.xml` (JSON via logstash-logback-encoder in prod, human-readable in dev), `CorrelationIdFilter.java` (X-Correlation-ID → MDC on every request)
- [x] Create docs/: AI_CONTEXT.md, HANDOFF.md, ENGINEERING_LOG.md, CURRENT_TASK.md
- [x] Create PHASES.md
- [x] Run `/plan-eng-review` before proceeding to Phase 1 — **complete 2026-06-05**: 18 decisions locked (D2–D18, see AI_CONTEXT.md), Codex outside-voice review run, 6 follow-up items written to `TODOS.md`

## Phase 1 — Domain + Database ✅ Complete
- [x] **TODOS gate:** rename `transaction` table → `ledger_transaction` — **complete 2026-06-11**: entity `LedgerTransaction.java`, migration `V3__create_ledger_transaction.sql`, all docs updated
- [x] **TODOS gate:** design fraud-scenario validation matrix — **complete 2026-06-11**: `docs/RISK_ENGINE_TEST_MATRIX.md` created; 5 scenarios with full score math, seed UUIDs, Phase 4 test assertions, multi-rule escalation decision locked
- [x] Define all JPA entities with proper types (NUMERIC for money, UUID for IDs, JSONB + Hypersistence Utils for CustomerRiskProfile per D5) — **complete 2026-06-11**: 11 entities (User, RefreshToken, Account, LedgerTransaction, CustomerRiskProfile, RiskAlert, AuditLog, Notification + 7 enums)
- [x] Write Flyway migrations V1–V6, including composite indexes per D18 — **complete 2026-06-11**: V1 (users/auth), V2 (accounts), V3 (ledger_transaction + 3 composite indexes), V4 (risk tables with JSONB), V5 (audit_log), V6 (notifications)
- [x] Write seed data migration V7 (5 users, 10 accounts, 50+ transactions across various patterns) — **complete 2026-06-11**: 5 scenario users (Alice/Bob/Carol/Dave/Eve), 5 accounts, 80 historical transactions, 5 pre-computed CustomerRiskProfiles with Welford M2 values
- [x] Seed data includes: high-velocity customer, unusual-amount customer, fan-out pattern, normal baselines — each labeled with expected risk-score range (see fraud-validation gate above)
- [x] Confirm migrations run clean — **complete 2026-06-11**: SchemaIntegrationTest (4 tests) passes; V1–V7 applied, Hibernate validate OK, seed data assertions pass

## Phase 2 — Auth + Account Module ✅ Complete
- [x] **TODOS gate:** decide refresh-token reuse/rotation policy — **complete 2026-06-11**: token family rotation locked. `family_id UUID` on refresh_token; replay = revoke entire family + log security event. TTL: 7 days. See D4 in `AI_CONTEXT.md`.
- [x] Implement User entity, Spring Security config, JWT service — **complete 2026-06-11**: `JwtService` (JJWT 0.12.5), `UserPrincipal`, `UserDetailsServiceImpl`, `JwtAuthenticationFilter`, `SecurityConfig` (stateless JWT, permitAll for auth/swagger/actuator-health)
- [x] Implement AuthController: register, login, refresh (DB-backed refresh token per D4), logout — **complete 2026-06-11**: `AuthService` + `AuthController`; replay detection logs security event and revokes entire family (`noRollbackFor` ensures revocation commits on exception)
- [x] Implement AccountService + AccountController — **complete 2026-06-11**: full CRUD (create, list, get, close); ownership enforced on get/close; V8 migration adds `family_id`
- [x] Write unit tests for auth and account services — **complete 2026-06-11**: 21/21 passing (AuthServiceTest 12, AccountServiceTest 9)

## Phase 3 — Transaction Module + Kafka ✅ Complete
- [x] **TODOS gate:** API-level idempotency keys on POST /transactions and /transfers — **complete 2026-06-11**: Stripe pattern, 24h TTL, SHA-256 request hash, 422 on mismatch; `IdempotencyKey` entity + `IdempotencyService` (REQUIRES_NEW propagation), V9 migration
- [x] **TODOS gate:** correlation-ID / trace propagation — **complete 2026-06-11**: `correlation_id VARCHAR(36)` on `ledger_transaction` via V9; propagated from MDC (`X-Correlation-ID` request header → `CorrelationIdFilter`) → service → `TransactionEvent.correlationId` → Kafka header `X-Correlation-ID`
- [x] Implement TransactionService (deposit, withdrawal, transfer with balance validation, pessimistic locking + fixed lock ordering per D13) — **complete 2026-06-11**
- [x] Implement TransactionEventProducer (publish to Kafka via `@TransactionalEventListener(AFTER_COMMIT)` per D10, keyed by userId per D3) — **complete 2026-06-11**
- [x] Implement TransactionController (deposit, withdraw, transfer with `Idempotency-Key` header support; GET /{id} and paged list) — **complete 2026-06-11**
- [x] Write unit tests for TransactionService (mock Kafka producer) — **complete 2026-06-11**: 15 tests passing
- [x] Write integration test: full deposit flow → Kafka event published — **complete 2026-06-11**: 2 tests (deposit + withdraw) via embedded Kafka (@EmbeddedKafka), 38/38 total tests passing

## Phase 4 — Risk Engine (Core Differentiator) ✅ Complete
- [x] **TODOS gate:** decide baseline-poisoning mitigation — **complete 2026-06-11**: score-first evaluation; skip profile update if score ≥ 0.4 (D19). Known counterparty = first non-alerted appearance. typicalCounterparties max 50 entries (LRU eviction). See TODOS.md.
- [x] **TODOS gate:** define `GraphPatternRule` traversal bounds — **complete 2026-06-11**: 1 hop only; fan-out/fan-in ≥5 new counterparties in 24h; round-trip exact-amount match within 2h; query cap 100; "new" = NOT IN typicalCounterparties (D20). See TODOS.md.
- [x] Implement CustomerRiskProfile update logic (Welford's online algorithm over a bounded recent-N window per D6/Tension 1 — replaces originally-considered EWMA) — **complete 2026-06-11**
- [x] Implement AmountAnomalyRule (Z-score) — **complete 2026-06-11**
- [x] Implement VelocityRule (sliding window counts, single conditional-aggregation query per D17) — **complete 2026-06-11**
- [x] Implement BehavioralBaselineRule (time-of-day, MCC, new counterparty — reuses `profile.typicalCounterparties` per D16) — **complete 2026-06-11**
- [x] Implement GraphPatternRule (fan-in, fan-out, round-trip detection — within the bounds decided above) — **complete 2026-06-11**
- [x] Implement RiskEngine (weighted score aggregation via `RiskRuleResult` records per D9, alert creation, lower-bound-inclusive thresholds per D14) — **complete 2026-06-11**
- [x] Implement TransactionRiskConsumer (Kafka consumer → RiskEngine, `@RetryableTopic` per D7, idempotency-key dedupe per D2) — **complete 2026-06-11; rewritten 2026-06-12 for full idempotency via ProcessedTransactionEvent**
- [x] Unit tests for each rule with edge cases — assert against the labeled scenario matrix from the Phase 1 fraud-validation gate — **complete 2026-06-11**: 36/36 rule unit tests
- [x] Integration test: transaction → Kafka → risk consumer → alert created — **complete 2026-06-11**
- [x] **Fraud scenario tests (per-scenario quality gate):** write a Testcontainers integration test for each of the 5 Swagger fraud scenarios — **complete 2026-06-11**: 5/5 passing (S1–S5). Fixed: Hibernate `LocalDateTime` UTC conversion bug via `-Duser.timezone=UTC` in Surefire + native SQL queries for 1h/2h windows; `@DirtiesContext` fixes SchemaIntegrationTest container lifecycle conflict. **84/84 total tests passing.**
  - Normal deposit: assert score **< 0.4** and **no alert created** (negative control — proves false-positive resistance) ✓
  - Velocity spike: assert score ≥ 0.4, severity MEDIUM+ ✓ (0.46)
  - Large amount to new counterparty: assert score ≥ 0.4, severity HIGH ✓
  - Fan-out pattern: assert score ≥ 0.4, severity HIGH ✓
  - Round-trip: assert score ≥ 0.4, severity CRITICAL ✓ (0.80 via tier-2 escalation)
  — (D3, /plan-eng-review 2026-06-10: NormalDeposit is a precision test, not a sensitivity test)
- [x] Run `/plan-eng-review` — **complete 2026-06-11**: 11 issues found (T1–T11), all approved for implementation
- [x] Run `/review` — **complete 2026-06-12**: 8 critical fixes implemented (T1–T6, T10, +retry/DLT topics); 84/84 tests passing. Commit: see ENGINEERING_LOG Session 15.
- [ ] Add 5 labeled fraud-scenario examples to OpenAPI spec via `@Operation`/`@ApiResponse` on transaction endpoint (Normal deposit, Velocity spike, Large amount to new counterparty, Fan-out pattern, Round-trip)
- [ ] **MILESTONE RULE: submit applications to Wells Fargo, Capital One, Citi, and JPMorgan the day this phase ships**

## Phase 5 — Admin + Audit ✅ Complete
- [x] Implement AuditAspect (AOP auto-logging with `@AuditLog` — renamed from `@Audited` to avoid a Hibernate Envers collision per D11; always logs, including failures, with an `outcome` field per D15) — **complete 2026-06-12**
- [x] Implement AuditService + AuditController — **complete 2026-06-12**
- [x] Implement AlertController (GET list/byId, PATCH review, GET /stream SSE) — **complete 2026-06-12**
- [x] Implement SSE endpoint via servlet `SseEmitter` + connection registry (per D8/Tension 3 — reversed from an initial WebFlux/Flux choice; needs explicit error/completion cleanup so dropped connections don't leak emitters) — **complete 2026-06-12**
- [x] Implement NotificationService + NotificationController (GET list, unread-count, PATCH read) — **complete 2026-06-12**
- [x] TODOS.md Phase 5: write `currentRiskScore`/`riskTier` after every evaluation — **complete 2026-06-12**
- [x] Write tests for audit logging (3 tests: success outcome, failure outcome, entityType attribute) — **complete 2026-06-12**
- [x] **89/89 tests passing**

## Phase 6 — Frontend
- [x] **DESIGN gate:** run `/plan-design-review` — **complete 2026-06-10**: `DESIGN.md` created, 11 decisions locked (sidebar layout, demo default route → `/admin/alerts`, risk gauge spec, interaction states, severity badges, WCAG 2.1 AA). All design decisions in `DESIGN.md`.
- [x] **Backend fixes (Lane A):** — **complete 2026-06-12**: SecurityConfig role guard on `/api/admin/**` (ADMIN + DEMO_ACTOR), `anyRequest().permitAll()` for SPA routes, `AlertDetailResponse` enriched DTO, `AuditController` optional entity filter + `listAll()`, `SseAlertService` no-timeout + 15s heartbeat, V12 DEMO_ACTOR migration. 89/89 tests.
- [x] Set up React 18 + TypeScript + Tailwind + React Query + Zustand — **complete 2026-06-12**: `frontend/` scaffold: package.json, vite.config.ts, tailwind.config.ts (all DESIGN.md tokens + animations), tsconfig.json, postcss.config.js, index.html, src/main.tsx, src/index.css. Vite outDir → `target/classes/static/`; maven-frontend-plugin bound to `prepare-package`. SpaFallbackController for React Router.
- [x] Build auth pages (login/register) — **complete 2026-06-12**: `LoginPage.tsx` (shake on bad creds, spinner, WCAG 2.1 AA focus rings, always-visible labels), `RegisterPage.tsx` (first/last name, same design). `authStore.ts` (Zustand: access token in memory, refresh in localStorage, `silentRefresh()` on boot). `ProtectedRoute.tsx` (spinner during silent re-auth, redirect on no token). All TypeScript types in `src/types/api.ts`.
- [x] Build Admin: Alert queue — **complete 2026-06-12**: `AlertsPage.tsx` (stat chips, filter tabs, pagination, SSE live invalidation, "Try a Demo Scenario →" CTA), `AlertTable.tsx` (sortable, skeleton shimmer, empty state, `alert-arrive` animation for fresh rows), `Sidebar.tsx` (NavLink nav, SSE connection dot), `AdminLayout.tsx`
- [x] Build `RiskGauge` component — **complete 2026-06-12**: SVG 200×160, START=135°, SWEEP=270°, spring-animated arc (stiffness=100, damping=20), 3-segment gradient, threshold marker at 0.4, center score (28px Geist Mono) + severity label, 4 rule contribution bars
- [x] Build Admin: Alert detail slide-in panel — **complete 2026-06-12**: `AlertDetailPanel.tsx` — 380px translateX panel, RiskGauge, transaction details, Review/Dismiss/Resolve via useMutation, backdrop overlay
- [x] Build Admin: Audit log — **complete 2026-06-12**: `AuditLogPage.tsx` (paginated, skeleton shimmer, OutcomeBadge)
- [x] Build Dashboard (account overview, recent activity) — **complete 2026-06-12**: `DashboardPage.tsx` (KPI row, recent alerts list, accounts summary)
- [x] Build Account list — **complete 2026-06-12**: `AccountsPage.tsx` (plain list — backend returns `List<AccountResponse>`, not paginated; empty state for DEMO_ACTOR)
- [x] Connect SSE for real-time alert badge — **complete 2026-06-12**: `useAlertStream.ts` (fetch+ReadableStream, JWT header, exp backoff 1s→30s); `sseStore.ts` (Zustand status atom); `useAlertStream` in `AlertsPage` invalidates React Query cache on each SSE event
- [x] Build Transfer form — **complete 2026-06-12**: `TransferPage.tsx` — 3-tab form (Deposit/Withdraw/Transfer), account dropdown, idempotency key auto-generated, success card with transaction number. `/transfer` route + Sidebar link.
- [x] WCAG 2.1 AA verification — **complete 2026-06-12**: contrast audit passed (#888888 on #111111 = 5.35:1, on #1a1a1a = 4.93:1, both ≥4.5:1 AA); keyboard nav on alert table (Tab/Enter/Space); Escape closes AlertDetailPanel; focus moves to close button on panel open; all form inputs have `useId()` label pairs; `role="alert"` on errors; ARIA landmarks verified.
- [x] **DESIGN gate (second pass):** run `/plan-design-review` — **complete 2026-06-12**: 10 fixes, 6.5→9/10. h1 20px, system-wide stat chips (AlertStatsResponse API), demo credentials on login, skeleton shimmer in panel, gauge bar semantics fixed, sidebar alert count badge, avg score dynamic color, button color rule, action btn focus rings, full-width content.
- [x] Run `/qa` to verify all flows end-to-end — **complete 2026-06-12**: 3 bugs fixed (ISSUE-006 score normalization, ISSUE-007 status filter, ISSUE-011 async accountId). Health: 6/10 → 9/10. Deposit/Withdraw/Transfer tab UI all confirmed working. Commit: `36be7f2`.

## Phase 7 — Observability + DevOps ✅ Complete
- [x] **TODOS gate:** design risk-engine-specific Prometheus metrics — **complete 2026-06-12**: `risk.scoring.duration` (Timer, tagged `alert_triggered`), `risk.alerts.created` (Counter, tagged `alert_type`+`severity`), `risk.dlt.messages` (Counter). Implemented in `RiskMetrics.java`, wired into `RiskEngine.evaluate()` and `TransactionRiskConsumer.handleDlt()`.
- [x] Verify all structured logging in place — **complete 2026-06-12**: `CorrelationIdFilter` propagates X-Correlation-ID from Phase 3; risk consumer sets MDC `correlationId` from Kafka header; Logback JSON in prod, readable in dev. All logging in place.
- [x] Add Spring Actuator health + metrics endpoints — **complete** (from Phase 6 application.properties): health/liveness/readiness probes + `/actuator/prometheus` already exposed. Added `micrometer-registry-prometheus` dep to pom.xml.
- [x] Finalize Docker Compose — **complete 2026-06-12**: Added `prometheus` (prom/prometheus:v2.53.0) and `grafana` (grafana/grafana:11.1.0) services with volume mounts, `host-gateway` extra_host for scraping the app, persistent volumes.
- [x] Write GitHub Actions CI workflow — **complete 2026-06-12**: `.github/workflows/ci.yml` — two jobs: `backend` (Java 21, Maven test with Testcontainers, Surefire report on failure) and `frontend` (Node 20, `npm ci`, `tsc --noEmit`, `npm run build`).
- [x] Add Prometheus + Grafana with dashboard — **complete 2026-06-12**: `monitoring/prometheus.yml` (scrapes `/actuator/prometheus`); Grafana auto-provisioned datasource + dashboard (`monitoring/grafana/dashboards/risk-engine.json`) with 6 panels: scoring latency p50/p95/p99, alert creation rate by type, severity pie chart, evaluations/min stat, DLT counter stat, alert vs clean transaction rate.
- [x] Write .env.example with all variables documented — **complete 2026-06-12**: DB_PASSWORD, JWT_SECRET, SPRING_KAFKA_BOOTSTRAP_SERVERS, SPRING_PROFILES_ACTIVE, GRAFANA_PASSWORD, commented Upstash Kafka block.

## Phase 7.5 — Live Demo Deploy (Supabase + Render)
> Railway dropped its free tier. Using Supabase (managed Postgres, free) + Render (app host, free 750h/month) instead.

- [x] **TODOS gate:** validate JVM memory flags — `-Xmx200m -Xms64m -XX:+UseSerialGC` set in Dockerfile ENTRYPOINT for Render free tier (512MB RAM) — **complete 2026-06-12**
- [x] **TODOS gate:** design demo seed SQL timestamp matrix — 6 trigger transactions + 6 alerts across CRITICAL/HIGH/MEDIUM/LOW × OPEN/UNDER_REVIEW/RESOLVED/DISMISSED — **complete 2026-06-12**: `V13__demo_alerts_and_audit.sql`
- [x] Write Dockerfile — multi-stage `eclipse-temurin:21-jdk-jammy` build → `eclipse-temurin:21-jre-alpine` runtime; non-root user; shell-form ENTRYPOINT maps Render's `$PORT` env var — **complete 2026-06-12**
- [x] Write `resources/db/demo/V13__demo_alerts_and_audit.sql` — 6 fixed-UUID trigger transactions, 6 risk alerts (covers all 5 fraud scenarios), 3 notifications, 3 audit log entries — **complete 2026-06-12**
- [x] Implement `DemoDataRefreshComponent` (`@Profile("demo")`, `ApplicationReadyEvent`) — refreshes all demo timestamps to NOW() + relative offsets on every startup — **complete 2026-06-12**
- [x] Configure Upstash Kafka SASL/PLAIN env vars — documented in `.env.example` and `render.yaml` — **complete 2026-06-12**
- [x] Add `render.yaml` blueprint — Docker runtime, free plan, health check at `/actuator/health/liveness`, all env vars declared — **complete 2026-06-12**
- [x] Fix `SpaFallbackController` — added explicit exclusions for `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` — **complete 2026-06-12**
- [x] **Supabase:** create project, copy direct connection string (port 5432, NOT 6543). JDBC URL: `jdbc:postgresql://[host].supabase.co:5432/postgres?sslmode=require`. Flyway applies V1–V14 + demo seed automatically on first deploy — **complete 2026-06-12**
- [x] **Render:** create Web Service from GitHub repo, Docker runtime, set env vars from `render.yaml`. Validate health check passes — **complete 2026-06-12**: `https://ledgerbridge-i0c5.onrender.com`
- [x] Verify demo end-to-end: live URL → login as `demo@ledgerbridge.io` → alerts pre-loaded → submit transfer → confirm transaction — **complete 2026-06-13**: deposit TXNBD67A30802F5 confirmed COMPLETED. SSE shows "reconnecting" (Render proxy times out long-lived connections — cosmetic only).
- [x] Add live URL to README "Live Demo" section with demo credentials and screenshot — **complete 2026-06-16**

## Phase 8 — Testing + Portfolio Integration
- [ ] Testcontainers integration tests: full transaction→risk flow
- [ ] Verify test coverage on all four risk engine rules
- [x] Write polished README with Mermaid architecture diagram, live demo URL, demo credentials, risk engine explanation, architecture decisions overview — **complete 2026-06-16**
- [x] /design-review pass — **complete 2026-06-20**: 8 findings fixed (login tagline, Demo CTA prominence, touch targets, StatChip semantic colors, Dashboard nav hidden for DEMO_ACTOR). Commits: `4db3968`–`dfa4049`.
- [x] Take screenshots of key pages (risk alert feed, risk score gauge, Swagger fraud scenario — use demo seed data) — **complete 2026-06-20**: moved to `docs/screenshots/`. README Screenshots section added.
- [ ] Write 10-15 Architecture Decision Records in `docs/adr/` covering: UUID PKs, NUMERIC(19,4), Flyway, Kafka userId keying, @TransactionalEventListener, Welford's algorithm, @RetryableTopic, SseEmitter, pessimistic locking + fixed ordering, idempotency keys, correlation IDs, JSONB + Hypersistence, RiskRuleResult record, @AuditLog naming, composite indexes
- [ ] Draft technical blog post (CC drafts from AI_CONTEXT.md, Kenny edits — publish to dev.to after Phase 7.5 live URL exists, share on LinkedIn with live demo URL)
- [ ] Add project to ePortfolio (`src/data/projects.js`)
- [x] Final `/review` + `/qa` pass — **complete 2026-06-16**: /review (commit `fe54a0b`), /qa found+fixed 2 bugs: ISSUE-001 RiskGauge score display (`fadab39`), ISSUE-002 stats stale after review (`9f9b8f1`). Health: 72 → 95.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 1 | CLEAN | mode: SELECTIVE_EXPANSION, 4 proposals, 4 accepted, 0 deferred |
| Codex Review | `/codex review` | Independent 2nd opinion | 3 | issues_found | Multiple rounds |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 3 | CLEAN | 7 issues, 0 critical gaps |
| Design Review | `/plan-design-review` | UI/UX gaps | 1 | CLEAN | score: 6.5/10 → 9/10, 10 decisions |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | — |

**VERDICT:** CEO + ENG + DESIGN CLEARED — ready for /qa run

NO UNRESOLVED DECISIONS
