package com.ledgerbridge.risk;

import com.ledgerbridge.risk.engine.RiskEngine;
import com.ledgerbridge.risk.engine.RiskScoringResult;
import com.ledgerbridge.risk.metrics.RiskMetrics;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.risk.rules.*;
import com.ledgerbridge.risk.service.AlertService;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock AmountAnomalyRule    amountAnomalyRule;
    @Mock VelocityRule         velocityRule;
    @Mock BehavioralBaselineRule behavioralBaselineRule;
    @Mock GraphPatternRule     graphPatternRule;
    @Mock AlertService         alertService;
    @Spy  RiskMetrics          riskMetrics = new RiskMetrics(new SimpleMeterRegistry());

    @InjectMocks RiskEngine riskEngine;

    private final TransactionEvent event = new TransactionEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, TransactionType.DEPOSIT, BigDecimal.valueOf(100), "USD",
            null, null, LocalDateTime.of(2026, 6, 11, 12, 0));

    private final CustomerRiskProfile profile = new CustomerRiskProfile();

    private RiskRuleResult result(double score, AlertType type) {
        return new RiskRuleResult(score, type, Map.of());
    }

    private void stubRules(double amount, double velocity, double behavioral, double graph) {
        when(amountAnomalyRule.evaluate(any(), any())).thenReturn(result(amount, AlertType.AMOUNT_ANOMALY));
        when(velocityRule.evaluate(any(), any())).thenReturn(result(velocity, AlertType.VELOCITY_ANOMALY));
        when(behavioralBaselineRule.evaluate(any(), any())).thenReturn(result(behavioral, AlertType.BEHAVIORAL_ANOMALY));
        when(graphPatternRule.evaluate(any(), any())).thenReturn(result(graph, AlertType.GRAPH_PATTERN));
    }

    // ── S1 Normal — no alert ─────────────────────────────────────────────────
    @Test
    void s1Normal_noAlert() {
        stubRules(0.0, 0.0, 0.0, 0.0);
        RiskScoringResult result = riskEngine.evaluate(event, profile);
        assertThat(result.finalScore()).isEqualTo(0.0);
        assertThat(result.alertTriggered()).isFalse();
        verifyNoInteractions(alertService);
    }

    // ── S2 Velocity spike — MEDIUM (0.46) ────────────────────────────────────
    @Test
    void s2VelocitySpike_mediumAlert() {
        stubRules(0.6, 0.7, 0.5, 0.0);
        RiskAlert mockAlert = new RiskAlert();
        mockAlert.setSeverity(AlertSeverity.MEDIUM);
        when(alertService.createAlert(any(), any(), eq(AlertSeverity.MEDIUM), any(double.class), any()))
                .thenReturn(mockAlert);

        RiskScoringResult result = riskEngine.evaluate(event, profile);

        // raw = 0.6*0.25 + 0.7*0.30 + 0.5*0.20 + 0.0*0.25 = 0.460
        assertThat(result.finalScore()).isCloseTo(0.460, within(0.001));
        assertThat(result.alertTriggered()).isTrue();
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    // ── S3 Large+new counterparty — HIGH via tier-1 escalation ───────────────
    @Test
    void s3LargeAmount_highViaTier1Escalation() {
        stubRules(0.9, 0.0, 0.5, 0.0);
        RiskAlert mockAlert = new RiskAlert();
        mockAlert.setSeverity(AlertSeverity.HIGH);
        when(alertService.createAlert(any(), any(), eq(AlertSeverity.HIGH), any(double.class), any()))
                .thenReturn(mockAlert);

        RiskScoringResult result = riskEngine.evaluate(event, profile);

        // raw = 0.9*0.25 + 0.0*0.30 + 0.5*0.20 + 0.0*0.25 = 0.325
        // tier-1: AmountAnomaly=0.9 ≥ 0.8 → floor max(0.325, 0.65) = 0.65
        assertThat(result.finalScore()).isEqualTo(0.65);
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    // ── S4 Fan-out — HIGH via tier-1 escalation ──────────────────────────────
    @Test
    void s4FanOut_highViaGraphEscalation() {
        stubRules(0.0, 0.4, 0.2, 0.8);
        RiskAlert mockAlert = new RiskAlert();
        mockAlert.setSeverity(AlertSeverity.HIGH);
        when(alertService.createAlert(any(), any(), eq(AlertSeverity.HIGH), any(double.class), any()))
                .thenReturn(mockAlert);

        RiskScoringResult result = riskEngine.evaluate(event, profile);

        // raw = 0.0*0.25 + 0.4*0.30 + 0.2*0.20 + 0.8*0.25 = 0.360
        // tier-1: GraphPattern=0.8 ≥ 0.8 → floor max(0.360, 0.65) = 0.65
        assertThat(result.finalScore()).isEqualTo(0.65);
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    // ── S5 Round-trip — CRITICAL via tier-2 multi-rule escalation ────────────
    @Test
    void s5RoundTrip_criticalViaTier2Escalation() {
        stubRules(0.9, 0.7, 0.5, 0.6);
        RiskAlert mockAlert = new RiskAlert();
        mockAlert.setSeverity(AlertSeverity.CRITICAL);
        when(alertService.createAlert(any(), any(), eq(AlertSeverity.CRITICAL), any(double.class), any()))
                .thenReturn(mockAlert);

        RiskScoringResult result = riskEngine.evaluate(event, profile);

        // raw = 0.9*0.25 + 0.7*0.30 + 0.5*0.20 + 0.6*0.25 = 0.685
        // tier-2: amount=0.9, velocity=0.7, graph=0.6 → 3 rules ≥ 0.6 → floor max(0.685, 0.80) = 0.80
        assertThat(result.finalScore()).isEqualTo(0.80);
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void tier2RequiresThreeRulesNotTwo() {
        // 2 rules ≥ 0.6 — no tier-2, tier-1 fires because amount=0.9
        stubRules(0.9, 0.7, 0.3, 0.0);
        RiskAlert mockAlert = new RiskAlert();
        mockAlert.setSeverity(AlertSeverity.HIGH);
        when(alertService.createAlert(any(), any(), eq(AlertSeverity.HIGH), any(double.class), any()))
                .thenReturn(mockAlert);

        RiskScoringResult result = riskEngine.evaluate(event, profile);
        // raw = 0.9*0.25 + 0.7*0.30 + 0.3*0.20 = 0.225+0.210+0.060 = 0.495
        // tier-1: 0.9 ≥ 0.8 → floor max(0.495, 0.65) = 0.65 → HIGH not CRITICAL
        assertThat(result.finalScore()).isEqualTo(0.65);
        assertThat(result.alert().getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void scoreBelowThreshold_noAlertCreated() {
        stubRules(0.3, 0.0, 0.0, 0.0);
        // raw = 0.3*0.25 = 0.075
        RiskScoringResult result = riskEngine.evaluate(event, profile);
        assertThat(result.finalScore()).isLessThan(0.4);
        assertThat(result.alertTriggered()).isFalse();
        verifyNoInteractions(alertService);
    }
}
