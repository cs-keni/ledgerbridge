# ENGINEERING_LOG.md — LedgerBridge

> Log every change: date, what changed, why. Add date header if missing.

---

## 2026-06-13 — Session 29 (Demo verification + V14 migration)

### Changes

- **`V14__demo_actor_accounts.sql`**: New migration — adds a CHECKING ($10,000) and SAVINGS ($25,000) account for the DEMO_ACTOR user (`demo@ledgerbridge.io`), plus a customer risk profile baseline. Without this, the Transfer page showed "No active accounts" making the demo golden path impossible.
- **Demo password corrected**: seed hash resolves to `password` (not `demo1234`); login confirmed working at `https://ledgerbridge-i0c5.onrender.com`.
- **Alerts dashboard verified**: 6 pre-seeded alerts loading correctly (CRITICAL / HIGH / MEDIUM / LOW), all status tabs present.

**Commit hash: (pending)**

---

## 2026-06-13 — Session 28 (Full codebase review — fix P1/P2/P3 findings)

### Changes

- **`TransactionService.java`**: P1 fix — `requireOwnedActiveAccount` now uses `findByIdWithLock` (PESSIMISTIC_WRITE) instead of `findById`. Concurrent deposit/withdraw to the same account previously had a lost-update race; transfer already used the lock but deposit/withdraw did not.
- **`AlertService.java`**: P2 fix — `getAlertsByStatus` now throws `AppException(400 BAD_REQUEST)` on an unrecognized status string instead of silently falling back to all-alerts.
- **`SecurityConfig.java`**: P2 fix — added `.requestMatchers("/actuator/**").hasAnyRole("ADMIN")` (after the `/actuator/health/**` permitAll rule) so `/actuator/metrics` and `/actuator/prometheus` require authentication.
- **`AccountService.java`**: P3 fix — `createAccount` now catches `DataIntegrityViolationException` on the save and retries with a fresh account number, matching the pattern used in `AlertService` and `CustomerRiskProfileService`.
- **`AuditLog.java`**: P3 fix — added `entityIdArgIndex()` attribute (default 0) so callers declare which UUID argument is the entity ID rather than relying on implicit position.
- **`AuditAspect.java`**: P3 fix — `extractEntityId` uses `argIndex` from the annotation instead of scanning for the first UUID.
- **`RiskEngine.java`**: P3 clarification — added comment that `AlertSeverity.LOW` is never produced by the live engine (only present in seeded demo data).

**Commit hash: `040d454`**

---

## 2026-06-13 — Session 27 (Phase 7.5: Fix SPA fallback + deploy live)

### Live URL
`https://ledgerbridge-i0c5.onrender.com` — Render free tier, demo profile, Supabase Postgres.

### Changes

### Changes

- **`SpaFallbackController.java`**: Replaced `/{*path}` catch-all + in-code `path.contains(".")` filter with regex-constrained path variables `/{path:[^.]*}` and `/**/{path:[^.]*}`. The regex only matches extensionless paths (frontend routes). Paths with dots (`.html`, `.js`, `.css`) fall through to the static resource handler. The old approach returned `null` for extension paths, which triggered Spring's default view name translator, caused a view resolution error, Spring Boot's error controller sent the request to `/error`, and the SPA controller forwarded back to `/index.html` — infinite loop. Fix prevents the re-entry entirely.

**Commit hashes: `629de78` (initial fix, infinite loop) → `e8a0b2d` (PathPatternParser compat)**

---

## 2026-06-12 — Session 26 (Phase 7.5: Fix V13 UUID syntax error)

### Changes

- **`db/demo/V13__demo_alerts_and_audit.sql`**: Fixed 6 invalid UUIDs. Notification IDs `n0000001-3` used `n` (not a hex digit); audit log IDs `au000001-3` used `u` (not a hex digit). Replaced with valid hex prefixes: `0b000001-3` (notifications) and `0a000001-3` (audit log). V1–V12 were already applied; V13 will re-run on next deploy.
- **`DemoDataRefreshComponent.java`**: Updated UPDATE statements to use the corrected UUIDs, matching V13.

**Commit hash: `ec4f308`**

---

## 2026-06-12 — Session 25 (Phase 7.5: Disable Kafka in demo profile)

### Changes

- **`KafkaConfig.java`**: Added `@Profile("!demo")` — prevents topic `NewTopic` beans from being created in demo, which would trigger a broker connection attempt.
- **`TransactionEventProducer.java`**: Added `@Profile("!demo")` — bean not created in demo; the `TransactionCompletedEvent` fires but no listener picks it up (no exception).
- **`TransactionRiskConsumer.java`**: Added `@Profile("!demo")` — `@KafkaListener` and `@RetryableTopic` infrastructure not registered in demo.
- **`application-demo.properties`**: Added `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration` — prevents Spring from trying to connect to a Kafka broker at startup. Kafka disabled because Upstash Kafka and CloudKarafka both deprecated/shut down in 2024–2025; no free managed Kafka tier available. Full pipeline runs locally via Docker Compose.
- **`render.yaml`**: Removed Kafka env var declarations (no longer needed).

