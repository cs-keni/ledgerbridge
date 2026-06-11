# ENGINEERING_LOG.md — LedgerBridge

> Log every change: date, what changed, why. Add date header if missing.

---

## 2026-06-11 — Session 12 (Phase 4 TODOS gates: D19 + D20)

### Changes
- **TODOS.md**: Phase 4 gates marked `[x]` complete — baseline poisoning mitigation (D19) and GraphPatternRule traversal bounds (D20)
- **PHASES.md**: Phase 4 gate checkboxes updated
- **docs/AI_CONTEXT.md**: D19 and D20 added to architecture decisions; event flow updated to reflect score-conditional baseline update
- Commit hash: `0bddc9f`

### Decisions locked
- **D19 — Baseline poisoning (score-conditional update):** score first → if ≥0.4 alert + skip profile update; if <0.4 update profile. Known counterparty = first non-alerted appearance. typicalCounterparties max 50 entries (LRU eviction).
- **D20 — GraphPatternRule bounds:** 1 hop only. Fan-out ≥5 recipients/24h (0.8), fan-in ≥5 senders/24h (0.7), round-trip exact-amount within 2h (0.6). "New" = NOT IN typicalCounterparties. Query cap 100.

### Phase 4 status: TODOS gates cleared — implementation ready to start

---

## 2026-06-11 — Session 11 (Phase 3 implementation)

### Changes
- **V9__phase3_idempotency_and_correlation.sql**: creates `idempotency_key` table (unique on `(idem_key, user_id)`), adds `correlation_id VARCHAR(36)` to `ledger_transaction` with partial index
- **`LedgerTransaction.java`** (edit): added `correlationId` field
- **`AccountRepository.java`** (edit): added `findByIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`) for transfer deadlock prevention (D13)
- **`TransactionRequest.java` / `TransferRequest.java` / `TransactionResponse.java`**: request/response DTOs
- **`TransactionEvent.java` / `TransactionCompletedEvent.java`**: Spring application event carrying Kafka payload
- **`KafkaConfig.java`**: `transaction-events` topic declaration (3 partitions, 1 replica)
- **`IdempotencyKey.java` / `IdempotencyKeyRepository.java` / `IdempotencyService.java`**: Stripe-pattern idempotency — SHA-256 request hash, 24h TTL, 422 on hash mismatch, `REQUIRES_NEW` propagation, silent on duplicate-key race
- **`TransactionEventProducer.java`**: `@TransactionalEventListener(AFTER_COMMIT)` → `kafkaTemplate.send()` keyed by `userId` (D3), sets `X-Correlation-ID` header (D10)
- **`TransactionService.java`**: deposit, withdraw, transfer (pessimistic lock + fixed UUID ordering per D13, insufficient-funds 422, ownership enforcement), getTransaction, getTransactionsByAccount (paged); publishes `TransactionCompletedEvent` after DB commit
- **`TransactionController.java`**: POST /api/transactions/{deposit,withdraw,transfer} with optional `Idempotency-Key` header; GET /{id} and GET ?accountId (paged)
- **`KafkaIntegrationTest.java`**: base class with PostgreSQL Testcontainer + `@EmbeddedKafka` (in-process, no Docker for Kafka)
- **`TransactionServiceTest.java`**: 15 Mockito unit tests (deposit 4, withdraw 3, transfer 5, getTransaction 1, getTransactionsByAccount 2)
- **`TransactionIntegrationTest.java`**: 2 integration tests (deposit + withdraw) — full DB + embedded Kafka; consumer uses `auto.offset.reset=latest` + pre-produce partition-assignment poll
- **Tests: 38/38 passing** (AuthServiceTest 12, AccountServiceTest 9, TransactionServiceTest 15, TransactionIntegrationTest 2)
- Commit hash: `19e71ed`

### Phase 3 status: COMPLETE

---

## 2026-06-11 — Session 10 (Phase 2 implementation)

