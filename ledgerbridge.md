# LedgerBridge — AI Agent Brief

> Read this entirely before writing a single line of code.
> This is the canonical project brief for LedgerBridge. All planning, architecture, and implementation decisions live here and in the docs/ folder once set up.

---

## Why This Project Exists

Kenny Nguyen (GitHub: cs-keni) is a CS grad from University of Oregon (2025) targeting software engineering roles at banking, fintech, and enterprise backend companies. Primary targets include Wells Fargo, Citi, Capital One, Chase/JPMorgan, US Bank, and large-scale software engineering roles in Oregon and the Pacific Northwest.

These employers want:
- Java/Spring Boot (the lingua franca of enterprise backend)
- Event-driven architecture (Kafka, message queues)
- Production-quality relational database design
- Security-first API design (Spring Security, JWT, RBAC)
- Docker multi-service deployments
- CI/CD and observability patterns

LedgerBridge is purpose-built to demonstrate this exact stack at a level that would impress a senior engineer at any of these companies. The key differentiator is that the **risk detection engine uses real statistical anomaly detection** — not naive threshold rules. There are hundreds of "I built a banking app" portfolio projects. LedgerBridge's risk engine needs to make a senior fintech engineer stop and read it.

---

## Project Overview

**LedgerBridge** is a banking transaction and risk monitoring system with an event-driven architecture. Users manage bank accounts, initiate transactions and transfers, and the system asynchronously analyzes every transaction for fraud and risk signals using statistical anomaly detection and behavioral baselining.

The system is designed as a **modular monolith** — clean module boundaries, designed as if the modules could later be extracted into microservices, but deployed as a single application. This is the practical production pattern at most banks right now, and it's the right architectural starting point.

### User Roles
- **User** — owns accounts, initiates transactions and transfers
- **Admin** — reviews risk alerts, manages user accounts, views audit logs

### Core Modules
1. **Account Module** — account lifecycle (create, view, close), balance management
2. **Transaction Module** — transaction and transfer processing, history, receipts
3. **Risk Module** — anomaly detection engine, alert management, admin review queue
4. **Audit Module** — append-only audit log for all sensitive actions
5. **Notification Module** — alert delivery (email mock or real), in-app notification queue

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT (JJWT library) |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Message Broker | Apache Kafka (via Bitnami Kafka Docker image) |
| Kafka Client | Spring Kafka |
| Frontend | React 18 + TypeScript + Tailwind CSS |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Containerization | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Build Tool | Maven (or Gradle — choose one and be consistent) |
| Logging | SLF4J + Logback (structured JSON output) |
| Optional | Prometheus + Grafana for observability |
| Optional | Redis for rate limiting / session cache |

---

## Architecture

### Module Structure

```
ledgerbridge/
├── src/main/java/com/ledgerbridge/
│   ├── account/
│   │   ├── controller/          AccountController.java
│   │   ├── service/             AccountService.java
│   │   ├── repository/          AccountRepository.java
│   │   ├── dto/                 CreateAccountRequest.java, AccountResponse.java
│   │   └── model/               Account.java
│   ├── transaction/
│   │   ├── controller/          TransactionController.java
│   │   ├── service/             TransactionService.java
│   │   ├── repository/          TransactionRepository.java, TransactionEventRepository.java
│   │   ├── kafka/               TransactionEventProducer.java
│   │   ├── dto/
│   │   └── model/               LedgerTransaction.java, TransactionEvent.java
│   ├── risk/
│   │   ├── consumer/            TransactionRiskConsumer.java  — Kafka consumer
│   │   ├── engine/              RiskEngine.java               — core detection logic
│   │   ├── rules/               AmountAnomalyRule.java, VelocityRule.java,
│   │   │                        BehavioralBaselineRule.java, GraphPatternRule.java
│   │   ├── service/             AlertService.java
│   │   ├── repository/          AlertRepository.java, CustomerProfileRepository.java
│   │   ├── dto/
│   │   └── model/               RiskAlert.java, CustomerRiskProfile.java
│   ├── audit/
│   │   ├── service/             AuditService.java
│   │   ├── repository/          AuditLogRepository.java
│   │   └── model/               AuditLog.java
│   ├── notification/
│   │   ├── service/             NotificationService.java
│   │   └── model/               Notification.java
│   ├── auth/
│   │   ├── controller/          AuthController.java
│   │   ├── service/             AuthService.java, JwtService.java
│   │   └── model/               User.java, Role.java
│   └── common/
│       ├── config/              SecurityConfig.java, KafkaConfig.java
│       ├── exception/           GlobalExceptionHandler.java
│       └── audit/               AuditAspect.java  — AOP-based auto audit logging
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/            V1__initial_schema.sql, V2__seed_data.sql
└── frontend/                    React + TypeScript app
    ├── src/
    │   ├── pages/
    │   ├── components/
    │   ├── services/            API client layer (Axios + React Query)
    │   ├── store/               Zustand state management
    │   └── types/
    └── package.json
```