**Commit hash: `20629ab`**

---

## 2026-06-12 — Session 24 (Phase 7.5: Supabase + Render Deploy Infrastructure)

### Changes

**Backend:**
- **`SpaFallbackController.java`**: Added explicit exclusions for `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` so API 404s don't return `index.html`. Previously only excluded paths with dots.
- **`DemoDataRefreshComponent.java`** (NEW — `common/demo/`): `@Profile("demo")` component. Fires on `ApplicationReadyEvent`. Updates `initiated_at`/`completed_at` on 6 trigger transactions, `created_at`/`reviewed_at` on 6 risk alerts, `created_at`/`read_at` on 3 notifications, and `occurred_at` on 3 audit log entries to be relative to NOW(). This keeps the demo dashboard looking fresh on every Render cold start.
- **`application-demo.properties`**: Updated Railway reference to Render. Added `spring.kafka.consumer.group-id=ledgerbridge-demo` to distinguish demo from local dev on shared Upstash brokers.

**Database (demo profile only):**
- **`db/demo/V13__demo_alerts_and_audit.sql`** (NEW): Pre-seeds 6 fixed-UUID trigger transactions (one per fraud scenario), 6 risk alerts in various states (CRITICAL/OPEN, HIGH/OPEN, HIGH/UNDER_REVIEW, MEDIUM/OPEN, MEDIUM/RESOLVED, LOW/DISMISSED), 3 notifications, and 3 audit log entries. All timestamps set relative to NOW() at Flyway run time; DemoDataRefreshComponent refreshes them on subsequent restarts.

**DevOps:**
- **`Dockerfile`** (NEW): Multi-stage — `eclipse-temurin:21-jdk-jammy` build stage (Maven + frontend-maven-plugin builds React bundle into `target/classes/static/`); `eclipse-temurin:21-jre-alpine` runtime stage. Non-root user `ledgerbridge`. Shell-form ENTRYPOINT maps Render's `${PORT}` env var to `-Dserver.port`. JVM flags: `-Xmx200m -Xms64m -XX:+UseSerialGC` (tuned for Render free 512MB RAM).
- **`render.yaml`** (NEW): Render blueprint. Docker runtime, free plan, health check at `/actuator/health/liveness`. Env vars: `SPRING_PROFILES_ACTIVE=demo`, `JWT_SECRET` (generateValue), `SPRING_DATASOURCE_*` (sync:false — set in dashboard), Upstash Kafka SASL vars.

**Commit hash: `540786f`**

---

## 2026-06-12 — Session 23 (Phase 7: Observability + DevOps)

### Changes

**Backend:**
- **`pom.xml`**: Added `micrometer-registry-prometheus` — unlocks `/actuator/prometheus` scrape endpoint (endpoint was already exposed in `application.properties` from Phase 6).
- **`RiskMetrics.java`** (NEW — `risk/metrics/`): Micrometer component wrapping three custom metrics: `risk.scoring.duration` (Timer, tagged `alert_triggered=true|false`), `risk.alerts.created` (Counter, tagged `alert_type` + `severity`), `risk.dlt.messages` (Counter, no variable tags). Metric names map to `risk_scoring_duration_seconds{quantiles}`, `risk_alerts_created_total`, `risk_dlt_messages_total` in Prometheus text format.
- **`RiskEngine.java`**: Injected `RiskMetrics`. `evaluate()` now opens a `Timer.Sample` before rule evaluation and records it in the `finally` block tagged with `alert_triggered`. Increments `risk.alerts.created` counter when an alert is persisted.
- **`TransactionRiskConsumer.java`**: Injected `RiskMetrics`. `handleDlt()` now increments `risk.dlt.messages` counter when a message exhausts all retries.
- **`RiskEngineTest.java`**: Added `@Spy RiskMetrics riskMetrics = new RiskMetrics(new SimpleMeterRegistry())` so `@InjectMocks` can satisfy the new constructor dep without needing a full Spring context.

