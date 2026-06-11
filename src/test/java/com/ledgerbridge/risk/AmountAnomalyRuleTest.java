package com.ledgerbridge.risk;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.rules.AmountAnomalyRule;
import com.ledgerbridge.risk.rules.RiskRuleResult;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmountAnomalyRuleTest {

    private final AmountAnomalyRule rule = new AmountAnomalyRule();

    // Profile: count=30, mean=110, stddev=25 (Alice baseline from test matrix)
    // M2 = variance * (n-1) = (25^2) * 29 = 625 * 29 = 18125
    private CustomerRiskProfile aliceProfile;

    @BeforeEach
    void setUp() {
        aliceProfile = new CustomerRiskProfile();
        aliceProfile.setUserId(UUID.randomUUID());
        aliceProfile.setTransactionCount(30);
        aliceProfile.setAmountMean(new BigDecimal("110.00"));
        aliceProfile.setAmountM2(new BigDecimal("18125.0000"));
    }

    private TransactionEvent event(BigDecimal amount) {
        return new TransactionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, TransactionType.DEPOSIT, amount, "USD", null, null,
                LocalDateTime.now());
    }

    @Test
    void normalAmount_scoresZero() {
        // z = (95-110)/25 = -0.6 — below 2.0 threshold
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("95.00")), aliceProfile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void zScoreTier2_scorePoint3() {
        // z = (165-110)/25 = 2.2 — in [2.0, 3.0)
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("165.00")), aliceProfile);
        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void zScoreTier3_scorePoint6() {
        // z = (190-110)/25 = 3.2 — in [3.0, 4.0)
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("190.00")), aliceProfile);
        assertThat(result.score()).isEqualTo(0.6);
    }

    @Test
    void zScoreTier4_scorePoint9() {
        // z = (220-110)/25 = 4.4 — ≥ 4.0
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("220.00")), aliceProfile);
        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void largeWire_scorePoint9() {
        // Carol scenario: z = (8000-200)/80 ≈ 97.5 — well above 4.0
        CustomerRiskProfile carolProfile = new CustomerRiskProfile();
        carolProfile.setTransactionCount(20);
        carolProfile.setAmountMean(new BigDecimal("200.00"));
        // M2 = (80^2) * 19 = 6400 * 19 = 121600
        carolProfile.setAmountM2(new BigDecimal("121600.0000"));

        RiskRuleResult result = rule.evaluate(event(new BigDecimal("8000.00")), carolProfile);
        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void insufficientHistory_scoresZero() {
        CustomerRiskProfile newProfile = new CustomerRiskProfile();
        newProfile.setTransactionCount(1); // count < 2 means can't compute variance

        RiskRuleResult result = rule.evaluate(event(new BigDecimal("5000.00")), newProfile);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.factors()).containsKey("reason");
    }

    @Test
    void negativeZScore_scoresZero() {
        // Amount below mean — z is negative, always 0.0
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("10.00")), aliceProfile);
        assertThat(result.score()).isEqualTo(0.0);
    }
}