### Event-Driven Flow

```
User initiates transaction
         ↓
TransactionService validates, persists Transaction
         ↓
TransactionEventProducer publishes to Kafka topic: "transaction-events"
         ↓
TransactionRiskConsumer reads from "transaction-events"
         ↓
RiskEngine runs all rules against the transaction
         ↓
  [No flag] → update CustomerRiskProfile baseline
  [Flagged]  → create RiskAlert → AlertService persists → NotificationService queues alert
         ↓
Admin Review Queue receives alert in real time (Server-Sent Events or WebSocket)
```

---

## Domain Model

### Entities

```java
// Account
@Entity
Account {
  UUID id
  String accountNumber  // ACC-XXXXXXXXXX (formatted)
  UUID userId
  AccountType type      // CHECKING, SAVINGS, MONEY_MARKET
  AccountStatus status  // ACTIVE, FROZEN, CLOSED
  BigDecimal balance
  String currency       // ISO 4217, default "USD"
  LocalDateTime openedAt
  LocalDateTime closedAt
}

// LedgerTransaction  (table: ledger_transaction — NOT "transaction"; SQL reserved word)
@Entity
@Table(name = "ledger_transaction")
LedgerTransaction {
  UUID id
  String transactionNumber  // TXN-YYYYMMDD-XXXXXXXX
  UUID accountId
  UUID counterpartyAccountId  // nullable (deposits/withdrawals have no counterparty)
  TransactionType type  // DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT
  BigDecimal amount
  String currency
  TransactionStatus status  // PENDING, COMPLETED, FAILED, REVERSED
  String merchantCategory   // MCC code (nullable for P2P transfers)
  String description
  LocalDateTime initiatedAt
  LocalDateTime completedAt
  String ipAddress
  String deviceFingerprint  // hashed
}

// CustomerRiskProfile
// The most important table for demonstrating algorithmic thinking
@Entity
CustomerRiskProfile {
  UUID id
  UUID userId

  // Rolling statistics (updated after each transaction)
  BigDecimal avgTransactionAmount30d    // exponentially weighted moving average
  BigDecimal stdDevTransactionAmount30d // rolling standard deviation
  Double avgTransactionsPerHour30d
  Double avgTransactionsPerDay30d

  // Behavioral baseline
  String typicalTransactionHours    // JSON: {"0":0.02, "9":0.15, "14":0.22, ...}  hour → frequency
  String typicalMerchantCategories  // JSON: {"5411":0.3, "5812":0.2, ...}  MCC → frequency
  String typicalCounterparties      // JSON: set of known counterparty account IDs

  // Risk score
  Double currentRiskScore  // 0.0 - 1.0
  RiskTier tier            // LOW, MEDIUM, HIGH, CRITICAL
  LocalDateTime lastUpdated
  Integer totalTransactionsAnalyzed
}

// RiskAlert
@Entity
RiskAlert {
  UUID id
  String alertNumber  // ALT-YYYYMMDD-XXXXXX
  UUID transactionId
  UUID userId
  AlertType alertType  // AMOUNT_ANOMALY, VELOCITY_ANOMALY, BEHAVIORAL_ANOMALY, GRAPH_PATTERN
  AlertSeverity severity  // INFO, LOW, MEDIUM, HIGH, CRITICAL
  AlertStatus status  // OPEN, UNDER_REVIEW, DISMISSED, ESCALATED, RESOLVED
  Double riskScore  // 0.0 - 1.0, the score that triggered this alert
  String ruleDetails  // JSON: which rules fired, with scores and thresholds
  UUID reviewedByAdminId  // nullable
  String adminNotes
  LocalDateTime createdAt
  LocalDateTime reviewedAt
}

// AuditLog (append-only)
@Entity
AuditLog {
  UUID id
  String entityType
  UUID entityId
  AuditAction action  // CREATE, UPDATE, DELETE, LOGIN_SUCCESS, LOGIN_FAILED, PERMISSION_DENIED, ALERT_REVIEWED
  UUID userId  // nullable for system actions
  String oldValues  // JSON snapshot
  String newValues  // JSON snapshot
  String ipAddress
  LocalDateTime occurredAt
}
```

