# Risk Engine Test Matrix — LedgerBridge

> This document is the Phase 1 TODOS gate for the fraud-scenario validation
> strategy. It defines five labeled seed-data scenarios, the expected raw score
> from each rule, and the final composite score range each Phase 4 integration
> test must assert against.
>
> Why this matters: the weights (0.25/0.30/0.20/0.25) and the 0.4 alert
> threshold are only defensible if we can prove, with seed data, that they
> produce the intended severity for each canonical fraud pattern — and that a
> clean transaction does NOT produce a false positive.

---

## Scoring Formula (from AI_CONTEXT.md)

```
totalScore = (AmountAnomaly × 0.25) + (Velocity × 0.30) + (Behavioral × 0.20) + (GraphPattern × 0.25)
```

### Escalation Rules

| Rule | Condition | Effect |
|---|---|---|
| Single-rule | Any rule raw score ≥ 0.8 | `totalScore = max(totalScore, 0.65)` |
| Multi-rule | ≥ 3 rules each raw score ≥ 0.6 | `totalScore = max(totalScore, 0.80)` |

**Rationale for multi-rule escalation:** multiple independent fraud signals
converging simultaneously is a qualitatively stronger confidence signal than
any one signal alone. A 3-signal convergence at ≥ 0.6 each means the engine
has high confidence across unrelated fraud typologies — this warrants a
guaranteed CRITICAL floor. This is how production fraud systems work (e.g.,
Featurespace ARIC, Actimize).

### Severity Mapping

| Score range | Severity |
|---|---|
| ≥ 0.80 | CRITICAL |
| ≥ 0.60 | HIGH |
| ≥ 0.40 | MEDIUM |
| < 0.40 | No alert |

---

## AmountAnomalyRule — Score Tiers

| Z-score | Raw score |
|---|---|
| < 2.0 | 0.0 |
| 2.0 – 2.9 | 0.3 |
| 3.0 – 3.9 | 0.6 |
| ≥ 4.0 | 0.9 |

*New customer (< 10 prior transactions): use conservative defaults (low
confidence flag), always score at the low end of the applicable tier.*

## VelocityRule — Score Tiers

| Condition | Raw score |
|---|---|
| txnLast1Hour > max(3, hourlyBaseline × 3) | 0.5 |
| txnLast24h > dailyBaseline × 2.5 | 0.4 |
| Both 1h AND 24h conditions | 0.7 (combined) |

## BehavioralBaselineRule — Score Contributions (summed, cap 0.9)

| Signal | Score contribution |
|---|---|
| Unusual hour (hourFrequency < 0.01) | 0.2 |
| Unusual MCC (mccFrequency < 0.02) | 0.3 |
| New counterparty + amount > $5,000 | 0.5 |
| New counterparty + amount ≤ $5,000 | 0.2 |

## GraphPatternRule — Score Tiers

| Pattern | Raw score |
|---|---|
| Fan-out: ≥ 5 distinct new recipients in 24h | 0.8 |
| Fan-in: ≥ 5 distinct new senders in 24h | 0.7 |
| Round-trip: same amount sent and returned within 2h | 0.6 |

---

## Scenario Matrix

### S1 — Normal Deposit (Negative Control)

**Purpose:** Prove the engine does NOT false-positive on a clean transaction.
This is a PRECISION test, not a sensitivity test (per AI_CONTEXT.md D3).

**Seed data — customer `alice-normal-uuid`:**
- 30 prior transactions over 30 days
- Amounts: $75–$150 (mean=$110, stddev=$25)
- Frequency: ~1/day, weekdays 9am–7pm only
- Counterparties: 2 known accounts (employer payroll, parent)
- MCCs: 5411 (grocery, 45%), 5541 (gas, 35%), 5812 (restaurant, 20%)

**Test transaction:**
- $95 deposit at 11am Tuesday from known payroll account, MCC 5411

**Rule evaluation:**

| Rule | Calculation | Raw score |
|---|---|---|
| AmountAnomaly | z = (95−110)/25 = −0.6 → below 2.0 threshold | **0.0** |
| Velocity | 1 txn this hour vs baseline ~0.04/hr; no spike | **0.0** |
| Behavioral | Hour 11: freq 0.10 (normal); MCC 5411: freq 0.45 (normal); known counterparty | **0.0** |
| GraphPattern | No new recipients | **0.0** |

