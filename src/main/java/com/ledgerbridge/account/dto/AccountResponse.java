package com.ledgerbridge.account.dto;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        LocalDateTime openedAt,
        LocalDateTime closedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCurrency(),
                account.getOpenedAt(),
                account.getClosedAt()
        );
    }
}