---

## Risk Detection Engine — The Core Differentiator

This is what makes LedgerBridge stand out. The risk engine uses **four distinct algorithmic rules** that can each contribute a score. A transaction's total risk score is a weighted sum of all triggered rules. If the score exceeds a threshold, an alert is created.

### Rule 1: Amount Anomaly (Z-Score)

```java
// AmountAnomalyRule.java
// Uses the customer's rolling mean and std dev (stored in CustomerRiskProfile)
// to compute a z-score for the transaction amount.

double zScore = (transaction.amount - profile.avgAmount30d) / profile.stdDevAmount30d;

// Z-score interpretation:
// z < 2.0  → normal
// 2.0-2.9  → slightly unusual → score: 0.3
// 3.0-3.9  → unusual          → score: 0.6
// 4.0+     → very unusual     → score: 0.9

// Edge cases:
// If stdDev is 0 (new customer, all same amounts) → use a population default
// If customer has <10 transactions → use conservative defaults, flag as LOW confidence
// First transaction for a new account → always flag as INFO with AMOUNT_ANOMALY
```

### Rule 2: Velocity Anomaly (Sliding Window)

```java
// VelocityRule.java
// Counts transactions in sliding time windows and compares against the customer baseline.

// Fetch counts:
int txnLast1Hour = transactionRepository.countByAccountIdAndTimeAfter(accountId, now.minusHours(1));
int txnLast24Hours = transactionRepository.countByAccountIdAndTimeAfter(accountId, now.minusDays(1));
int txnLast7Days = transactionRepository.countByAccountIdAndTimeAfter(accountId, now.minusDays(7));

// Compare against baseline:
double hourlyRate = profile.avgTransactionsPerHour30d;
double dailyRate = profile.avgTransactionsPerDay30d;

// Rule:
// If txnLast1Hour > max(3, hourlyRate * 3)  → velocity spike → score: 0.5
// If txnLast24Hours > dailyRate * 2.5       → elevated daily volume → score: 0.4
// Combined: both triggered → score: 0.7 (multiplicative escalation)
```

### Rule 3: Behavioral Baseline Deviation

```java
// BehavioralBaselineRule.java
// Checks if the transaction looks like this customer's normal behavior.

// Time-of-day check:
int hour = transaction.initiatedAt.getHour();
Map<Integer, Double> typicalHours = parseJson(profile.typicalTransactionHours);
double hourFrequency = typicalHours.getOrDefault(hour, 0.0);
// If hourFrequency < 0.01 (unusual hour for this customer) → score contribution: 0.2

// Merchant category check:
String mcc = transaction.merchantCategory;
Map<String, Double> typicalMcc = parseJson(profile.typicalMerchantCategories);
double mccFrequency = typicalMcc.getOrDefault(mcc, 0.0);
// If mccFrequency < 0.02 (unusual MCC for this customer) → score contribution: 0.3

// New counterparty check:
Set<String> knownCounterparties = parseJson(profile.typicalCounterparties);
if (transaction.counterpartyAccountId != null && !knownCounterparties.contains(counterpartyId)) {
    // First time sending to this account
    // Score contribution based on amount: > $5000 to new counterparty → 0.5
}
```

### Rule 4: Graph Pattern Detection

```java
// GraphPatternRule.java
// Detects money mule patterns: fan-out (one account sends to many new accounts quickly)
// and fan-in (one account receives from many different sources quickly).

// Fan-out: account sent to N distinct new counterparties in last 24h
int distinctNewRecipients = riskRepository.countDistinctNewCounterpartiesLast24h(accountId);
// If distinctNewRecipients >= 5 → structuring pattern → score: 0.8

// Fan-in: account received from M distinct sources in last 24h
int distinctNewSenders = riskRepository.countDistinctNewSendersLast24h(accountId);
// If distinctNewSenders >= 5 → aggregation pattern → score: 0.7

// Round-trip detection: transaction amount sent and returned within 2h
boolean roundTrip = riskRepository.detectRoundTrip(accountId, transaction.amount, 2);
// If round trip → layering pattern → score: 0.6
```