### Changes
- **V8__add_refresh_token_family.sql**: adds `family_id UUID NOT NULL` + index to `refresh_token`; default used for migration then dropped (safe because no seed rows in table)
- **`RefreshToken.java`**: added `familyId UUID` field
- **`RefreshTokenRepository.java`**: added `revokeAllByFamilyId(UUID familyId)` `@Modifying` query
- **`JwtService.java`**: JJWT 0.12.5 access token generation/validation; constructor-injected `secret` pre-computed to `SecretKey` at startup (not per-call)
- **`UserPrincipal.java` (record)**: `UserDetails` adapter for `User`; username = userId UUID string (matches JWT subject)
- **`UserDetailsServiceImpl.java`**: `loadUserByUsername(userId)` → `UserPrincipal`
- **`JwtAuthenticationFilter.java`**: `OncePerRequestFilter`; extracts Bearer token, sets `SecurityContext` on valid token; silently skips on invalid (no 401 thrown — filter chain continues unauthenticated)
- **`SecurityConfig.java`**: stateless, JWT filter, `HttpStatusEntryPoint(401)`, permitAll: `/api/auth/**`, Swagger, `/actuator/health/**`
- **`AuthService.java`**: register (email dedup, BCrypt), login (credentials check, disabled check), refresh (token family rotation, replay detection with `noRollbackFor = AppException.class` to ensure revocation commits), logout; opaque refresh tokens (32-byte SecureRandom → Base64Url, stored as SHA-256 hex hash)
- **`AuthController.java`**: POST /api/auth/register (201), /login, /refresh, /logout (204)
- **`AppException.java` + `ErrorResponse.java` + `GlobalExceptionHandler.java`**: D12 standard error contract; handles AppException, MethodArgumentNotValidException, fallback 500
- **`AccountService.java`**: create (12-digit account number loop until unique), list (active only), get (ownership enforced), close (idempotency check); class-level `@Transactional(readOnly=true)`, write methods override
- **`AccountController.java`**: POST /api/accounts (201), GET list, GET /{id}, DELETE /{id}
- **`AuthServiceTest.java`**: 12 tests — register (success, email conflict), login (success, not found, wrong password, disabled), refresh (success with family rotation assertion, not found, expired, replay attack), logout (found, unknown)
- **`AccountServiceTest.java`**: 9 tests — create (success, custom currency), list, get (success, not found, wrong owner), close (success, already closed, wrong owner)
- **Unit tests: 21/21 passing**
- Commit hash: `7a66217`

### Phase 2 status: COMPLETE

---

## 2026-06-11 — Session 9 (Phase 2 TODOS gate: refresh-token rotation policy)

### Changes
- **Locked refresh-token rotation policy (D4 completion)**: Token family rotation — `family_id UUID NOT NULL` on `refresh_token` table. Normal refresh: revoke old token, issue new token in same family. Replay (revoked token presented): revoke ALL tokens in that family + log security event + force re-login. TTL: 7 days. Auth0 / OAuth 2.0 Security BCP standard.
- `TODOS.md`: new "Before Phase 2" section added, gate marked `[x]` complete
- `PHASES.md`: Phase 2 TODOS gate checkbox marked `[x]` complete
- `docs/AI_CONTEXT.md`: D4 updated with full rotation policy + schema addition; project state updated to Phase 1 COMPLETE, Phase 2 TODOS gate CLEAR
- `docs/HANDOFF.md`: open design work table entry updated
- `docs/CURRENT_TASK.md`: TODOS gate marked done
- Commit hash: `3640ae9`

### Phase 2 status: TODOS gate cleared — implementation ready to start

---

## 2026-06-11 — Session 8 (Phase 1 verification + fixes)

### Changes
- **Upgraded Testcontainers BOM `1.20.1` → `1.21.4`** (`pom.xml`): resolved docker-java `3.4.0` → `3.4.2`; fixed Docker API version negotiation failure that blocked all integration tests against native Docker daemon (Engine 29.x, min API v1.44)
- **Fixed V7 seed SQL — UUID cast bug** (`V7__seed_demo_data.sql`): `CASE WHEN ... THEN 'uuid-string' END` expressions in `INSERT ... SELECT` context resolve to `text` type; PostgreSQL won't auto-cast `text → uuid`; added `::uuid` to all 5 account-ID and counterparty-ID literals in `generate_series` SELECTs
- **Fixed `CustomerRiskProfile.amountM2` column name** (`CustomerRiskProfile.java`): Hibernate 6 `CamelCaseToUnderscoresNamingStrategy` only inserts underscore before uppercase when the *next* char is also lowercase; `amountM2` → `M` followed by digit `2` (not lowercase) → produced `amountm2` instead of `amount_m2`; fixed with `@Column(name = "amount_m2")`
- **SchemaIntegrationTest passes — 4/4** (`SchemaIntegrationTest.java`): migrations V1–V7 all apply clean, Hibernate schema validation passes, seed data assertions pass (5 users, 5 accounts, 5 profiles with correct Welford stats)
- **PHASES.md**: Phase 1 marked ✅ Complete
- Commit hash: `77bf21d`