**DevOps:**
- **`docker-compose.yml`**: Added `prometheus` (prom/prometheus:v2.53.0, port 9090) and `grafana` (grafana/grafana:11.1.0, port 3000) services. Both have named volumes for persistence. Prometheus uses `extra_hosts: host.docker.internal:host-gateway` to reach the Spring Boot app running natively on the Docker host. Grafana password configurable via `$GRAFANA_PASSWORD`.
- **`monitoring/prometheus.yml`** (NEW): Scrape config targeting `host.docker.internal:8080/actuator/prometheus` every 15s, with `instance` relabel to `ledgerbridge-api`.
- **`monitoring/grafana/provisioning/datasources/prometheus.yml`** (NEW): Auto-provisions Prometheus datasource pointing to `http://prometheus:9090`.
- **`monitoring/grafana/provisioning/dashboards/dashboard.yml`** (NEW): Dashboard file provider watching `/var/lib/grafana/dashboards/`.
- **`monitoring/grafana/dashboards/risk-engine.json`** (NEW): 6-panel Grafana dashboard — scoring latency (p50/p95/p99 timeseries), alert creation rate by type, severity pie chart, evaluations/min stat, DLT messages stat (red threshold at 1), alert vs clean transaction rate.
- **`.github/workflows/ci.yml`** (NEW): Two parallel CI jobs — `backend` (Java 21 Temurin, Maven cache, `mvn test`, Surefire artifact on failure) and `frontend` (Node 20, npm cache, `tsc --noEmit`, `npm run build`). Testcontainers works because Docker is available on `ubuntu-latest`.
- **`.env.example`**: Added `SPRING_KAFKA_BOOTSTRAP_SERVERS` and `GRAFANA_PASSWORD` entries. Updated JWT_SECRET generation hint to use `openssl rand -hex 32`.
- **`PHASES.md`**: Phase 6 `/qa` task marked complete; Phase 7 block replaced with fully checked tasks.

**Commit hash: `5d308b3`**

---

## 2026-06-12 — Session 22 (/qa: 3 bugs fixed, 6→9/10 health score)

### Changes

**Frontend — 3 bugs fixed:**
- **`TransferPage.tsx`**: Added `useEffect` to `SingleAccountForm` and `TransferForm` to sync `accountId`/`fromId` when `accounts` prop loads asynchronously. Root cause: `useState(accounts[0]?.id ?? '')` initializes to `''` on mount when React Query hasn't resolved yet; `<select value="">` visually shows the first option (browser fallback) but internal state stays `''`, causing `handleSubmit` guard to silently bail. Added `useEffect` dep on `[accounts, accountId]` to auto-select the first account when it arrives. (ISSUE-011)
- **`AlertDetailPanel.tsx` + `AlertTable.tsx` + `DashboardPage.tsx`**: Normalized `riskScore / 100` at all 3 `RiskGauge`/`ScoreChip` call sites. Both components expect 0–1 range; API returns 0–100. (ISSUE-006)
- **`vite.config.ts`**: Added `watch: { usePolling: true, interval: 300 }` under `server:` — fixes WSL2 inotify limitation on Windows filesystem, enabling Vite HMR to detect file changes.

**Backend — 1 bug fixed:**
- **`AlertController.java` + `AlertService.java`**: Fixed status filter — was hardcoded `if ("OPEN".equalsIgnoreCase(status))`. Added `getAlertsByStatus(String, Pageable)` to `AlertService` that parses any valid `AlertStatus` enum. `AlertController.list()` now routes any non-blank status to `getAlertsByStatus`, falling back to `getAlerts` on invalid enum value. (ISSUE-007)

**QA artifacts:**
- **`.gstack/qa-reports/qa-report-localhost-2026-06-12.md`** (NEW): Full QA report, health scores before/after, all issues documented.

**Commit hash: `36be7f2`**

---

## 2026-06-12 — Session 21 (/plan-design-review: 10 design fixes, 6.5→9/10)

### Changes

**Backend:**
- **`RiskAlertRepository.java`**: added `countBySeverityAndStatus(AlertSeverity, AlertStatus)` derived query.
- **`AlertStatsResponse.java`** (NEW): record DTO `{open, underReview, critical}` for system-wide alert counts.
- **`AlertService.java`**: added `getAlertStats()` — single transactional call to three count queries.
- **`AlertController.java`**: added `GET /api/admin/alerts/stats` endpoint returning `AlertStatsResponse`.

**Frontend — 10 design fixes from /plan-design-review (6.5 → 9/10):**
- **h1 size**: all admin pages `text-lg font-semibold` → `text-[20px] font-bold` (DESIGN.md spec: Inter 20px 700).
- **Stat chips**: now sourced from `GET /api/admin/alerts/stats` (system-wide) instead of current page slice. Added `fetchAlertStats()` to `api/alerts.ts`, `AlertStatsResponse` to `types/api.ts`.
- **Alert list error state**: added `isError` check with inline "Failed to load alerts / Retry" button (DESIGN.md spec).
- **Login page demo credentials**: added visible "Demo credentials" card below login form (`demo@ledgerbridge.io / password`) — portfolio critical.
- **AlertDetailPanel loading**: replaced centered spinner with skeleton shimmer (gauge arc placeholder + 4 bar rows + transaction field rows) per DESIGN.md spec.
- **RiskGauge rule bars**: bar width = raw ruleScore (0–100%), label = weighted contribution (`+0.27`) with tooltip showing calculation. Previously: width = weighted contribution, label = raw score → confusing.
- **Sidebar alert count badge**: indigo pill badge showing OPEN alert count next to "Risk Alerts" nav item, sourced from `alert-stats` query. SSE event invalidates the cache to keep count live.
- **Dashboard avg score color**: `scoreColor()` helper applied dynamically instead of hardcoded amber.
- **Transfer submit button**: `bg-accent text-white` → `bg-[#2a2a2a] text-text hover:bg-[#333]` (DESIGN.md: accent = hover/active only, never as fill).
- **ActionBtn focus rings**: added `focus:outline-none focus:ring-1 focus:ring-accent-light/60` to Review/Dismiss/Resolve buttons in AlertDetailPanel.
- **Content max-width**: removed `max-w-5xl mx-auto` from AlertsPage, DashboardPage, AuditLogPage, AccountsPage. TransferPage keeps `max-w-lg` (form width constraint is intentional). Full-width content on large monitors.
- **SSE invalidation**: `useAlertStream` callback now invalidates `['alert-stats']` in addition to `['alerts']` so count badge stays live.