### Score Aggregation

```java
// RiskEngine.java
double totalScore = 0.0;
List<String> firedRules = new ArrayList<>();

double amountScore = amountAnomalyRule.evaluate(transaction, profile);
double velocityScore = velocityRule.evaluate(transaction, profile);
double behavioralScore = behavioralBaselineRule.evaluate(transaction, profile);
double graphScore = graphPatternRule.evaluate(transaction, profile);

// Weighted sum (weights reflect severity of each signal type)
totalScore = (amountScore * 0.25) + (velocityScore * 0.30) + (behavioralScore * 0.20) + (graphScore * 0.25);

// Escalation tier 1: any single rule ≥ 0.8 → floor 0.65 (HIGH)
totalScore = Math.max(totalScore, maxSingleRuleScore >= 0.8 ? 0.65 : totalScore);

// Escalation tier 2: ≥ 3 rules each scoring ≥ 0.6 simultaneously → floor 0.80 (CRITICAL)
// Rationale: multi-signal convergence across independent fraud typologies is qualitatively
// stronger than any single extreme signal. Required to reach CRITICAL on round-trip scenario.
// See docs/RISK_ENGINE_TEST_MATRIX.md for full derivation.
long convergingRules = Stream.of(amountScore, velocityScore, behavioralScore, graphScore)
    .filter(s -> s >= 0.6).count();
totalScore = Math.max(totalScore, convergingRules >= 3 ? 0.80 : totalScore);

if (totalScore >= 0.4) {
    // Create RiskAlert
    AlertSeverity severity = totalScore >= 0.8 ? CRITICAL : totalScore >= 0.6 ? HIGH : totalScore >= 0.4 ? MEDIUM : LOW;
}

// Always update CustomerRiskProfile baseline after evaluation (EWMA update)
profileService.updateBaseline(userId, transaction);
```

---

## Database Schema

### Key Design Decisions to Document
1. `account.balance` uses `NUMERIC(19, 4)` not FLOAT — financial data cannot use floating point
2. All monetary amounts use `NUMERIC(19, 4)` everywhere
3. UUIDs for all primary keys (better for distributed systems, avoids ID enumeration attacks)
4. `audit_log` is append-only — no foreign key constraints that could prevent log entries on deletion
5. `customer_risk_profile` is upserted on each transaction — never deleted, only updated
6. `transaction` status machine: PENDING → COMPLETED/FAILED; COMPLETED → REVERSED (only by admin)

### Flyway Migrations
```
src/main/resources/db/migration/
  V1__create_users_and_auth.sql
  V2__create_accounts.sql
  V3__create_ledger_transaction.sql   ← NOT "transactions" — SQL reserved word
  V4__create_risk_tables.sql
  V5__create_audit_log.sql
  V6__create_notifications.sql
  V7__seed_demo_data.sql
```

---

## API Design

### Key Endpoints

```
Auth:
  POST /api/auth/register
  POST /api/auth/login
  POST /api/auth/refresh
  POST /api/auth/logout

Accounts:
  GET  /api/accounts                    — list my accounts
  POST /api/accounts                    — create account
  GET  /api/accounts/{id}               — account detail + balance
  GET  /api/accounts/{id}/transactions  — paginated transaction history

Transactions:
  POST /api/transactions/deposit        — deposit to my account
  POST /api/transactions/withdrawal     — withdraw from my account
  POST /api/transactions/transfer       — transfer to another account
  GET  /api/transactions/{id}           — transaction detail

Risk (Admin only):
  GET  /api/admin/alerts                — paginated alert queue, filterable
  GET  /api/admin/alerts/{id}           — alert detail with rule breakdown
  PATCH /api/admin/alerts/{id}/review   — dismiss, escalate, or resolve alert
  GET  /api/admin/customers/{id}/risk-profile  — customer risk profile detail

Audit (Admin only):
  GET  /api/admin/audit-log             — paginated with filters

User:
  GET  /api/user/profile
  GET  /api/user/notifications
  PATCH /api/user/notifications/{id}/read
```