### Phase 1 status: COMPLETE

---

## 2026-06-05

### Session Start
- **Agent**: Claude Code
- **Task**: Phase 0 setup — project initialization

### Changes
- Initialized git repo at `/mnt/c/dev/ledgerbridge`
- Connected remote: `git@github.com:cs-keni/ledgerbridge.git`
- Renamed default branch to `main`
- Created `PHASES.md` with all 8 phases and task checkboxes
- Created `docs/AI_CONTEXT.md` — full stack, architecture, event flow, key design decisions
- Created `docs/HANDOFF.md` — current status, module ownership, what's next
- Created `docs/ENGINEERING_LOG.md` (this file)
- Created `docs/CURRENT_TASK.md` — active task tracking
- Stored LedgerBridge project context in gbrain for cross-session continuity

### Next
Spring Boot project initialization, Docker Compose setup, Flyway config, Logback config, SpringDoc setup. Then run `/plan-eng-review` before Phase 1.

---

### Session 2 — `/plan-eng-review` (architecture lock)
- **Agent**: Claude Code
- **Task**: Run the required Phase-1 architecture gate on the full project spec

### Changes
- Walked all 4 review sections (Architecture, Code Quality, Tests, Performance)
  via interactive AskUserQuestion decision briefs — locked **17 decisions
  (D2–D18)**, each with explicit pros/cons/recommendation. Full rationale in
  `docs/AI_CONTEXT.md` "Key Design Decisions" and gbrain page
  `ledgerbridge-risk-engine-design`.
- Notable locks: idempotency keys over Transactional Outbox (D2); Kafka
  messages keyed by `userId` (D3); DB-backed refresh-token table (D4); JSONB +
  Hypersistence Utils (D5); **Welford's online algorithm over a bounded
  recent-N window** replacing the originally-considered EWMA (D6); 
  `@RetryableTopic` for DLT (D7); `RiskRuleResult` record (D9);
  `@TransactionalEventListener(AFTER_COMMIT)` (D10); renamed `@Audited` →
  **`@AuditLog`** to avoid a silent collision with Hibernate Envers' own
  `@Audited` annotation (D11); standard `ErrorResponse` record (D12);
  pessimistic row locking with fixed lock ordering (D13); lower-bound-inclusive
  score boundaries (D14); always-log-with-outcome audit policy (D15).
- Ran Codex (gpt-5.5) as an independent "outside voice" — surfaced 20
  findings. Four created genuine cross-model tensions with decisions already
  made; resolved all four via dedicated AskUserQuestion calls, including
  **one reversal**: D8 (SSE transport) flipped from Spring WebFlux/Flux to
  **servlet `SseEmitter` + connection registry** — Codex correctly flagged
  that running both reactive and servlet stacks for a single endpoint added
  real complexity with no payoff for this scope.
- Wrote `TODOS.md` — 6 deferred design items surfaced by the outside-voice
  review, each gated to a specific phase: fraud-scenario validation strategy
  & `transaction`→`ledger_transaction` rename (before Phase 1), API-level
  idempotency keys & correlation-ID propagation (before Phase 3),
  baseline-poisoning mitigation (before Phase 4), risk-engine-specific
  Prometheus metrics (alongside Phase 7).
- Produced a failure-mode audit across 8 new codepaths — flagged **2 critical
  gaps** (the DB-commit/Kafka-publish dual-write boundary with no Outbox to
  cover it; unbounded graph traversal in `GraphPatternRule`) and **1
  underspecified item** (refresh-token reuse/rotation policy — D4 picked the
  storage mechanism but never decided replay-detection behavior).
