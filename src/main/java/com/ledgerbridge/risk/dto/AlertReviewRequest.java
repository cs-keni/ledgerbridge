package com.ledgerbridge.risk.dto;

import com.ledgerbridge.risk.model.AlertStatus;
import jakarta.validation.constraints.NotNull;

public record AlertReviewRequest(
        @NotNull AlertStatus status,
        String notes
) {}