---

## Frontend Pages (React + TypeScript)

```
/login
/register
/dashboard                    — account overview, recent transactions, unread alerts
/accounts                     — account list
/accounts/:id                 — account detail + transaction history
/transfer                     — initiate transfer form
/transactions/:id             — transaction detail with status
/admin/alerts                 — alert review queue (admin only)
/admin/alerts/:id             — alert detail: transaction + risk score breakdown + rule details
/admin/customers/:id/risk     — customer risk profile visualization
/admin/audit-log              — full audit log
```

### Key UI Components
- **Risk Score Gauge** — visual 0.0–1.0 score indicator with color coding (green/yellow/orange/red)
- **Rule Breakdown Card** — shows which rules fired, their individual scores, thresholds crossed
- **Transaction Timeline** — chronological transaction history with status indicators
- **Alert Queue Table** — sortable/filterable, priority-colored rows, bulk review actions
- **Real-time alert badge** — unread alert count, updates via Server-Sent Events when new alerts arrive

---

## Real-Time Updates

Use **Server-Sent Events (SSE)** for admin alert notifications (simpler than WebSocket for one-directional server→client push):

```
GET /api/admin/alerts/stream   — SSE endpoint, pushes new alert events as they're created
```

Admin dashboard subscribes to this stream. When a new alert is created (by the Kafka consumer), the SSE endpoint pushes a notification. The React frontend updates the alert count badge and optionally shows a toast.

Alternatively use Spring WebFlux + WebSocket — choose based on what demonstrates best.

---

## Security

- Spring Security with stateless JWT (no sessions)
- JWT contains: userId, email, roles, exp
- Access token TTL: 15 minutes
- Refresh token TTL: 7 days, stored in HttpOnly cookie
- Role-based method security: `@PreAuthorize("hasRole('ADMIN')")` on risk/audit endpoints
- Password hashing: BCrypt with strength 12
- Rate limiting on auth endpoints (Bucket4j or custom filter): 10 login attempts per 5 min per IP
- Input validation: Bean Validation (`@Valid`, `@NotNull`, `@DecimalMin`, etc.) on all request DTOs
- No financial data logged in plaintext — mask account numbers in logs (show last 4 digits only)
- Parameterized queries throughout (JPA prevents injection by default — document this)
- CORS configured to explicit allowed origins
- HTTPS enforced via `server.ssl` configuration in production profile
- Secrets via environment variables — document all required vars in .env.example

---

## Observability (Structured Logging + Optional Metrics)

### Structured Logging (Required)
Every log entry includes:
- `timestamp`, `level`, `service` (always "ledgerbridge"), `traceId`
- For requests: `method`, `path`, `statusCode`, `durationMs`, `userId` (if authenticated)
- For Kafka events: `topic`, `partition`, `offset`, `eventType`, `transactionId`
- For risk engine: `transactionId`, `userId`, `rulesEvaluated`, `totalScore`, `alertCreated`

### Health Checks (Required)
Spring Boot Actuator endpoints:
- `/actuator/health` — overall health (DB + Kafka connectivity)
- `/actuator/metrics` — application metrics

### Optional Prometheus + Grafana
If adding observability, Docker Compose includes:
- Prometheus scraping `/actuator/prometheus`
- Grafana with pre-built dashboard for: transaction volume, risk alert rate, rule fire rates, API latency

---

## DevOps

### Docker Compose

```yaml
services:
  api:
    build: .
    ports: ["8080:8080"]
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/ledgerbridge
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JWT_SECRET=${JWT_SECRET}
    depends_on: [db, kafka]

  frontend:
    build: ./frontend
    ports: ["3000:80"]

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=ledgerbridge
      - POSTGRES_USER=ledgerbridge
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes: [pgdata:/var/lib/postgresql/data]

  kafka:
    image: bitnami/kafka:latest
    environment:
      - KAFKA_CFG_NODE_ID=0
      - KAFKA_CFG_PROCESS_ROLES=controller,broker
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
    ports: ["9092:9092"]

  # Optional:
  prometheus:
    image: prom/prometheus
  grafana:
    image: grafana/grafana
```

