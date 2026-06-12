package com.ledgerbridge.audit.service;

import com.ledgerbridge.audit.dto.AuditLogResponse;
import com.ledgerbridge.audit.model.AuditAction;
import com.ledgerbridge.audit.model.AuditLog;
import com.ledgerbridge.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Persists one audit entry. Uses REQUIRES_NEW so a failure in the calling
     * transaction does not suppress the audit record (D15 — always fires including failures).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, UUID entityId,
                       UUID userId, String ipAddress, String correlationId, String outcome) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setUserId(userId);
        log.setIpAddress(ipAddress);
        log.setCorrelationId(correlationId);
        log.setOutcome(outcome);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getByEntity(String entityType, UUID entityId, Pageable pageable) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId, pageable)
                .map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getByUser(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByOccurredAtDesc(userId, pageable)
                .map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAll(Pageable pageable) {
        return repository.findAll(pageable).map(AuditLogResponse::from);
    }
}