- Updated `docs/AI_CONTEXT.md`, `docs/HANDOFF.md`, `docs/CURRENT_TASK.md`, and
  `PHASES.md` to reflect all finalized decisions and TODOS-gated checkpoints
  inline at the phases where they apply.
- Generated `tasks-eng-review-20260606-030335.jsonl` (T1–T9) covering the
  remainder of Phase 0 + the start of Phase 1.
- Logged review completion via `gstack-review-log` (status `issues_open` —
  reflecting the 6 open TODOs, not unresolved architecture questions; all 22
  decision points from this review ARE resolved).

### Next
Phase 1 is unblocked. Execute T1–T9: Spring Boot init, Docker Compose,
Flyway/SpringDoc/Logback config, then the V1 schema migration (apply the
`ledger_transaction` rename + D18 composite indexes here — cheapest point to
do it), JPA entities with JSONB mapping, and the Testcontainers scaffold.

### Commit
`79912dc` — "Set up project scaffolding and lock architecture via /plan-eng-review"
(root commit — project brief, CLAUDE.md, PHASES.md, TODOS.md, docs/ scaffolding,
all 18 locked architecture decisions).

> Note: gbrain write attempts for this session's decisions hit an internal
> Postgres I/O error (`could not read blocks... read only 0 of 8192 bytes`)
> on its storage backend — both a full write and a minimal probe page failed.
> This looks like an infrastructure issue on gbrain's side, not a content
> problem. Cross-session continuity is preserved locally in this log,
> AI_CONTEXT.md, PHASES.md, and TODOS.md; retry the gbrain write once its
> health check passes.

---

## 2026-06-10

### Session 3 — `/plan-ceo-review` (portfolio strategy lock)
- **Agent**: Claude Code
- **Task**: Run /plan-ceo-review in SELECTIVE EXPANSION mode on the full project

### Changes
- Ran `/office-hours` first (design doc created at `~/.gstack/projects/cs-keni-ledgerbridge/keni-main-design-20260610-131720.md`, status APPROVED) — validated demand evidence, confirmed Phase 4 Milestone Rule strategy
- Ran full `/plan-ceo-review` SELECTIVE EXPANSION mode — 11 review sections, Codex outside voice, 21 AskUserQuestion decisions
- **4 scope expansions accepted:** Phase 4 Swagger fraud scenarios (5 examples with per-scenario integration tests), Phase 7.5 Railway deploy (full-stack: React dashboard + Spring Boot + Railway PostgreSQL + Upstash Kafka), Phase 8 ADRs (10-15 docs/adr/ covering all 18 locked decisions), Phase 8 blog post (draft Phase 4, publish after Phase 7.5 ships)
- **Key decisions locked in this review:**
  - Upstash Kafka free tier for Railway deploy (SASL/PLAIN, Spring Kafka env vars) — Railway manages only Spring Boot + PostgreSQL
  - Demo seed data: V8__demo_seed.sql with timestamped scenario matrix (not count-based)
  - Demo auth: seeded demo@ledgerbridge.io credentials in README, no account creation required
  - Blog publish timing: after Phase 7.5 ships (live URL + screenshots), not Phase 4 day
  - Full-stack Railway: React admin dashboard + Spring Boot on one Railway service (classpath:/static/)
  - Per-scenario Testcontainers tests required as Phase 4 quality gate
- **3 new TODOS.md items:** JVM flag validation (P2, before Phase 7.5), /plan-design-review gate (P2, before Phase 6), demo seed timestamp matrix (P1, before Phase 7.5)
- **Updated:** PHASES.md (Phase 4 per-scenario tests, Phase 7.5 new phase, Phase 8 ADRs + blog), TODOS.md (+3 items), docs/designs/portfolio-strategy.md (promoted CEO plan to repo)
- **Codex outside voice:** 2 cross-model tensions — blog timing (resolved: Phase 7.5) and demo auth (resolved: seeded credentials)
- Generated `tasks-ceo-review-20260610-144616.jsonl` (T1–T6) covering deploy, seed, and test tasks
- CEO plan at `~/.gstack/projects/cs-keni-ledgerbridge/ceo-plans/2026-06-10-ledgerbridge-portfolio.md` (status: PROMOTED)

