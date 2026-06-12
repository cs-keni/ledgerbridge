package com.ledgerbridge.risk;

import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.rules.GraphPatternRule;
import com.ledgerbridge.risk.rules.RiskRuleResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphPatternRuleTest {

    @Mock TransactionRepository transactionRepository;
    @InjectMocks GraphPatternRule rule;

    private CustomerRiskProfile profile;
    private final UUID accountId      = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final LocalDateTime now   = LocalDateTime.of(2026, 6, 11, 14, 0);

    @BeforeEach
    void setUp() {
        profile = new CustomerRiskProfile();
        profile.setUserId(UUID.randomUUID());
        profile.setTypicalCounterparties(new ArrayList<>());
    }

    private TransactionEvent event(BigDecimal amount) {
        return new TransactionEvent(UUID.randomUUID(), accountId, UUID.randomUUID(),
                counterpartyId, TransactionType.TRANSFER_DEBIT, amount, "USD", null, null, now);
    }

    private void stubFanOut(long count) {
        when(transactionRepository.countDistinctNewCounterpartiesSince(
                eq(accountId), any(), anyList())).thenReturn(count);
    }

    private void stubFanIn(long count) {
        when(transactionRepository.countDistinctSendersSince(
                eq(counterpartyId), any())).thenReturn(count);
    }

    private void stubRoundTrip(boolean exists) {
        when(transactionRepository.existsRoundTrip(any(), any(), any(), any())).thenReturn(exists);
    }

    @Test
    void noCounterparty_scoresZero() {
        TransactionEvent depositEvent = new TransactionEvent(UUID.randomUUID(), accountId,
                UUID.randomUUID(), null, TransactionType.DEPOSIT,
                BigDecimal.valueOf(100), "USD", null, null, now);
        RiskRuleResult result = rule.evaluate(depositEvent, profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void normalTransaction_scoresZero() {
        stubFanOut(1);
        stubFanIn(0);
        stubRoundTrip(false);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("500.00")), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void fanOut5orMore_scorePoint8() {
        stubFanOut(5);
        stubFanIn(0);
        stubRoundTrip(false);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("1000.00")), profile);
        assertThat(result.score()).isEqualTo(0.8);
        assertThat(result.alertType()).isEqualTo(AlertType.GRAPH_PATTERN);
    }

    @Test
    void fanOutBelowThreshold_noFanOutSignal() {
        stubFanOut(4);
        stubFanIn(0);
        stubRoundTrip(false);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("1000.00")), profile);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void fanIn5orMore_scorePoint7() {
        stubFanOut(0);
        stubFanIn(5);
        stubRoundTrip(false);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("1000.00")), profile);
        assertThat(result.score()).isEqualTo(0.7);
    }

    @Test
    void roundTrip_scorePoint6() {
        stubFanOut(0);
        stubFanIn(0);
        stubRoundTrip(true);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("25000.00")), profile);
        assertThat(result.score()).isEqualTo(0.6);
    }

    @Test
    void fanOutTakesPrecedenceOverFanIn() {
        // Fan-out ≥ 5 wins over fan-in ≥ 5 (higher risk pattern)
        stubFanOut(6);
        stubFanIn(6);
        stubRoundTrip(true);
        RiskRuleResult result = rule.evaluate(event(new BigDecimal("1000.00")), profile);
        assertThat(result.score()).isEqualTo(0.8);
    }
}
