package com.ledgerbridge.risk.engine;

import com.ledgerbridge.risk.metrics.RiskMetrics;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.risk.rules.*;
import com.ledgerbridge.risk.service.AlertService;
import com.ledgerbridge.transaction.event.TransactionEvent;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RiskEngine {

    // Weights must sum to 1.0
    private static final double AMOUNT_WEIGHT     = 0.25;
    private static final double VELOCITY_WEIGHT   = 0.30;
    private static final double BEHAVIORAL_WEIGHT = 0.20;
    private static final double GRAPH_WEIGHT      = 0.25;

    // Escalation tier 1: any single rule raw ≥ 0.8 → total score floor 0.65
    private static final double TIER1_RAW_THRESHOLD = 0.8;
    private static final double TIER1_FLOOR         = 0.65;

    // Escalation tier 2: ≥3 rules each raw ≥ 0.6 → total score floor 0.80 (D14)
    private static final double TIER2_RAW_THRESHOLD = 0.6;
    private static final int    TIER2_MIN_RULES      = 3;
    private static final double TIER2_FLOOR          = 0.80;

    private static final double ALERT_THRESHOLD = 0.4;

    private final AmountAnomalyRule    amountAnomalyRule;
    private final VelocityRule         velocityRule;
    private final BehavioralBaselineRule behavioralBaselineRule;
    private final GraphPatternRule     graphPatternRule;
    private final AlertService         alertService;
    private final RiskMetrics          riskMetrics;

    public RiskScoringResult evaluate(TransactionEvent event, CustomerRiskProfile profile) {
        Timer.Sample sample = riskMetrics.startScoringSample();
        boolean shouldAlert = false;
        try {
            RiskRuleResult amountResult     = amountAnomalyRule.evaluate(event, profile);
            RiskRuleResult velocityResult   = velocityRule.evaluate(event, profile);
            RiskRuleResult behavioralResult = behavioralBaselineRule.evaluate(event, profile);
            RiskRuleResult graphResult      = graphPatternRule.evaluate(event, profile);

            double weighted = amountResult.score()     * AMOUNT_WEIGHT
                            + velocityResult.score()   * VELOCITY_WEIGHT
                            + behavioralResult.score() * BEHAVIORAL_WEIGHT
                            + graphResult.score()      * GRAPH_WEIGHT;

            List<RiskRuleResult> results = List.of(amountResult, velocityResult, behavioralResult, graphResult);

            // Tier 2 check first (stronger) — ≥3 rules each at ≥ 0.6
            long tier2Count = results.stream().filter(r -> r.score() >= TIER2_RAW_THRESHOLD).count();
            boolean tier2   = tier2Count >= TIER2_MIN_RULES;

            // Tier 1 — any single rule at ≥ 0.8
            boolean tier1 = results.stream().anyMatch(r -> r.score() >= TIER1_RAW_THRESHOLD);

            double finalScore = weighted;
            if (tier2)       finalScore = Math.max(finalScore, TIER2_FLOOR);
            else if (tier1)  finalScore = Math.max(finalScore, TIER1_FLOOR);
            finalScore = Math.min(finalScore, 1.0);

            shouldAlert = finalScore >= ALERT_THRESHOLD;
            RiskAlert alert = null;
            if (shouldAlert) {
                AlertSeverity severity     = resolveSeverity(finalScore);
                AlertType     dominantType = resolveDominantType(results);
                Map<String, Object> ruleDetails = buildRuleDetails(
                        amountResult, velocityResult, behavioralResult, graphResult, finalScore, tier1, tier2);
                alert = alertService.createAlert(event, dominantType, severity, finalScore, ruleDetails);
                riskMetrics.incrementAlertCreated(dominantType, severity);
            }

            return new RiskScoringResult(finalScore, shouldAlert, alert,
                    amountResult, velocityResult, behavioralResult, graphResult);
        } finally {
            riskMetrics.stopScoringSample(sample, shouldAlert);
        }
    }

    // ALERT_THRESHOLD is 0.4, so any live alert is at minimum MEDIUM.
    // AlertSeverity.LOW exists in the enum but is only produced by seeded demo data.
    private AlertSeverity resolveSeverity(double score) {
        if (score >= 0.8) return AlertSeverity.CRITICAL;
        if (score >= 0.6) return AlertSeverity.HIGH;
        return AlertSeverity.MEDIUM;
    }

    private AlertType resolveDominantType(List<RiskRuleResult> results) {
        return results.stream()
                .max(Comparator.comparingDouble(RiskRuleResult::score))
                .map(RiskRuleResult::alertType)
                .orElse(AlertType.AMOUNT_ANOMALY);
    }

    private Map<String, Object> buildRuleDetails(
            RiskRuleResult amount, RiskRuleResult velocity,
            RiskRuleResult behavioral, RiskRuleResult graph,
            double finalScore, boolean tier1, boolean tier2) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("finalScore", finalScore);
        details.put("escalationTier1", tier1);
        details.put("escalationTier2", tier2);
        details.put("amountAnomaly",    Map.of("score", amount.score(),     "factors", amount.factors()));
        details.put("velocity",         Map.of("score", velocity.score(),   "factors", velocity.factors()));
        details.put("behavioral",       Map.of("score", behavioral.score(), "factors", behavioral.factors()));
        details.put("graphPattern",     Map.of("score", graph.score(),      "factors", graph.factors()));
        return details;
    }
}