### Commit
`b9389a6` — "Lock portfolio strategy via /plan-ceo-review (SELECTIVE EXPANSION)"

### Next
Run `/plan-eng-review` fresh pass to pick up the 4 CEO review scope additions (Swagger scenarios, Railway deploy, ADRs, blog post) into the engineering architecture review. Then proceed with Phase 0 T1–T9 (Spring Boot init, Docker Compose, Flyway, SpringDoc, Logback).

---

### Session 4 — `/plan-eng-review` fresh pass (CEO additions lock)
- **Agent**: Claude Code
- **Task**: Run /plan-eng-review focused on 4 CEO cherry-picks: Swagger fraud scenarios, Phase 7.5 Railway deploy, Phase 8 ADRs, Phase 8 blog post

### Changes
- Walked all 4 review sections; outside voice (Codex gpt-5.5) run. **10 decisions locked (D1–D10 of this session):**
  - **D1**: Maven Frontend Plugin — `mvn package` compiles React + Java, Railway Dockerfile stays a single Java stage
  - **D2+D8**: `SpaFallbackController` — catches `/**` except `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/webjars/**`
  - **D3**: NormalDeposit scenario test = negative control (assert score < 0.4 + no alert)
  - **D4**: `DemoDataRefreshComponent` (@Profile("demo"), ApplicationReadyEvent + DB retry) — refreshes seed timestamps so velocity windows never go stale
  - **D5**: V8 demo seed profile-gated to `demo` Spring profile (`resources/db/demo/`, `application-demo.properties`)
  - **D6**: Demo user role = `DEMO_ACTOR` (POST `/api/transactions` + GET `/api/admin/**`, no management access)
  - **D7**: Railway health check = `/actuator/health/liveness` (NOT `/actuator/health` — prevents Kafka readiness flapping)
  - **D8**: SPA fallback Swagger path exclusion already captured above
  - **D9**: Milestone Rule confirmed at Phase 4 ship day (risk engine is the differentiator)
  - **D10**: ADR count confirmed 10-15 (each must be substantive — alternatives + consequences)
- **1 critical gap resolved**: `DemoDataRefreshComponent` must guard against DB not ready on `ApplicationReadyEvent` (retry or @DependsOn)
- **1 new TODOS item**: Upstash Kafka SASL/PLAIN connectivity spike (P2, before Phase 7.5)
- **Updated**: PHASES.md (D1-D8 folded into Phase 7.5 + D3 corrected in Phase 4), TODOS.md (+Upstash spike + V8 seed spec updated with D4-D6), docs/designs/portfolio-strategy.md (GSTACK REVIEW REPORT updated to run 2), HANDOFF.md, AI_CONTEXT.md note in HANDOFF
- **Tasks file**: `tasks-eng-review-20260610-162510.jsonl` (T1–T6) — Maven Frontend Plugin, SpaFallbackController, NormalDepositScenarioIT, DemoDataRefreshComponent, profile-gating + DEMO_ACTOR role, Railway liveness config

### Commit
`90030ac` — "Lock CEO additions via /plan-eng-review (D1–D10)"

### Next
Phase 0 coding (T1–T9): Spring Boot 3.x Maven init, Docker Compose (PostgreSQL 16 + Kafka Bitnami), Flyway config, SpringDoc, Logback + correlation-ID MDC scaffold. Then V1 schema migration (`ledger_transaction` rename + D18 composite indexes), JPA entities (Hypersistence Utils JSONB), Testcontainers scaffold.

---

### Session 5 — `/plan-design-review` (design system lock)
- **Agent**: Claude Code
- **Task**: Run /plan-design-review — lock all design decisions before Phase 6 frontend implementation

