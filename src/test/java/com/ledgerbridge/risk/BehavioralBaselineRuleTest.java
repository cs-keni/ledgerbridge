package com.ledgerbridge.risk;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.rules.BehavioralBaselineRule;
import com.ledgerbridge.risk.rules.RiskRuleResult;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BehavioralBaselineRuleTest {

    private final BehavioralBaselineRule rule = new BehavioralBaselineRule();

    private CustomerRiskProfile profile;
    private final UUID knownCounterparty = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        profile = new CustomerRiskProfile();
        profile.setUserId(UUID.randomUUID());

        Map<String, Double> hours = new HashMap<>();
        hours.put("11", 0.10);  // 11am is normal
        hours.put("14", 0.08);  // 2pm is normal
        profile.setTypicalTransactionHours(hours);

        Map<String, Double> mccs = new HashMap<>();
        mccs.put("5411", 0.45); // grocery is normal
        mccs.put("5541", 0.35); // gas is normal
        profile.setTypicalMerchantCategories(mccs);

        profile.setTypicalCounterparties(new ArrayList<>(List.of(knownCounterparty.toString())));
    }

    private TransactionEvent event(int hour, String mcc, UUID counterparty, BigDecimal amount) {
        LocalDateTime at = LocalDateTime.of(2026, 6, 10, hour, 0);
        return new TransactionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                counterparty, TransactionType.TRANSFER_DEBIT, amount, "USD", mcc, null, at);
    }

    @Test
    void allNormal_scoresZero() {
        var result = rule.evaluate(
                event(11, "5411", knownCounterparty, new BigDecimal("95.00")), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void unusualHour_addsPoint2() {
        // 3am is not in the profile (freq=0.0 < 0.01)
        var result = rule.evaluate(
                event(3, "5411", knownCounterparty, new BigDecimal("95.00")), profile);
        assertThat(result.score()).isEqualTo(0.2);
        assertThat(result.factors()).containsKey("unusualHour");
    }

    @Test
    void unusualMcc_addsPoint3() {
        // MCC 6012 not in profile
        var result = rule.evaluate(
                event(11, "6012", knownCounterparty, new BigDecimal("95.00")), profile);
        assertThat(result.score()).isEqualTo(0.3);
        assertThat(result.factors()).containsKey("unusualMcc");
    }

    @Test
    void newCounterpartyLowValue_addsPoint2() {
        UUID newCounterparty = UUID.randomUUID();
        var result = rule.evaluate(
                event(11, "5411", newCounterparty, new BigDecimal("200.00")), profile);
        assertThat(result.score()).isEqualTo(0.2);
        assertThat(result.factors()).containsKey("newCounterparty");
        assertThat(result.factors().get("highValue")).isEqualTo(false);
    }

    @Test
    void newCounterpartyHighValue_addsPoint5() {
        UUID newCounterparty = UUID.randomUUID();
        var result = rule.evaluate(
                event(14, "5411", newCounterparty, new BigDecimal("8000.00")), profile);
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.factors().get("highValue")).isEqualTo(true);
    }

    @Test
    void allSignalsCombined_cappedAtPoint9() {
        UUID newCounterparty = UUID.randomUUID();
        // 3am (unusual) + MCC 6012 (unusual) + new counterparty > $5K = 0.2+0.3+0.5 = 1.0 → capped at 0.9
        var result = rule.evaluate(
                event(3, "6012", newCounterparty, new BigDecimal("8000.00")), profile);
        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void unusualHourAndUnusualMcc_point5() {
        // 3am + MCC 6012 = 0.2 + 0.3 = 0.5
        var result = rule.evaluate(
                event(3, "6012", knownCounterparty, new BigDecimal("100.00")), profile);
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void nullMcc_noMccPenalty() {
        UUID newCounterparty = UUID.randomUUID();
        // No MCC (wire transfer) + unusual hour + new counterparty > $5K = 0.2+0.5=0.7
        var result = rule.evaluate(
                event(3, null, newCounterparty, new BigDecimal("8000.00")), profile);
        assertThat(result.score()).isEqualTo(0.7);
    }

    @Test
    void noCounterparty_noCounterpartyPenalty() {
        // Deposit with no counterparty
        var result = rule.evaluate(
                event(11, "5411", null, new BigDecimal("100.00")), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }
}
