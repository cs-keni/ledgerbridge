package com.ledgerbridge.audit.dto;

import com.ledgerbridge.audit.model.AuditAction;
import com.ledgerbridge.audit.model.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String entityType,
        UUID entityId,
        AuditAction action,
        UUID userId,
        String ipAddress,
        String correlationId,
        String outcome,
        LocalDateTime occurredAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getUserId(),
                log.getIpAddress(),
                log.getCorrelationId(),
                log.getOutcome(),
                log.getOccurredAt());
    }
}
