package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GraphPatternRule implements RiskRule {

    private static final int FAN_THRESHOLD = 5;

    private final TransactionRepository transactionRepository;

    @Override
    public RiskRuleResult evaluate(TransactionEvent event, CustomerRiskProfile profile) {
        if (event.counterpartyAccountId() == null) {
            return new RiskRuleResult(0.0, AlertType.GRAPH_PATTERN, Map.of());
        }

        var now = event.initiatedAt();
        List<String> knownCounterparties = profile.getTypicalCounterparties();

        // Fan-out: distinct new recipients this account sent to in last 24h
        long fanOut = transactionRepository.countDistinctNewCounterpartiesSince(
                event.accountId(), now.minusDays(1), knownCounterparties);

        // Fan-in: distinct new senders to the counterparty's account in last 24h
        long fanIn = transactionRepository.countDistinctNewCounterpartiesSince(
                event.counterpartyAccountId(), now.minusDays(1), knownCounterparties);

        // Round-trip: same exact amount returned from counterparty within 2h
        boolean roundTrip = transactionRepository.existsRoundTrip(
                event.accountId(), event.counterpartyAccountId(),
                event.amount(), now.minusHours(2));

        Map<String, Object> factors = new HashMap<>();
        factors.put("fanOut", fanOut);
        factors.put("fanIn", fanIn);
        factors.put("roundTrip", roundTrip);

        // Highest-risk pattern wins (not additive — distinct fraud typologies)
        if (fanOut >= FAN_THRESHOLD) {
            return new RiskRuleResult(0.8, AlertType.GRAPH_PATTERN, factors);
        }
        if (fanIn >= FAN_THRESHOLD) {
            return new RiskRuleResult(0.7, AlertType.GRAPH_PATTERN, factors);
        }
        if (roundTrip) {
            return new RiskRuleResult(0.6, AlertType.GRAPH_PATTERN, factors);
        }
        return new RiskRuleResult(0.0, AlertType.GRAPH_PATTERN, factors);
    }
}