**Score math:**

```
raw = 0.0×0.25 + 0.0×0.30 + 0.0×0.20 + 0.0×0.25 = 0.000
escalation: none
final = 0.000
```

**Phase 4 assertion:**
```java
assertThat(score).isLessThan(0.10);
assertThat(alertCreated).isFalse();
```

---

### S2 — Velocity Spike

**Purpose:** Detect a high-frequency burst of transactions at an unusual hour
with slightly elevated amounts (realistic money-mule testing pattern).

**Note on weight math:** VelocityRule alone (max raw 0.7 × weight 0.30 = 0.21)
cannot reach the 0.4 alert threshold by itself. This is intentional — pure
velocity without any corroborating signal is not a strong enough standalone
indicator. The scenario is designed as a combined signal: velocity + amount
elevation + behavioral deviation, which is the realistic pattern.

**Seed data — customer `bob-velocity-uuid`:**
- 15 prior transactions over 30 days
- Amounts: $80–$150 (mean=$100, stddev=$30)
- Frequency: ~0.5/day, weekdays 8am–8pm only
- Counterparties: 3 known accounts

**Test window:** 8 transactions between 3:00–3:45 AM
- Amounts: $180–$220 each (elevated above normal range)
- To 2 known counterparties (no new counterparties — keeps GraphPattern silent)
- MCC: 6012 (financial institutions — unusual for Bob, who typically uses grocery/gas)

**Rule evaluation:**

| Rule | Calculation | Raw score |
|---|---|---|
| AmountAnomaly | z = (200−100)/30 = 3.33 → tier 3.0–3.9 | **0.6** |
| Velocity | 8 in 45min >> max(3, 0.5/24×3)=3 (hourly spike); 8 in 24h >> 0.5×2.5=1.25 (daily elevated) → combined | **0.7** |
| Behavioral | Hour 3: freq ~0.0 (all prior txns were 8am–8pm) → unusual hour 0.2; MCC 6012: freq 0.0 → unusual MCC 0.3 | **0.5** |
| GraphPattern | Known counterparties, no new recipients | **0.0** |

**Score math:**

```
raw = 0.6×0.25 + 0.7×0.30 + 0.5×0.20 + 0.0×0.25
    = 0.150 + 0.210 + 0.100 + 0.000 = 0.460

escalation check:
  single-rule (≥0.8): max rule = 0.7 < 0.8 → no escalation
  multi-rule (≥3 rules at ≥0.6): AmountAnomaly=0.6 ✓, Velocity=0.7 ✓, Behavioral=0.5 ✗ → only 2, no escalation

final = 0.460
```

**Phase 4 assertion:**
```java
assertThat(score).isGreaterThanOrEqualTo(0.40);
assertThat(score).isLessThan(0.60);
assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
assertThat(alertCreated).isTrue();
```

---

### S3 — Large Amount to New Counterparty

**Purpose:** Detect an unusually large wire to a never-seen counterparty —
a classic fraud precursor (account takeover, authorized push payment fraud).

**Seed data — customer `carol-highamount-uuid`:**
- 20 prior transactions over 30 days
- Amounts: $100–$500 (mean=$200, stddev=$80)
- Frequency: ~0.67/day, business hours (9am–6pm)
- Counterparties: 2 known accounts

**Test transaction:**
- $8,000 wire at 2pm Friday to a brand-new counterparty (never in profile)
- MCC: null (wire transfer, P2P — no merchant category)

**Rule evaluation:**

| Rule | Calculation | Raw score |
|---|---|---|
| AmountAnomaly | z = (8000−200)/80 = 97.5 → tier ≥4.0 | **0.9** |
| Velocity | 1 transaction today vs normal ~0.67/day; no spike | **0.0** |
| Behavioral | Business hours (normal); MCC null (wire transfer is normal); new counterparty + amount $8,000 > $5,000 | **0.5** |
| GraphPattern | 1 new recipient in 24h (< 5 fan-out threshold) | **0.0** |

**Score math:**

```
raw = 0.9×0.25 + 0.0×0.30 + 0.5×0.20 + 0.0×0.25
    = 0.225 + 0.000 + 0.100 + 0.000 = 0.325

escalation check:
  single-rule (≥0.8): AmountAnomaly = 0.9 ≥ 0.8 → floor max(0.325, 0.65) = 0.65
  multi-rule: only 1 rule at ≥0.6 → no multi-escalation

final = 0.650
```

