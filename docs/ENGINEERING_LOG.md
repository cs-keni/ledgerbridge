# ENGINEERING_LOG.md — LedgerBridge

> Log every change: date, what changed, why. Add date header if missing.

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