**Commit hash: `830d5e3`**

---

## 2026-06-12 — Session 20 (Phase 6: Transfer form + WCAG 2.1 AA)

### Changes

**Transfer form (T remaining):**
- **`api/transactions.ts`** (NEW): `deposit`, `withdraw`, `transfer` — each auto-generates a UUID v4 `Idempotency-Key` header per request. `api.post` extended to accept `extraHeaders`.
- **`api/accounts.ts`**: fixed `fetchTransactions` signature to accept required `accountId` param (backend requires `?accountId=` query param).
- **`pages/admin/TransferPage.tsx`** (NEW): 3-tab form (Deposit / Withdraw / Transfer). Account dropdown populated from `fetchAccounts`. Idempotency key auto-generated. Success card with spring-in confirmation showing transaction number, amount, status. `useId()` for all label–input pairs (WCAG 2.1 AA 1.3.1). `noValidate` with custom error message (aria `role="alert"`). Spinner on submit.
- **`App.tsx`**: added `/transfer` route under AdminLayout.
- **`Sidebar.tsx`**: added Transfer nav link between Audit Log and Accounts.

**WCAG 2.1 AA sweep:**
- `LoginPage.tsx` + `RegisterPage.tsx`: fixed post-login redirect from `/admin/alerts` → `/alerts` (route mismatch would have sent users to 404).
- `ProtectedRoute.tsx`: added `role="status"` + `aria-label="Loading"` on spinner container; `aria-hidden="true"` on spinner div.
- `AlertDetailPanel.tsx`: close button gets `autoFocus` after 310ms (after CSS transition ends) via `useEffect`; `Escape` keydown listener closes panel; close button has `focus:ring-2` focus indicator.
- All form inputs: `useId()` for stable label–input ID pairs; `aria-describedby` wired to error messages; `role="alert"` on error paragraphs.
- Contrast: `text-muted` (#888888) on bg (#111111) = 5.35:1 ✓ AA; on surface (#1a1a1a) = 4.93:1 ✓ AA; both pass WCAG AA for normal text.
- AlertTable: keyboard nav already present (`tabIndex={0}`, Enter/Space handlers, `aria-selected`).
- Landmarks: `<nav aria-label>`, `<main id="main">`, `<aside aria-label>` all in place from prior sessions.

---

## 2026-06-12 — Session 19 (Phase 6 T9–T14: full admin frontend)

### Changes

**T9–T14 — Admin frontend (AlertTable, AlertDetailPanel, RiskGauge, AlertsPage, AuditLogPage, DashboardPage, AccountsPage, App.tsx):**
- **`api/client.ts`** (NEW): `apiFetch` wrapper — adds `Authorization: Bearer` header, handles 401 with `silentRefresh()` + retry, throws typed `ApiError`. `api.get/post/patch/del` helpers.
- **`api/alerts.ts`** (NEW): `fetchAlerts`, `fetchAlertDetail`, `reviewAlert`.
- **`api/audit.ts`** (NEW): `fetchAuditLog` (paginated `Page<AuditLogResponse>`).
- **`api/accounts.ts`** (NEW): `fetchAccounts` (plain `AccountResponse[]` — backend returns list not page), `fetchTransactions`.
- **`stores/sseStore.ts`** (NEW): Zustand atom for SSE connection status (`connected` / `reconnecting` / `disconnected`).
- **`hooks/useSpring.ts`** (NEW): Euler spring animation hook — stiffness=100, damping=20 (critically damped), dt capped at 50ms, settles when |x−target| < 0.001 && |v| < 0.005.
- **`hooks/useAlertStream.ts`** (NEW): SSE over fetch+ReadableStream (NOT EventSource — can't send JWT headers). Parses event/data/blank-line SSE protocol; ignores `:heartbeat` comments. JWT-authenticated, 401→silentRefresh+reconnect, exponential backoff 1s→30s. Re-subscribes on token change.
- **`components/layout/Sidebar.tsx`** (NEW): NavLink nav for `/alerts`, `/audit`, `/accounts`, `/dashboard`. SSE status dot on Risk Alerts (green pulse = connected, amber pulse = reconnecting). Logout: POST /api/auth/logout (best-effort) → clearAuth → /login.
- **`components/layout/AdminLayout.tsx`** (NEW): Sidebar + Outlet layout.
- **`components/alerts/SeverityBadge.tsx`** (NEW): dark chip (#1e1e1e bg, #3a3a3a border) with semantic dot + colored label per DESIGN.md.
- **`components/alerts/ScoreChip.tsx`** (NEW): 24×24 SVG mini arc (same 135°/270° geometry as RiskGauge), severity-colored fill, static (no spring for table perf).
- **`components/risk/RiskGauge.tsx`** (NEW): SVG 200×160, R=76, START=135°, SWEEP=270°. Spring-animated arc (useSpring), 3-segment gradient (green/amber/red at 0.3/0.6). Threshold marker at score 0.4. Center: animated score (28px Geist Mono) + severity label. Rule bars: amountAnomaly(0.25), velocity(0.30), behavioral(0.20), graphPattern(0.25) — bar width = ruleScore×weight.
- **`components/alerts/AlertTable.tsx`** (NEW): sortable table (Score/AlertType/Severity/Status/Time), skeleton shimmer loading, empty state with Swagger link, `alert-arrive` animation for fresh rows.
- **`components/alerts/AlertDetailPanel.tsx`** (NEW): 380px slide-in panel (translateX transition 300ms). RiskGauge + transaction details + Review/Dismiss/Resolve action buttons via useMutation. Backdrop overlay.
- **`pages/admin/AlertsPage.tsx`** (NEW): stat chips, filter tabs, AlertTable, pagination, SSE via `useAlertStream` (invalidates query cache on each alert). "Try a Demo Scenario →" CTA.
- **`pages/admin/AuditLogPage.tsx`** (NEW): paginated audit log table with skeleton shimmer and OutcomeBadge.
- **`pages/admin/DashboardPage.tsx`** (NEW): KPI row (open/critical/avg score/total balance), recent alerts list, accounts summary — links to detail pages.
- **`pages/admin/AccountsPage.tsx`** (NEW): plain list table (backend returns `AccountResponse[]`, not paginated).
- **`App.tsx`** (UPDATED): added `AdminLayout` + all admin routes (`/alerts`, `/audit`, `/accounts`, `/dashboard`). Default `/` → `/alerts`.
- **`stores/authStore.ts`**: login endpoint fixed to `/api/auth/login` (not `/authenticate`).
- **TypeScript**: 0 errors — `tsc --noEmit` passes clean.

---

## 2026-06-12 — Session 18 (Phase 6 Lane A + Lane B: backend fixes + frontend scaffold)

### Changes

**Lane A — Backend fixes (T1–T4, T17):**
- **`SecurityConfig.java`**: added `/api/admin/**` → `hasAnyRole("ADMIN", "DEMO_ACTOR")` before the catch-all; changed `anyRequest().authenticated()` → `anyRequest().permitAll()` so SPA routes and static assets are served without auth redirect.
- **`V12__add_demo_actor.sql`** (NEW): Flyway migration inserts DEMO_ACTOR user `demo@ledgerbridge.io` (password "password", same BCrypt hash as V7 seed users) — Phase 6 local testing credential.
- **`risk/dto/AlertDetailResponse.java`** (NEW): enriched DTO record joining `RiskAlert + LedgerTransaction + Account`. Exposes transaction amount, currency, type, accountId, accountNumber, counterpartyAccountId, description, merchantCategory, transactionInitiatedAt — required for the alert detail panel.
- **`risk/service/AlertService.java`**: injected `TransactionRepository` + `AccountRepository`; `getAlertById()` now returns `AlertDetailResponse` by walking alert → transaction → account. Null-safe on account (returns null accountNumber if account row missing).
- **`risk/controller/AlertController.java`**: updated `getById()` return type to `ResponseEntity<AlertDetailResponse>`.
- **`audit/service/AuditService.java`**: added `listAll(Pageable)` using `repository.findAll(pageable)` (JpaRepository base method).
- **`audit/controller/AuditController.java`**: made `entityType` + `entityId` `@RequestParam(required = false)`; dispatches to `getByEntity()` when both present, `listAll()` otherwise. Enables the frontend audit log page without a required entity filter.
- **`risk/service/SseAlertService.java`**: SSE_TIMEOUT_MS removed; emitters now created with `-1L` (no timeout). Added 15s heartbeat via `ScheduledExecutorService` (daemon thread, `@PostConstruct` start, `@PreDestroy` shutdown) — sends `SseEmitter.event().comment("heartbeat")` every 15s to keep connections alive through proxies.
- **`SchemaIntegrationTest.java`**: updated `seed_data_has_five_scenario_users` count assertion from 5 to 6 (V12 added DEMO_ACTOR as 6th user).

**Lane B — Frontend scaffold + build plugin (T5–T8, T15, T16):**
- **`pom.xml`**: added `frontend-maven-plugin` (com.github.eirslett 1.15.1) bound to `prepare-package` phase — Node 20 LTS install → npm install → vite build → `target/classes/static/`. Bound to `prepare-package` (not `generate-resources`) so `./mvnw clean test` skips the build.
- **`common/config/SpaFallbackController.java`** (NEW): catches extension-free paths `/{path:[^\\.]*}` and `/**/{path:[^\\.]*}` — forwards to `/index.html`. Extension-free pattern avoids intercepting `.js`/`.css`/`.ico` static assets (which have dots in filename), leaving those to Spring's `ResourceHttpRequestHandler`.
- **`frontend/package.json`** (NEW): React 18.3.1, TypeScript 5.5.3, Vite 5.4, Tailwind CSS 3.4.7, @tanstack/react-query v5.59, Zustand v4.5.5, react-router-dom v6.27, @fontsource/inter + @fontsource/geist-mono.
- **`frontend/vite.config.ts`** (NEW): `outDir: ../target/classes/static`, `emptyOutDir: true`, dev proxy `/api → localhost:8080`.
- **`frontend/tailwind.config.ts`** (NEW): extends theme with all DESIGN.md tokens (colors, font families, sidebar spacing, shake/shimmer/fade-in-up/slide-in-right/alert-arrive keyframe animations).
- **`frontend/tsconfig.json` + `tsconfig.node.json`** (NEW): strict TS config, bundler module resolution, noEmit for Vite.
- **`frontend/postcss.config.js`** (NEW): tailwindcss + autoprefixer.
- **`frontend/index.html`** (NEW): minimal SPA entry.
- **`frontend/src/main.tsx`** (NEW): React 18 `createRoot`, `QueryClientProvider` (staleTime 30s, retry 1).
- **`frontend/src/index.css`** (NEW): Tailwind directives + CSS custom properties for all DESIGN.md color tokens.
- **`frontend/src/App.tsx`** (NEW): `BrowserRouter` + `Routes` — login, register, `ProtectedRoute` wrapper, fallback `→ /login`.
- **`frontend/src/types/api.ts`** (NEW): TypeScript types for `RiskAlertResponse`, `AlertDetailResponse`, `Page<T>`, `AuthResponse`, `LoginRequest`, `RegisterRequest`, `AuditLogResponse` — all matching backend DTO shapes.
- **`frontend/src/stores/authStore.ts`** (NEW): Zustand store — access token in memory (never localStorage), refresh token in `localStorage` under key `lb_refresh_token`. `silentRefresh()` calls `/api/auth/refresh` on boot; sets `isInitialized` flag when done (true or false — used by `ProtectedRoute` to hold render until auth is known).
- **`frontend/src/components/auth/ProtectedRoute.tsx`** (NEW): checks `isInitialized`; shows spinner while silent refresh is pending; redirects to `/login` if no access token.
- **`frontend/src/pages/LoginPage.tsx`** (NEW): centered card, always-visible labels, WCAG 2.1 AA focus rings, spinner on submit, shake animation on bad credentials.
- **`frontend/src/pages/RegisterPage.tsx`** (NEW): same design pattern as login, first/last name fields.
- **`DESIGN.md`**: updated SSE connection state section — "Browser `EventSource` auto-reconnects" → `useAlertStream` hook (fetch + ReadableStream with JWT header + exponential backoff).

### Test results
- **89/89 tests passing** — all backend tests green with V12 migration

---

## 2026-06-12 — Session 17 (Phase 5: Admin + Audit)

### Changes

- **`pom.xml`**: added `spring-boot-starter-aop` dependency (aspectjweaver required for `@Aspect` proxy)
- **`V11__add_audit_outcome.sql`** (NEW): adds `outcome VARCHAR(100)` column to `audit_log` (D15 — always-fire including failures)
- **`audit/model/AuditLog.java`**: added `outcome` field + getter/setter via Lombok
- **`common/audit/AuditLog.java`** (NEW): `@AuditLog` annotation (D11 — named to avoid Hibernate Envers collision). Carries `action` (AuditAction) and optional `entityType`.
- **`common/audit/AuditAspect.java`** (NEW): `@Around` advice on `@AuditLog`-annotated methods. Fires always including exceptions (D15). Extracts entityId from first UUID arg; userId from SecurityContextHolder; IP from RequestContextHolder (null-safe for Kafka threads); correlationId from MDC. Calls `AuditService.record()` with `REQUIRES_NEW` propagation so failures in the calling tx don't suppress the log.
- **`audit/service/AuditService.java`** (NEW): `record()` (REQUIRES_NEW), `getByEntity()`, `getByUser()` — thin service over `AuditLogRepository`.
- **`audit/dto/AuditLogResponse.java`** (NEW): record DTO projecting all audit_log fields.
- **`audit/controller/AuditController.java`** (NEW): `GET /api/admin/audit-log?entityType=&entityId=` + `GET /api/admin/audit-log/user/{userId}`.
- **`risk/service/SseAlertService.java`** (NEW): D8 — `SseEmitter`-based broadcaster. `CopyOnWriteArrayList` registry with `onTimeout`/`onCompletion`/`onError` cleanup callbacks (D2 — no leaked emitters). 30s timeout. `broadcast()` called from `AlertService.createAlert()` after save.
- **`risk/dto/AlertReviewRequest.java`** (NEW): `{status: AlertStatus, notes: String}` PATCH body.
- **`risk/service/AlertService.java`**: added `SseAlertService` dep; `createAlert()` now broadcasts via SSE after save; new `getAlertById()` + `reviewAlert()` methods. `reviewAlert()` annotated `@AuditLog(action = ALERT_REVIEWED)` — writes to audit log on every admin review including failures.
- **`risk/controller/AlertController.java`** (NEW): `GET /api/admin/alerts`, `GET /api/admin/alerts/{id}`, `PATCH /api/admin/alerts/{id}/review`, `GET /api/admin/alerts/stream` (SSE).
- **`notification/dto/NotificationResponse.java`** (NEW): record DTO with `read` boolean derived from `readAt`.
- **`notification/service/NotificationService.java`** (NEW): `getForUser()`, `countUnread()`, `markRead()` (ownership-enforced, idempotent re-mark).
- **`notification/controller/NotificationController.java`** (NEW): `GET /api/user/notifications`, `GET /api/user/notifications/unread-count`, `PATCH /api/user/notifications/{id}/read`.
- **`risk/service/CustomerRiskProfileService.java`**: TODOS.md Phase 5 — added `saveRiskScore()` method; writes `currentRiskScore` and `riskTier` (LOW/MEDIUM/HIGH/CRITICAL) derived from score thresholds (0.3/0.6/0.8).
- **`risk/consumer/TransactionRiskConsumer.java`**: calls `profileService.saveRiskScore()` after every evaluation (TODOS.md Phase 5 — persists latest score+tier regardless of alert status).

### Test results
- **89/89 tests passing** (3 new AuditAspect tests added: success outcome, failure outcome, entityType attribute override)
- Commit hash: `8da0dfc`

---

## 2026-06-12 — Session 16 (P2 fixes: T7/T8/T9 — velocity + fan-in correctness)

### Changes
- **`VelocityRule.java`**: T7 — added 7-day spike detection using `lastWeek` (already fetched but unused). Weekly threshold: `max(20, avgPerDay*7*2.0)`. Scoring: `weekSpike only → 0.3`; `daySpike + weekSpike → 0.5` (sustained multi-day pattern escalates). Also added floor to `dailyThreshold = max(4, dailyBaseline * 2.5)` — prevents false positives on new users while EWMA converges (T8 companion fix).
- **`CustomerRiskProfileService.java`**: T8 — added EWMA inter-arrival velocity baseline update. Uses `profile.lastUpdated` (timestamp of previous profile save) and `event.initiatedAt()` to compute inter-arrival time. Clamps to [1min, 7d]. Alpha = 2/(min(N,30)+1). Skips first transaction (newCount < 2, no prior arrival to diff against).
- **`TransactionRepository.java`**: T9 — added `countDistinctSendersSince(receiverAccountId, since)` native SQL query: counts distinct `account_id` values WHERE `counterparty_account_id = :receiverAccountId` (true fan-in direction). Prior `countDistinctNewCounterpartiesSince` was counting the counterparty's outbound — semantically backwards.
- **`GraphPatternRule.java`**: T9 — fan-in now calls `countDistinctSendersSince` instead of `countDistinctNewCounterpartiesSince`. No exclusion list needed for fan-in (we don't have the receiver's `typicalCounterparties` loaded).
- **`VelocityRuleTest.java`**: added `weekSpikeOnly_scorePoint3` and `daySpikeAndWeekSpike_escalatesToPoint5` tests; overloaded `stubCounts` to accept 3-arg form.
- **`GraphPatternRuleTest.java`**: updated `stubFanIn` to stub `countDistinctSendersSince`.

### Test results
- **86/86 tests passing** (2 new VelocityRule tests added)
- Commit hash: `6bff060`

---

## 2026-06-12 — Session 15 (/review gate: 8 critical fixes, 84/84 tests green)

### Changes — /review gate critical fixes (T1–T6, T10, +NEW)

- **`TransactionRepository.java`**: T1 — added `AND type = 'TRANSFER_DEBIT'` to `existsRoundTrip` native SQL; T2 — converted `countDistinctNewCounterpartiesSince` to native SQL with `LIMIT 100` subquery (Hibernate JPQL NOT IN with LocalDateTime params had the same UTC-offset bug as the prior session's velocity queries)
- **`GraphPatternRule.java`**: T6 — added sentinel UUID guard (`"00000000-0000-0000-0000-000000000000"`) for empty `knownCounterparties`; prevents Hibernate 6 `IllegalArgumentException` on `NOT IN ()` — crashed as DLT for every new user before this fix
- **`CustomerRiskProfileService.java`**: T5 — fixed hour frequency initial value from `1.0` to `1.0 / newCount` (Welford hour histogram was neutered after first observation); T4 — added `DataIntegrityViolationException` catch in `getOrCreate()` with re-fetch to resolve TOCTOU race on concurrent user first-transaction
- **`AlertService.java`**: T3 — added `DataIntegrityViolationException` catch in `createAlert()` with re-fetch via `findByTransactionId()` (concurrent Kafka retry duplicates blocked by DB UNIQUE on `transaction_id`)
- **`RiskAlertRepository.java`**: T3 — added `Optional<RiskAlert> findByTransactionId(UUID)` used by `AlertService` DIV catch
- **`risk/model/ProcessedTransactionEvent.java`** (NEW): T10 — JPA entity for full consumer idempotency; `transaction_id UUID PK`
- **`risk/repository/ProcessedTransactionEventRepository.java`** (NEW): T10 — `existsByTransactionId()` replaces the prior `riskAlertRepository.existsByTransactionId()` guard (which only protected alerted transactions — clean redeliveries re-ran Welford + double-incremented)
- **`TransactionRiskConsumer.java`**: T10 — rewrote to use `ProcessedTransactionEventRepository`; idempotency guard now covers ALL deliveries (clean + alerted); marks processed AFTER evaluation with DIV catch for concurrent retry race
- **`KafkaConfig.java`**: NEW CRITICAL — added `transactionEventsRetry0Topic`, `transactionEventsRetry1Topic`, `transactionEventsDltTopic` beans; `@RetryableTopic` sets `autoCreateTopics=false` so these must be provisioned explicitly; without this, Kafka retries silently fail on brokers with `auto.create.topics.enable=false`
- **`V10__risk_alert_unique_txn_and_processed_events.sql`** (NEW): T3 — `UNIQUE (transaction_id)` on `risk_alert`; T10 — `processed_transaction_event` table
- **`AmountAnomalyRuleTest.java`** / **`RiskEngineTest.java`**: pinned `LocalDateTime.now()` to deterministic fixed values to eliminate flake from wall-clock drift

### Test results
- **84/84 tests passing** — all unit + integration tests including `RiskScenarioIntegrationTest` 5/5 and `SchemaIntegrationTest` 4/4
- `Could not configure topics` logged by `KafkaAdmin` at test startup is benign — tests use embedded Kafka; broker connection times out but tests proceed correctly
- Commit hash: `b66c7ab`

---

## 2026-06-11 — Session 14 (Phase 4 integration tests: all 84 tests green)

### Changes
- **`TransactionRepository.java`**: converted `countVelocityWindows` and `existsRoundTrip` from JPQL to `nativeQuery = true`. Root cause: `hibernate.jdbc.time_zone=UTC` causes Hibernate to treat `LocalDateTime` parameters as JVM-local time and shift to UTC before binding — a 7-hour gap on Pacific JVM that moved 1-hour and 2-hour windows out of range for S2 and S5.
- **`pom.xml`**: added `maven-surefire-plugin` configuration with `<argLine>-Duser.timezone=UTC</argLine>`. This is the canonical fix: running the test JVM in UTC makes `hibernate.jdbc.time_zone=UTC` a no-op (JVM-local = UTC → no offset applied). Production deployments should also run the JVM in UTC.
- **`RiskScenarioIntegrationTest.java`**: added `@DirtiesContext(classMode = AFTER_CLASS)` + `import`. Tests insert extra accounts/transactions; dirtying the context ensures Testcontainers starts a fresh container for `SchemaIntegrationTest`, preventing the container lifecycle conflict that caused `SchemaIntegrationTest` to fail when run together.

### Test results
- **84/84 tests passing** (full suite: unit + all integration tests including both `RiskScenarioIntegrationTest` 5/5 and `SchemaIntegrationTest` 4/4)
- Commit hash: `a1b04a9`
- S2 velocity (0.46 MEDIUM) ✓, S5 round-trip (0.80 CRITICAL) ✓

### Root cause retrospective
Two bugs masked each other across S2 and S5:
1. `hibernate.jdbc.time_zone=UTC` + JVM in Pacific: JPQL/native `LocalDateTime` params shifted +7h before DB comparison. 1-hour velocity window and 2-hour round-trip window both fell outside the query range.
2. Fix #1 (native SQL) was correct but insufficient: Hibernate's PARAMETER BINDING (not query syntax) was the source of the offset, so native SQL queries had the same issue.
3. Final fix: JVM timezone = UTC eliminates the JVM-to-UTC offset entirely. Both JdbcTemplate inserts and Hibernate queries use UTC values as-is.

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