### GitHub Actions CI

```yaml
# Triggers: push to main, PR to main
# Steps:
#   1. Set up Java 21
#   2. Build with Maven (./mvnw verify)
#   3. Run unit tests
#   4. Run integration tests (Testcontainers spins up PostgreSQL + Kafka)
#   5. Build Docker image
#   6. Run frontend tests (npm test)
#   7. Build frontend Docker image
```

### Testcontainers
Integration tests use Testcontainers to spin up real PostgreSQL and Kafka instances:
- `@Testcontainers` + `@Container` on PostgreSQL and Kafka containers
- All integration tests hit real infrastructure — no mocks for DB or Kafka
- Tests cover: full transaction → Kafka → risk engine → alert creation flow

---

## Phases

### Phase 0 — Setup
- [ ] Create GitHub repo `ledgerbridge`
- [ ] Initialize Spring Boot 3.x project with all dependencies
- [ ] Set up Docker Compose with PostgreSQL + Kafka
- [ ] Configure Flyway for migrations
- [ ] Set up SpringDoc OpenAPI (Swagger UI)
- [ ] Configure Logback for structured JSON logging
- [ ] Create docs/: AI_CONTEXT.md, HANDOFF.md, ENGINEERING_LOG.md, CURRENT_TASK.md
- [ ] Run `/plan-eng-review` before proceeding to Phase 1

### Phase 1 — Domain + Database
- [ ] Define all JPA entities with proper types (NUMERIC for money, UUID for IDs)
- [ ] Write Flyway migrations V1–V6
- [ ] Write seed data migration V7 (5 users, 10 accounts, 50+ transactions across various patterns)
- [ ] Seed data should include at least: a high-velocity customer, a customer with unusual amounts, a fan-out pattern, normal baseline customers
- [ ] Confirm migrations run clean

### Phase 2 — Auth + Account Module
- [ ] Implement User entity, Spring Security config, JWT service
- [ ] Implement AuthController: register, login, refresh, logout
- [ ] Implement AccountService + AccountController
- [ ] Write unit tests for auth and account services

### Phase 3 — Transaction Module + Kafka
- [ ] Implement TransactionService (deposit, withdrawal, transfer with balance validation)
- [ ] Implement TransactionEventProducer (publish to Kafka after successful transaction)
- [ ] Implement TransactionController
- [ ] Write unit tests for TransactionService (mock Kafka producer)
- [ ] Write integration test: full deposit flow → Kafka event published

### Phase 4 — Risk Engine (Core Differentiator)
- [ ] Implement CustomerRiskProfile update logic (EWMA for rolling mean/stddev)
- [ ] Implement AmountAnomalyRule (Z-score as described above)
- [ ] Implement VelocityRule (sliding window counts)
- [ ] Implement BehavioralBaselineRule (time-of-day, MCC, new counterparty)
- [ ] Implement GraphPatternRule (fan-in, fan-out, round-trip detection)
- [ ] Implement RiskEngine (score aggregation, alert creation)
- [ ] Implement TransactionRiskConsumer (Kafka consumer wiring RiskEngine)
- [ ] Write unit tests for each rule with edge cases
- [ ] Write integration test: transaction submitted → Kafka → risk consumer → alert created
- [ ] Run `/review` before calling Phase 4 done — the risk engine must be reviewed

### Phase 5 — Admin + Audit
- [ ] Implement AuditAspect (AOP-based auto-logging for service methods with @Audited)
- [ ] Implement AuditService + AuditController
- [ ] Implement AlertService + admin alert review flow
- [ ] Implement SSE endpoint for real-time alert notifications
- [ ] Write tests for audit logging

### Phase 6 — Frontend
- [ ] Set up React + TypeScript + Tailwind + React Query + Zustand
- [ ] Build auth pages (login/register)
- [ ] Build Dashboard (account overview, recent transactions)
- [ ] Build Account list + detail with transaction history
- [ ] Build Transfer form
- [ ] Build Admin: Alert queue with risk score visualization
- [ ] Build Admin: Alert detail with rule breakdown breakdown display
- [ ] Build Admin: Audit log
- [ ] Connect SSE for real-time alert badge
- [ ] Run `/qa` to verify all flows end-to-end