### Changes
- Ran full 7-pass design review (classifier, hierarchy, motion/animation, layout, color/type, interaction states, accessibility)
- Ran Codex (gpt-5.5) as independent outside design voice — surfaced 7 findings (no first-screen contract, wrong center of gravity, missing CTA, unearned cards, gauge underspecified, motion undefined, no design system) — all resolved
- **11 decisions locked (D1–D11):** full review pass, outside voices, layout (D3), default route (D4), demo empty state (D5), SSE indicator (D6), gauge loading/null states (D7), demo CTA (D8), severity badge pattern (D9), accessibility standard (D10), risk gauge spec (D11)
- **Score raised: 2/10 → 8/10** across 7 dimensions
- **Created `DESIGN.md`** — comprehensive design system:
  - Color tokens (`--color-bg: #111111`, `--color-surface: #1a1a1a`, sidebar `#161616`, accent `#6366f1`)
  - Severity tokens (critical `#dc2626`, high `#ea580c`, medium `#d97706`, low `#16a34a`)
  - Typography: Inter (body), Geist Mono (numeric/amounts/IDs)
  - Animation system: 150ms route fades, 40ms/Y-8px stagger, 600ms spring gauge
  - Global layout: 160px sidebar + main content
  - Full component specs: Alert Queue table (44px rows, sortable), RiskGauge (270° radial arc, 0.4 threshold), SeverityBadge (dark chip + semantic dot), SSE connection badge
  - Interaction state table: 6 features × 4 states (loading/empty/error/SSE-disconnected)
  - WCAG 2.1 AA requirements (muted token `#888888` only at ≥14px/uppercase)
- **Updated PHASES.md**: Phase 6 DESIGN gate marked done; all 9 build tasks rewritten with component specs from DESIGN.md
- **Updated TODOS.md**: `/plan-design-review` gate marked `[x]` complete
- **Updated docs/designs/portfolio-strategy.md**: GSTACK REVIEW REPORT Design Review row added (1 run, CLEAR, 11 decisions, score 2→8)
- Generated `tasks-design-review-20260610-172401.jsonl` (T1–T7) covering Phase 6 components

### Commit
`d02bcd9` — "Lock Phase 6 design system via /plan-design-review (D1–D11)"

### Next
Phase 0 coding (T1–T9): Spring Boot 3.x Maven init, Docker Compose, Flyway, SpringDoc, Logback. All 4 planning gates now cleared — implementation can begin.

---

### Session 6 — Phase 0 implementation
- **Agent**: Claude Code
- **Task**: Initialize Spring Boot project scaffold — all Phase 0 tasks

### Changes
- `pom.xml`: Spring Boot 3.3.5, Java 21, all declared dependencies (Spring Web, Data JPA, Security, Validation, Actuator, Kafka, PostgreSQL, Flyway + flyway-database-postgresql, JJWT 0.12.5, SpringDoc 2.5.0, Hypersistence 3.7.7, logstash-logback-encoder 7.4, Testcontainers 1.19.8 BOM)
- `LedgerBridgeApplication.java`: main entry point, `@SpringBootApplication`
- `LedgerBridgeApplicationTests.java`: smoke test (full @SpringBootTest context load deferred to Phase 1 with Testcontainers)
- `common/filter/CorrelationIdFilter.java`: reads/generates X-Correlation-ID, stores in MDC as `correlationId`, echoes back in response header. `@Order(1)` ensures it runs before all other filters
- `common/config/OpenApiConfig.java`: SpringDoc `OpenAPI` bean — Bearer JWT security scheme applied globally; all endpoints show lock icon and Accept the Authorize button JWT
- `application.properties`: full environment-variable-driven configuration (datasource, JPA, Flyway, Kafka, Actuator, SpringDoc, JWT). Virtual threads enabled. JWT_SECRET falls back to a dev-only unsafe value — must be overridden in prod
- `application-demo.properties`: adds `classpath:db/demo` Flyway location for demo seed data (D5)
- `logback-spring.xml`: JSON via `LogstashEncoder` in prod (correlationId, userId, transactionId as top-level JSON keys); human-readable pattern in `dev` profile; `ShortenedThrowableConverter` for compact stack traces
- `docker-compose.yml`: PostgreSQL 16-alpine + Bitnami Kafka 3.7 KRaft. Kafka dual-listener: PLAINTEXT on 9092 (container) + EXTERNAL on 9094 (host). Both have health checks. `api` service scaffolded but commented until Phase 7.5 Dockerfile
- `.env.example`: documented DB_PASSWORD, JWT_SECRET, SPRING_PROFILES_ACTIVE, Upstash Kafka env vars (Phase 7.5)
- `.gitignore`: target/, .env, IDE files, node_modules/, frontend/dist/, src/main/resources/static/
- `db/migration/.gitkeep`: Flyway migration directory placeholder
- Maven wrapper generated via `mvn wrapper:wrapper` — `mvnw` / `mvnw.cmd` / `.mvn/`
- Compilation verified with Java 17 (structure valid; Java 21 target requires JDK 21 on dev machine)