**Phase 4 assertion:**
```java
assertThat(score).isGreaterThanOrEqualTo(0.60);
assertThat(score).isLessThan(0.80);
assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.HIGH);
assertThat(alertCreated).isTrue();
```

---

### S4 — Fan-Out Pattern

**Purpose:** Detect a money mule distributing funds to many new counterparties
— the classic structuring/smurfing pattern.

**Seed data — customer `dave-fanout-uuid`:**
- 10 prior transactions over 30 days
- Amounts: $500–$2,000 (mean=$1,000, stddev=$300)
- Frequency: ~0.33/day, business hours
- Counterparties: 1 known account (very narrow network — makes fan-out more salient)

**Test window:** 6 transactions over 24 hours, each to a different new
counterparty never seen in Dave's profile
- Amounts: $800–$1,200 each (within normal range — amounts alone are not suspicious)
- Spread over business hours (behavioral signals are mild)

**Rule evaluation:**

| Rule | Calculation | Raw score |
|---|---|---|
| AmountAnomaly | z = (1000−1000)/300 = 0.0 → normal | **0.0** |
| Velocity | 6 in 24h >> 0.33×2.5=0.83 (daily elevated fires); spread over 24h so hourly <3 (no hourly spike) → daily elevated only | **0.4** |
| Behavioral | 6 new counterparties, amounts ≤ $5K each → 6 × "new counterparty ≤ $5K" = 6×0.2 (capped at) → raw **0.3** (cap applied) |
| GraphPattern | 6 distinct new recipients in 24h ≥ 5 → fan-out | **0.8** |

**Score math:**

```
raw = 0.0×0.25 + 0.4×0.30 + 0.3×0.20 + 0.8×0.25
    = 0.000 + 0.120 + 0.060 + 0.200 = 0.380

escalation check:
  single-rule (≥0.8): GraphPattern = 0.8 ≥ 0.8 → floor max(0.380, 0.65) = 0.65
  multi-rule: only 1 rule at ≥0.6 (GraphPattern=0.8) → need 3, no multi-escalation

final = 0.650
```

**Phase 4 assertion:**
```java
assertThat(score).isGreaterThanOrEqualTo(0.60);
assertThat(score).isLessThan(0.80);
assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.HIGH);
assertThat(alertCreated).isTrue();
assertThat(alert.getAlertType()).isEqualTo(AlertType.GRAPH_PATTERN);
```

---

### S5 — Round-Trip (Layering Pattern)

**Purpose:** Detect a money laundering layering cycle — funds sent to a new
account and returned within 2 hours, often used to create paper trails or
test movement channels. All 4 rule categories fire simultaneously, triggering
the multi-rule escalation to CRITICAL.

**Seed data — customer `eve-roundtrip-uuid`:**
- 5 prior transactions over 30 days
- Amounts: $200–$500 (mean=$300, stddev=$80)
- Frequency: ~0.17/day, business hours only
- Counterparties: 1 known account

**Test window (3 events in 2 hours, starting 11:00 PM):**
1. T+0min: 2 small probe transactions (~$250) in rapid succession (testing channel)
2. T+10min: $25,000 wire to new account X
3. T+90min: $25,000 received back from account X (round-trip completes)

*The probe transactions push txnLast1Hour past the velocity threshold. The
$25,000 amount is 97σ above Eve's normal range. Account X has never appeared
in Eve's profile. The return at T+90min triggers round-trip detection.*

**Rule evaluation:**

| Rule | Calculation | Raw score |
|---|---|---|
| AmountAnomaly | z = (25000−300)/80 ≈ 308 → tier ≥4.0 | **0.9** |
| Velocity | 4 txns in 1h (probes + wire) > max(3, 0.17/24×3)=3 → hourly spike; 4 in 24h >> 0.17×2.5=0.43 → daily elevated → combined | **0.7** |
| Behavioral | Hour 23: freq ~0.0 (Eve only transacts business hours) → unusual hour 0.2; new counterparty + $25,000 > $5,000 → 0.5 | **0.5** (capped: 0.2+0.5 capped at 0.5 for single tx) |
| GraphPattern | Round-trip: $25K sent to X at T+10, $25K received from X at T+90 (< 2h window) | **0.6** |

**Score math:**

