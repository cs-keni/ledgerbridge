package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.transaction.event.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class BehavioralBaselineRule implements RiskRule {

    private static final double UNUSUAL_HOUR_THRESHOLD = 0.01;
    private static final double UNUSUAL_MCC_THRESHOLD  = 0.02;
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final double MAX_SCORE = 0.9;

    @Override
    public RiskRuleResult evaluate(TransactionEvent event, CustomerRiskProfile profile) {
        double score = 0.0;
        Map<String, Object> factors = new HashMap<>();

        // Time-of-day anomaly
        String hour = String.valueOf(event.initiatedAt().getHour());
        double hourFreq = profile.getTypicalTransactionHours().getOrDefault(hour, 0.0);
        if (hourFreq < UNUSUAL_HOUR_THRESHOLD) {
            score += 0.2;
            factors.put("unusualHour", hour);
            factors.put("hourFrequency", hourFreq);
        }

        // MCC anomaly — only checked when a merchant category is present
        String mcc = event.merchantCategory();
        if (mcc != null && !mcc.isBlank()) {
            double mccFreq = profile.getTypicalMerchantCategories().getOrDefault(mcc, 0.0);
            if (mccFreq < UNUSUAL_MCC_THRESHOLD) {
                score += 0.3;
                factors.put("unusualMcc", mcc);
                factors.put("mccFrequency", mccFreq);
            }
        }

        // New counterparty check
        if (event.counterpartyAccountId() != null) {
            String counterpartyStr = event.counterpartyAccountId().toString();
            boolean isNew = !profile.getTypicalCounterparties().contains(counterpartyStr);
            if (isNew) {
                boolean highValue = event.amount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
                score += highValue ? 0.5 : 0.2;
                factors.put("newCounterparty", counterpartyStr);
                factors.put("highValue", highValue);
            }
        }

        return new RiskRuleResult(Math.min(score, MAX_SCORE), AlertType.BEHAVIORAL_ANOMALY, factors);
    }
}
