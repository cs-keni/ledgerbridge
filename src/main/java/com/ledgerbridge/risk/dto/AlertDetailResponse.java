package com.ledgerbridge.risk.dto;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertStatus;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.transaction.model.LedgerTransaction;
import com.ledgerbridge.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AlertDetailResponse(
        UUID id,
        String alertNumber,
        UUID transactionId,
        UUID userId,
        AlertType alertType,
        AlertSeverity severity,
        AlertStatus status,
        double riskScore,
        Map<String, Object> ruleDetails,
        LocalDateTime createdAt,
        UUID reviewedByAdminId,
        String adminNotes,
        LocalDateTime reviewedAt,
        BigDecimal amount,
        String currency,
        TransactionType transactionType,
        UUID accountId,
        String accountNumber,
        UUID counterpartyAccountId,
        String description,
        String merchantCategory,
        LocalDateTime transactionInitiatedAt
) {
    public static AlertDetailResponse from(RiskAlert alert, LedgerTransaction tx, Account account) {
        return new AlertDetailResponse(
                alert.getId(),
                alert.getAlertNumber(),
                alert.getTransactionId(),
                alert.getUserId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getRiskScore(),
                alert.getRuleDetails(),
                alert.getCreatedAt(),
                alert.getReviewedByAdminId(),
                alert.getAdminNotes(),
                alert.getReviewedAt(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getType(),
                tx.getAccountId(),
                account != null ? account.getAccountNumber() : null,
                tx.getCounterpartyAccountId(),
                tx.getDescription(),
                tx.getMerchantCategory(),
                tx.getInitiatedAt()
        );
    }
}
