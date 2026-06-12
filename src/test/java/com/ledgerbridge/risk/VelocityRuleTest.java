package com.ledgerbridge.risk;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.rules.RiskRuleResult;
import com.ledgerbridge.risk.rules.VelocityRule;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    @Mock TransactionRepository transactionRepository;
    @InjectMocks VelocityRule rule;

    private CustomerRiskProfile profile;
    private final UUID accountId = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.of(2026, 6, 11, 3, 30);

    @BeforeEach
    void setUp() {
        profile = new CustomerRiskProfile();
        profile.setUserId(UUID.randomUUID());
        // Bob baseline: 0.021/hr, 0.5/day
        profile.setAvgTransactionsPerHour(0.021);
        profile.setAvgTransactionsPerDay(0.5);
    }

    private TransactionEvent event() {
        return new TransactionEvent(UUID.randomUUID(), accountId, UUID.randomUUID(),
                null, TransactionType.DEPOSIT, BigDecimal.valueOf(200), "USD", null, null, now);
    }

    private void stubCounts(long lastHour, long lastDay) {
        stubCounts(lastHour, lastDay, lastDay); // week defaults to day for backward compat
    }

    private void stubCounts(long lastHour, long lastDay, long lastWeek) {
        TransactionRepository.VelocityWindowCounts counts = new TransactionRepository.VelocityWindowCounts() {
            public long getLastHour() { return lastHour; }
            public long getLastDay()  { return lastDay; }
            public long getLastWeek() { return lastWeek; }
        };
        when(transactionRepository.countVelocityWindows(eq(accountId), any(), any(), any()))
                .thenReturn(counts);
    }

    @Test
    void noSpike_scoresZero() {
        stubCounts(1, 1);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void hourSpikeOnly_scorePoint5() {
        // 8 in last hour > max(3, 0.021*3)=3 → hourly spike only
        // 1 in last day: 1 > 0.5*2.5=1.25 is false → no daily spike
        stubCounts(8, 1);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void daySpikeOnly_scorePoint4() {
        // 5 in last day > max(4, 0.5*2.5)=4; only 2 in last hour (≤ threshold 3)
        // weekSpike: lastWeek=5 vs threshold max(20, 0.5*7*2)=20 → false
        stubCounts(2, 5);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.4);
    }

    @Test
    void weekSpikeOnly_scorePoint3() {
        // 50 in last week > max(20, 0.5*7*2)=20 → week spike
        // 2 in last hour (≤ 3), 3 in last day (≤ max(4,1.25)=4)
        stubCounts(2, 3, 50);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.3);
    }

    @Test
    void daySpikeAndWeekSpike_escalatesToPoint5() {
        // 5 in last day (>4) AND 50 in last week (>20) → sustained pattern, escalated to 0.5
        stubCounts(2, 5, 50);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    void bothSpikes_scorePoint7() {
        // Bob scenario: 8 in 45min hour (>3), 8 in day (>1.25)
        stubCounts(8, 8);
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.7);
    }

    @Test
    void hourlyThresholdUsesMax3WhenBaselineIsLow() {
        // Very low baseline: 0.001/hr → threshold = max(3, 0.003) = 3
        profile.setAvgTransactionsPerHour(0.001);
        stubCounts(3, 1); // exactly 3 — NOT strictly greater
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void hourlyThresholdUsesBaselineWhenHigh() {
        // High baseline: 5/hr → threshold = max(3, 15) = 15
        profile.setAvgTransactionsPerHour(5.0);
        stubCounts(10, 1); // 10 < 15, no spike
        RiskRuleResult result = rule.evaluate(event(), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }
}
