package com.ledgerbridge.transaction.event;

import com.ledgerbridge.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionEvent(
        UUID transactionId,
        UUID accountId,
        UUID userId,
        UUID counterpartyAccountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String correlationId,
        LocalDateTime initiatedAt
) {}