### Phase 7 — Observability + DevOps
- [ ] Verify all structured logging is in place
- [ ] Add Spring Actuator health + metrics endpoints
- [ ] Finalize Docker Compose (all services)
- [ ] Write GitHub Actions CI workflow
- [ ] (Optional) Add Prometheus + Grafana
- [ ] Write .env.example with all variables documented

### Phase 8 — Testing + Portfolio Integration
- [ ] Testcontainers integration tests for full transaction→risk flow
- [ ] Verify test coverage on risk engine rules
- [ ] Write polished README: Problem, Architecture Diagram (Mermaid), Event Flow, DB Design, Risk Engine Explanation, Setup, Security, Testing, Resume Bullets
- [ ] Take screenshots of key pages (use seed data)
- [ ] Add project to Kenny's ePortfolio (update `src/data/projects.js`)
- [ ] Run `/review` and `/qa` on final state

---

## Resume Bullets

- Built a banking transaction and risk monitoring system in Java 21 and Spring Boot with Kafka event streaming, where every transaction is asynchronously analyzed by a four-rule statistical risk engine (Z-score anomaly detection, velocity analysis, behavioral baselining, and graph pattern detection)
- Designed a PostgreSQL schema using Flyway migrations with financial-precision numeric types, UUID primary keys, and an append-only audit log used by AOP-based automatic action logging across all sensitive service operations
- Implemented event-driven transaction processing with Kafka: TransactionService publishes events on commit, a dedicated RiskConsumer runs the scoring pipeline independently, and new alerts arrive on the Admin dashboard via Server-Sent Events without polling
- Secured all APIs with Spring Security + stateless JWT, role-based method authorization, BCrypt password hashing, and rate limiting on auth endpoints, with no financial data exposed in plaintext logs

---

## Portfolio Case Study Content

**context**: "Wells Fargo, Citi, and Capital One process billions of transactions daily and need engineers who understand event-driven architecture, relational schema design, and fraud detection at scale. LedgerBridge demonstrates the full stack for that environment."

**challenge**: "Building a risk detection system that goes beyond naive threshold rules — one that models each customer's individual behavioral baseline and uses statistical anomaly detection to flag transactions that are unusual for *that specific customer*, not just globally suspicious."

**approach**: "Modular monolith in Java 21/Spring Boot with clean module boundaries (Account, Transaction, Risk, Audit, Notification). Kafka decouples transaction processing from risk analysis. The risk engine runs four rule categories — Z-score amount analysis, sliding-window velocity, behavioral baselining (time-of-day, merchant category, counterparty novelty), and graph pattern detection (fan-in/fan-out) — and aggregates a weighted risk score per transaction."

**outcome**: "A fully operational event-driven banking system: account management, real-time transaction processing, statistical fraud detection, admin alert review queue with risk score breakdowns, append-only audit logging, Docker multi-service deployment, and Testcontainers integration tests covering the full Kafka pipeline."

---

## AI Agent Working Instructions

This project is built in a separate GitHub repo, not inside the ePortfolio repo.

### gstack Skills to Use
- `/plan-eng-review` — REQUIRED before Phase 1 (database schema), Phase 4 (risk engine), and Phase 6 (frontend)
- `/review` — REQUIRED before marking Phase 4 (risk engine) complete — this is the critical section
- `/qa` — run after Phase 6 to verify all flows end-to-end
- `/ship` — for commits and pushes

### gbrain
If gbrain is configured, store risk engine design decisions as pages for cross-session continuity.

### Documentation Hygiene
Every session must maintain:
- `docs/ENGINEERING_LOG.md` — log every change with date, what changed, why
- `docs/HANDOFF.md` — update on architecture changes
- `docs/AI_CONTEXT.md` — update on stack or system design changes
- `docs/CURRENT_TASK.md` — reflect active work

### Commit Convention
Format: `Add Phase N: [what changed] — [why it matters]`
Always push immediately after commit.

### Code Quality Standards
- No business logic in controllers or Kafka consumers (consumers call services)
- All monetary values as `BigDecimal` — never `double` or `float`
- Service methods are `@Transactional` where they modify state
- Kafka consumer has a dead-letter topic for failed messages
- Risk engine rules implement a common `RiskRule` interface — new rules plug in without modifying RiskEngine
- Test coverage required on all four risk rules before Phase 4 is considered done
