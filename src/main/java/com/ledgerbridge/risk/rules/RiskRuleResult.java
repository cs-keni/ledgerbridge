package com.ledgerbridge.risk.rules;

import com.ledgerbridge.risk.model.AlertType;

import java.util.Map;

public record RiskRuleResult(
        double score,
        AlertType alertType,
        Map<String, Object> factors
) {}
