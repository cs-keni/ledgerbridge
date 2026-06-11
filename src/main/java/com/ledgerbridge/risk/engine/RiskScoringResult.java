package com.ledgerbridge.risk.engine;

import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.risk.rules.RiskRuleResult;

public record RiskScoringResult(
        double finalScore,
        boolean alertTriggered,
        RiskAlert alert,
        RiskRuleResult amountResult,
        RiskRuleResult velocityResult,
        RiskRuleResult behavioralResult,
        RiskRuleResult graphResult
) {}
