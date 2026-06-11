package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VelocityRule implements RiskRule {

    private final TransactionRepository transactionRepository;

    @Override
    public RiskRuleResult evaluate(TransactionEvent event, CustomerRiskProfile profile) {
        var now = event.initiatedAt();
        var counts = transactionRepository.countVelocityWindows(
                event.accountId(),
                now.minusHours(1),
                now.minusDays(1),
                now.minusDays(7));

        long lastHour = counts.getLastHour();
        long lastDay  = counts.getLastDay();

        double hourlyBaseline = profile.getAvgTransactionsPerHour();
        double dailyBaseline  = profile.getAvgTransactionsPerDay();

        double hourlyThreshold = Math.max(3, hourlyBaseline * 3);
        double dailyThreshold  = dailyBaseline * 2.5;

        boolean hourSpike = lastHour > hourlyThreshold;
        boolean daySpike  = lastDay  > dailyThreshold;

        double score;
        if (hourSpike && daySpike) score = 0.7;
        else if (hourSpike)        score = 0.5;
        else if (daySpike)         score = 0.4;
        else                       score = 0.0;

        return new RiskRuleResult(score, AlertType.VELOCITY_ANOMALY,
                Map.of("lastHour", lastHour, "lastDay", lastDay,
                        "hourlyThreshold", hourlyThreshold, "dailyThreshold", dailyThreshold,
                        "hourSpike", hourSpike, "daySpike", daySpike));
    }
}