### Commit
`3758ff0` — "Add Phase 0: Spring Boot scaffold, Docker Compose, Logback, Flyway config"

### Next
Phase 1: Flyway migrations V1–V6, JPA entities (NUMERIC(19,4), UUID PKs, JSONB + Hypersistence), V7 seed data, switch ddl-auto to validate, Testcontainers scaffold. First: resolve TODOS gate items (ledger_transaction rename + fraud validation matrix design).

---

## 2026-06-11

### Session 7 — Phase 1 TODOS gates (rename + validation matrix)
- **Agent**: Claude Code
- **Task**: Knock out both Phase 1 TODOS gates before beginning Phase 1 implementation

### Changes

**Gate 1: `transaction` → `ledger_transaction` rename (design lock)**
- Confirmed `AI_CONTEXT.md` already had this correct (entity `LedgerTransaction`, migration `V3__create_ledger_transaction.sql`)
- Updated `ledgerbridge.md`: entity block renamed `Transaction` → `LedgerTransaction` with `@Table(name = "ledger_transaction")` annotation; module structure updated `Transaction.java` → `LedgerTransaction.java`; Flyway migration list updated `V3__create_transactions.sql` → `V3__create_ledger_transaction.sql`
- No code changes needed yet (table doesn't exist until Phase 1 migration runs)

**Gate 2: fraud-scenario validation matrix (new design document)**
- Created `docs/RISK_ENGINE_TEST_MATRIX.md` — full matrix with 5 scenarios:
  - S1 Normal deposit: score < 0.10, no alert (precision/negative-control)
  - S2 Velocity spike: score 0.40–0.60, MEDIUM (combined velocity + amount elevation + behavioral)
  - S3 Large amount to new counterparty: score 0.60–0.80, HIGH (single-rule escalation via AmountAnomaly 0.9)
  - S4 Fan-out pattern: score 0.60–0.80, HIGH (single-rule escalation via GraphPattern 0.8)
  - S5 Round-trip: score ≥ 0.75, CRITICAL (multi-rule escalation — 3 rules ≥ 0.6 simultaneously)
- **New design decision locked: multi-rule escalation tier** — if ≥3 rules each score ≥0.6 raw, floor total at 0.80 (CRITICAL). This is *distinct* from the existing single-rule escalation (any rule ≥0.8 → floor 0.65). Required because: (a) pure velocity can't reach 0.4 alone under these weights, so S5 needs all 4 signals converging; (b) multi-signal convergence is qualitatively stronger confidence than any single extreme signal.
- Each scenario includes: seed customer UUID, exact seed data description, per-rule raw score with calculation, full score math with escalation trace, and Java assertion code for Phase 4 Testcontainers tests
- Fixed-UUID seed customers defined: alice-normal, bob-velocity, carol-highamount, dave-fanout, eve-roundtrip

**Doc updates**
- `TODOS.md`: both Phase 1 gates marked `[x]` complete
- `PHASES.md`: both Phase 1 TODOS gate checkboxes marked `[x]` complete
- `AI_CONTEXT.md`: risk engine section updated with multi-rule escalation tier + rationale; Open Design Work table updated
- `docs/HANDOFF.md`: open design work table updated
- `docs/CURRENT_TASK.md`: both gates marked done
- Updated `ledgerbridge.md` RiskEngine pseudocode to include tier-2 escalation

### Next
Phase 1 implementation is now fully unblocked. Write Flyway migrations V1–V6, define JPA entities, write V7 seed data migration with the 5 labeled scenario customers, switch ddl-auto to validate, add Testcontainers scaffold.
