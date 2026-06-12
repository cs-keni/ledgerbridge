package com.ledgerbridge.risk.dto;

public record AlertStatsResponse(
        long open,
        long underReview,
        long critical
) {}
