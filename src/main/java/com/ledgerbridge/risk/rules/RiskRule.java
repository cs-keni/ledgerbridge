package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.transaction.event.TransactionEvent;

public interface RiskRule {
    RiskRuleResult evaluate(TransactionEvent event, CustomerRiskProfile profile);
}
