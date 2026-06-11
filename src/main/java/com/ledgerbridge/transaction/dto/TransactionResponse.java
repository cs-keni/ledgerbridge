package com.ledgerbridge.transaction.dto;

import com.ledgerbridge.transaction.model.LedgerTransaction;
import com.ledgerbridge.transaction.model.TransactionStatus;
import com.ledgerbridge.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String transactionNumber,
        UUID accountId,
        UUID counterpartyAccountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String merchantCategory,
        String description,
        String correlationId,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt
) {
    public static TransactionResponse from(LedgerTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionNumber(),
                tx.getAccountId(),
                tx.getCounterpartyAccountId(),
                tx.getType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getMerchantCategory(),
                tx.getDescription(),
                tx.getCorrelationId(),
                tx.getInitiatedAt(),
                tx.getCompletedAt()
        );
    }
}
