package com.ledgerbridge.risk.dto;

import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertStatus;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.RiskAlert;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record RiskAlertResponse(
        UUID id,
        String alertNumber,
        UUID transactionId,
        UUID userId,
        AlertType alertType,
        AlertSeverity severity,
        AlertStatus status,
        double riskScore,
        Map<String, Object> ruleDetails,
        LocalDateTime createdAt
) {
    public static RiskAlertResponse from(RiskAlert alert) {
        return new RiskAlertResponse(
                alert.getId(),
                alert.getAlertNumber(),
                alert.getTransactionId(),
                alert.getUserId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getRiskScore(),
                alert.getRuleDetails(),
                alert.getCreatedAt());
    }
}
