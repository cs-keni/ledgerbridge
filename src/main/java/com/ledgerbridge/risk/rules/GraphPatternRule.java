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

        // Fan-out: distinct new recipients this account sent to in last 24h.
        // When knownCounterparties is empty (new user) we must avoid JPQL NOT IN ()
        // which throws IllegalArgumentException in Hibernate 6. Use a sentinel
        // single-element list that matches no real UUID — all counterparties are "new".
        List<String> exclusionList = knownCounterparties.isEmpty()
                ? List.of("00000000-0000-0000-0000-000000000000")
                : knownCounterparties;

        long fanOut = transactionRepository.countDistinctNewCounterpartiesSince(
                event.accountId(), now.minusDays(1), exclusionList);

        // Fan-in: distinct accounts that sent TO the counterparty in last 24h.
        // Uses countDistinctSendersSince (reads sender column where counterparty = receiver),
        // not countDistinctNewCounterpartiesSince which counts the counterparty's outbound.
        long fanIn = transactionRepository.countDistinctSendersSince(
                event.counterpartyAccountId(), now.minusDays(1));

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
