package com.ledgerbridge.account.dto;

import com.ledgerbridge.account.model.AccountType;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull AccountType type,
        String currency
) {}
