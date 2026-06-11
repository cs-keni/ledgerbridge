package com.ledgerbridge.risk;

import com.ledgerbridge.common.AbstractIntegrationTest;
import com.ledgerbridge.common.TestScenarioIds;
import com.ledgerbridge.risk.engine.RiskEngine;
import com.ledgerbridge.risk.engine.RiskScoringResult;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.service.CustomerRiskProfileService;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-scenario Testcontainers integration tests (D3 from AI_CONTEXT.md).
 *
 * Runs against V7-seeded profiles in a real PostgreSQL container.
 * Each test directly invokes RiskEngine.evaluate() so assertions are
 * synchronous — no Kafka timing needed for scoring correctness.
 *
 * counterparty_account_id has a FK to account(id), so test accounts are
 * created via insertAccount() before inserting priming transactions.
 * risk_alert.transaction_id has a FK to ledger_transaction(id), so the
 * trigger transaction is also inserted into the DB before evaluate().
 */
// Inserts extra accounts/transactions; dirties context so SchemaIntegrationTest gets a fresh container.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RiskScenarioIntegrationTest extends AbstractIntegrationTest {

    @Autowired RiskEngine riskEngine;
    @Autowired CustomerRiskProfileService profileService;
    @Autowired JdbcTemplate jdbc;

    // ── S1: Normal deposit — precision test (false-positive resistance) ───────

    @Test
    void s1_normalDeposit_noAlertAndScoreBelow10() {
        CustomerRiskProfile alice = profileService.getOrCreate(TestScenarioIds.ALICE_USER_ID);

        // Trigger transaction: $95 deposit at 11am UTC from known payroll account, MCC 5411
        // Use UTC timestamps throughout to keep JdbcTemplate inserts consistent with
        // Hibernate's jdbc.time_zone=UTC setting on JPQL query parameters.
        UUID txId = insertTransaction(TestScenarioIds.ALICE_ACCOUNT,
                TestScenarioIds.BOB_ACCOUNT, // known in Alice's profile
                new BigDecimal("95.00"),
                LocalDateTime.now(ZoneOffset.UTC).withHour(11).withMinute(0),
                "5411", TransactionType.DEPOSIT);

        TransactionEvent event = new TransactionEvent(txId,
                TestScenarioIds.ALICE_ACCOUNT,
                TestScenarioIds.ALICE_USER_ID,
                TestScenarioIds.BOB_ACCOUNT,
                TransactionType.DEPOSIT,
                new BigDecimal("95.00"),
                "USD", "5411", null,
                LocalDateTime.now(ZoneOffset.UTC).withHour(11).withMinute(0));

        RiskScoringResult result = riskEngine.evaluate(event, alice);

        assertThat(result.finalScore()).isLessThan(0.10);
        assertThat(result.alertTriggered()).isFalse();
        assertThat(result.alert()).isNull();
    }

    // ── S2: Velocity spike — MEDIUM ───────────────────────────────────────────

    @Test
    void s2_velocitySpike_mediumAlert() {
        CustomerRiskProfile bob = profileService.getOrCreate(TestScenarioIds.BOB_USER_ID);

        // UTC timestamps avoid JdbcTemplate/Hibernate jdbc.time_zone=UTC mismatch.
        LocalDateTime spikeTime = LocalDateTime.now(ZoneOffset.UTC).withHour(3).withMinute(45);

        // Prime: 7 transactions in the last hour at 3am to known counterparties
        for (int i = 1; i <= 7; i++) {
            insertTransaction(TestScenarioIds.BOB_ACCOUNT,
                    TestScenarioIds.ALICE_ACCOUNT,
                    new BigDecimal("195.00"),
                    spikeTime.minusMinutes(40 - i * 4L),
                    "6012", TransactionType.TRANSFER_DEBIT);
        }

        // 8th transaction — triggers hour + day spike (>3 in 1h, >1.25 in 24h combined = 0.7)
        UUID txId = insertTransaction(TestScenarioIds.BOB_ACCOUNT,
                TestScenarioIds.ALICE_ACCOUNT,
                new BigDecimal("200.00"),
                spikeTime,
                "6012", TransactionType.TRANSFER_DEBIT);

        // Event at 3am UTC — unusual hour for Bob (typical hours 8am-7pm).
        // Velocity: 8 in last hour (>threshold 3), 8 in last day (>1.25) → combined 0.7
        // Behavioral: hour 3 unusual (0.2) + MCC 6012 unusual (0.3) = 0.5
        // AmountAnomaly: z=(200-100)/30=3.33 → tier 3-4 → 0.6
        // Raw = 0.6*0.25 + 0.7*0.30 + 0.5*0.20 = 0.460 → MEDIUM
        TransactionEvent event = new TransactionEvent(txId,
                TestScenarioIds.BOB_ACCOUNT,
                TestScenarioIds.BOB_USER_ID,
                TestScenarioIds.ALICE_ACCOUNT,
                TransactionType.TRANSFER_DEBIT,
                new BigDecimal("200.00"),
                "USD", "6012", null, spikeTime);

        RiskScoringResult result = riskEngine.evaluate(event, bob);

        assertThat(result.finalScore()).isGreaterThanOrEqualTo(0.40);
        assertThat(result.finalScore()).isLessThan(0.60);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    // ── S3: Large amount to new counterparty — HIGH (single-rule escalation) ──

    @Test
    void s3_largeAmountNewCounterparty_highAlert() {
        CustomerRiskProfile carol = profileService.getOrCreate(TestScenarioIds.CAROL_USER_ID);

        // New counterparty: an account that doesn't exist in Carol's typicalCounterparties
        // Carol's known counterparties are Alice and Dave accounts — use a brand-new one
        UUID newCounterpartyAccount = insertAccount(TestScenarioIds.ALICE_USER_ID);

        LocalDateTime txTime = LocalDateTime.now(ZoneOffset.UTC).withHour(14).withMinute(0);

        // $8,000 wire — z = (8000-200)/80 ≈ 97.5 → AmountAnomaly raw = 0.9
        UUID txId = insertTransaction(TestScenarioIds.CAROL_ACCOUNT, newCounterpartyAccount,
                new BigDecimal("8000.00"), txTime, null, TransactionType.TRANSFER_DEBIT);

        TransactionEvent event = new TransactionEvent(txId,
                TestScenarioIds.CAROL_ACCOUNT,
                TestScenarioIds.CAROL_USER_ID,
                newCounterpartyAccount,
                TransactionType.TRANSFER_DEBIT,
                new BigDecimal("8000.00"),
                "USD", null, null, txTime);

        RiskScoringResult result = riskEngine.evaluate(event, carol);

        assertThat(result.finalScore()).isGreaterThanOrEqualTo(0.60);
        assertThat(result.finalScore()).isLessThan(0.80);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    // ── S4: Fan-out pattern — HIGH (single-rule escalation via GraphPattern) ──

    @Test
    void s4_fanOut_highAlertWithGraphPatternType() {
        CustomerRiskProfile dave = profileService.getOrCreate(TestScenarioIds.DAVE_USER_ID);
        // Dave's known counterparty: Alice only
        LocalDateTime spreadTime = LocalDateTime.now(ZoneOffset.UTC).withHour(10).withMinute(0);

        // Create 6 brand-new accounts as counterparties (all unknown to Dave)
        UUID[] newRecipients = new UUID[6];
        for (int i = 0; i < 6; i++) {
            newRecipients[i] = insertAccount(TestScenarioIds.BOB_USER_ID);
        }

        // Prime: 5 transactions to first 5 new recipients in last 24h
        for (int i = 0; i < 5; i++) {
            insertTransaction(TestScenarioIds.DAVE_ACCOUNT, newRecipients[i],
                    new BigDecimal("900.00"),
                    spreadTime.minusHours((i + 1) * 3L),
                    null, TransactionType.TRANSFER_DEBIT);
        }

        // Trigger: 6th transaction to 6th new recipient — fan-out reaches ≥5
        UUID txId = insertTransaction(TestScenarioIds.DAVE_ACCOUNT, newRecipients[5],
                new BigDecimal("1000.00"), spreadTime, null, TransactionType.TRANSFER_DEBIT);

        TransactionEvent event = new TransactionEvent(txId,
                TestScenarioIds.DAVE_ACCOUNT,
                TestScenarioIds.DAVE_USER_ID,
                newRecipients[5],
                TransactionType.TRANSFER_DEBIT,
                new BigDecimal("1000.00"),
                "USD", null, null, spreadTime);

        RiskScoringResult result = riskEngine.evaluate(event, dave);

        assertThat(result.finalScore()).isGreaterThanOrEqualTo(0.60);
        assertThat(result.finalScore()).isLessThan(0.80);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(result.alert().getAlertType()).isEqualTo(AlertType.GRAPH_PATTERN);
    }

    // ── S5: Round-trip layering — CRITICAL (multi-rule escalation) ───────────

    @Test
    void s5_roundTrip_criticalAlert() {
        CustomerRiskProfile eve = profileService.getOrCreate(TestScenarioIds.EVE_USER_ID);
        // Eve's known counterparty: Bob only
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(0);

        // New account X for the round-trip
        UUID newAccountX = insertAccount(TestScenarioIds.CAROL_USER_ID);

        // Probe transactions: 3 in last hour — pushes hourly velocity over threshold (>3)
        for (int i = 1; i <= 3; i++) {
            insertTransaction(TestScenarioIds.EVE_ACCOUNT,
                    TestScenarioIds.BOB_ACCOUNT,
                    new BigDecimal("250.00"),
                    now.minusMinutes(50 - i * 10L),
                    null, TransactionType.TRANSFER_DEBIT);
        }

        // Return leg: $25K received FROM account X → account Y (Eve's account as receiver)
        // existsRoundTrip query: accountId=newAccountX, counterpartyAccountId=EVE_ACCOUNT, amount=25000, since now-2h
        insertTransaction(newAccountX, TestScenarioIds.EVE_ACCOUNT,
                new BigDecimal("25000.00"),
                now.minusMinutes(5),
                null, TransactionType.TRANSFER_DEBIT);

        // Trigger: Eve sends $25K to account X — round-trip closes
        UUID txId = insertTransaction(TestScenarioIds.EVE_ACCOUNT, newAccountX,
                new BigDecimal("25000.00"), now.minusMinutes(50), null, TransactionType.TRANSFER_DEBIT);

        TransactionEvent event = new TransactionEvent(txId,
                TestScenarioIds.EVE_ACCOUNT,
                TestScenarioIds.EVE_USER_ID,
                newAccountX,
                TransactionType.TRANSFER_DEBIT,
                new BigDecimal("25000.00"),
                "USD", null, null, now);

        RiskScoringResult result = riskEngine.evaluate(event, eve);

        assertThat(result.finalScore()).isGreaterThanOrEqualTo(0.75);
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a minimal account owned by userId and returns its ID.
     * Required because ledger_transaction.counterparty_account_id has a FK to account.id.
     */
    private UUID insertAccount(UUID userId) {
        UUID accountId = UUID.randomUUID();
        String accountNumber = "T" + accountId.toString().replace("-", "").substring(0, 14).toUpperCase();
        jdbc.update("""
                INSERT INTO account (id, account_number, user_id, type, status, balance, opened_at)
                VALUES (?, ?, ?, 'CHECKING', 'ACTIVE', 1000.0000, NOW())
                """, accountId, accountNumber, userId);
        return accountId;
    }

    /**
     * Inserts a transaction and returns its ID.
     * The ID is used in TransactionEvent so risk_alert.transaction_id FK is satisfied.
     */
    private UUID insertTransaction(UUID accountId, UUID counterpartyAccountId,
                                   BigDecimal amount, LocalDateTime initiatedAt,
                                   String merchantCategory, TransactionType type) {
        UUID txId = UUID.randomUUID();
        String txNumber = "T" + txId.toString().replace("-", "").substring(0, 14).toUpperCase();
        jdbc.update("""
                INSERT INTO ledger_transaction
                  (id, transaction_number, account_id, counterparty_account_id,
                   type, amount, currency, status, merchant_category, initiated_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, 'USD', 'COMPLETED', ?, ?, ?)
                """,
                txId, txNumber, accountId, counterpartyAccountId,
                type.name(), amount, merchantCategory,
                initiatedAt, initiatedAt.plusMinutes(1));
        return txId;
    }
}
