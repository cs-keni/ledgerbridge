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
        long lastWeek = counts.getLastWeek();

        double hourlyBaseline  = profile.getAvgTransactionsPerHour();
        double dailyBaseline   = profile.getAvgTransactionsPerDay();
        double weeklyBaseline  = dailyBaseline * 7;

        double hourlyThreshold  = Math.max(3,  hourlyBaseline * 3);
        // Floor at 4 protects new users (zero baseline) while EWMA converges.
        double dailyThreshold   = Math.max(4,  dailyBaseline  * 2.5);
        double weeklyThreshold  = Math.max(20, weeklyBaseline * 2.0);

        boolean hourSpike = lastHour > hourlyThreshold;
        boolean daySpike  = lastDay  > dailyThreshold;
        boolean weekSpike = lastWeek > weeklyThreshold;

        double score;
        if (hourSpike && daySpike)     score = 0.7;
        else if (hourSpike)            score = 0.5;
        else if (daySpike && weekSpike) score = 0.5; // sustained multi-day pattern escalates
        else if (daySpike)             score = 0.4;
        else if (weekSpike)            score = 0.3;
        else                           score = 0.0;

        return new RiskRuleResult(score, AlertType.VELOCITY_ANOMALY,
                Map.of("lastHour", lastHour, "lastDay", lastDay, "lastWeek", lastWeek,
                        "hourlyThreshold", hourlyThreshold, "dailyThreshold", dailyThreshold,
                        "weeklyThreshold", weeklyThreshold,
                        "hourSpike", hourSpike, "daySpike", daySpike, "weekSpike", weekSpike));
    }
}