```
raw = 0.9×0.25 + 0.7×0.30 + 0.5×0.20 + 0.6×0.25
    = 0.225 + 0.210 + 0.100 + 0.150 = 0.685

escalation check:
  single-rule (≥0.8): AmountAnomaly = 0.9 ≥ 0.8 → floor max(0.685, 0.65) → 0.685
  multi-rule (≥3 rules at ≥0.6):
    AmountAnomaly = 0.9 ≥ 0.6 ✓
    Velocity = 0.7 ≥ 0.6 ✓
    GraphPattern = 0.6 ≥ 0.6 ✓
    → 3 rules qualify → floor max(0.685, 0.80) = 0.80

final = 0.800
```

**Phase 4 assertion:**
```java
assertThat(score).isGreaterThanOrEqualTo(0.75);  // 0.75 floor gives impl variance room
assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
assertThat(alertCreated).isTrue();
assertThat(alert.getAlertType()).isEqualTo(AlertType.GRAPH_PATTERN);
```

---

## Summary Table

| Scenario | Amount | Velocity | Behavioral | Graph | Raw Sum | Escalation | **Final** | **Severity** | Alert |
|---|---|---|---|---|---|---|---|---|---|
| S1 Normal | 0.0 | 0.0 | 0.0 | 0.0 | 0.000 | none | **0.00** | — | ❌ |
| S2 Velocity spike | 0.6 | 0.7 | 0.5 | 0.0 | 0.460 | none | **0.46** | MEDIUM | ✅ |
| S3 Large+new counterparty | 0.9 | 0.0 | 0.5 | 0.0 | 0.325 | single (A≥0.8→0.65) | **0.65** | HIGH | ✅ |
| S4 Fan-out | 0.0 | 0.4 | 0.3 | 0.8 | 0.380 | single (G≥0.8→0.65) | **0.65** | HIGH | ✅ |
| S5 Round-trip | 0.9 | 0.7 | 0.5 | 0.6 | 0.685 | multi (3×≥0.6→0.80) | **0.80** | CRITICAL | ✅ |

---

## Seed Customer Fixed UUIDs

These UUIDs are used in `V7__seed_demo_data.sql` and referenced in Phase 4
test constants:

```java
// Full constants in src/test/.../common/TestScenarioIds.java
public static final UUID ALICE_USER_ID = UUID.fromString("a0000001-0000-0000-0000-000000000001");
public static final UUID BOB_USER_ID   = UUID.fromString("b0000002-0000-0000-0000-000000000002");
public static final UUID CAROL_USER_ID = UUID.fromString("c0000003-0000-0000-0000-000000000003");
public static final UUID DAVE_USER_ID  = UUID.fromString("d0000004-0000-0000-0000-000000000004");
public static final UUID EVE_USER_ID   = UUID.fromString("e0000005-0000-0000-0000-000000000005");

public static final UUID ALICE_ACCOUNT = UUID.fromString("a0000001-0001-0000-0000-000000000001");
// ... (see TestScenarioIds.java for full set including account and profile IDs)
```

---

## Design Decisions Locked by This Document

1. **Multi-rule escalation tier**: 3 or more rules each scoring ≥ 0.6 raw →
   total score floor of 0.80 (CRITICAL). This rule ONLY fires for S5 among
   the 5 scenarios.

2. **Velocity spike requires corroboration**: Pure velocity cannot reach 0.4
   alone under these weights. This is by design — velocity without any
   corroborating signal (amount, behavioral, graph) produces too many false
   positives on legitimate bulk-payment scenarios.

3. **Large-amount escalation covers the amount-only case**: A single extreme
   z-score (≥ 4.0 → raw 0.9 ≥ 0.8) auto-escalates to 0.65 (HIGH) even without
   corroborating signals. This ensures no egregiously large transaction
   slips through as MEDIUM.

4. **Round-trip CRITICAL requires the full multi-signal convergence**: Velocity
   probe transactions are part of the S5 seed by design — they push the
   velocity window over threshold. A pure round-trip of a normal-sized amount
   at normal velocity would score lower and not reach CRITICAL.

---

*This document drives: Phase 4 `V7__seed_demo_data.sql` seed data structure,
Phase 4 per-scenario Testcontainers integration tests, Phase 7.5
`V8__demo_seed.sql` timestamp matrix (which mirrors this structure), and the
Phase 8 README "Risk Engine Explanation" section.*
