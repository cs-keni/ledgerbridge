package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.transaction.event.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AmountAnomalyRule implements RiskRule {

    // Need count ≥ 2 to compute Welford's variance. Test matrix expects scoring
    // from count=5 (Eve, S5) — 10 was too conservative per RISK_ENGINE_TEST_MATRIX.md.
    private static final int MIN_HISTORY_COUNT = 2;

    @Override
    public RiskRuleResult evaluate(TransactionEvent event, CustomerRiskProfile profile) {
        int count = profile.getTransactionCount();

        if (count < MIN_HISTORY_COUNT) {
            return new RiskRuleResult(0.0, AlertType.AMOUNT_ANOMALY,
                    Map.of("reason", "insufficient_history", "count", count));
        }

        BigDecimal mean = profile.getAmountMean();
        BigDecimal m2 = profile.getAmountM2();

        // Welford's: variance = M2 / (n-1), stddev = sqrt(variance)
        double variance = m2.doubleValue() / (count - 1);
        double stddev = Math.sqrt(variance);

        if (stddev < 0.01) {
            // Zero-variance baseline: any deviation is suspicious but unquantifiable
            boolean deviant = event.amount().compareTo(mean) != 0;
            return new RiskRuleResult(deviant ? 0.3 : 0.0, AlertType.AMOUNT_ANOMALY,
                    Map.of("reason", "zero_variance", "amount", event.amount(), "mean", mean));
        }

        double z = (event.amount().doubleValue() - mean.doubleValue()) / stddev;

        double score;
        if (z < 2.0) score = 0.0;
        else if (z < 3.0) score = 0.3;
        else if (z < 4.0) score = 0.6;
        else score = 0.9;

        return new RiskRuleResult(score, AlertType.AMOUNT_ANOMALY,
                Map.of("amount", event.amount(), "mean", mean,
                        "stddev", stddev, "zScore", z));
    }
}
