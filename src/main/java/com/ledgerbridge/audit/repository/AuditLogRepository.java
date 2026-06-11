package com.ledgerbridge.audit.repository;

import com.ledgerbridge.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, UUID entityId, Pageable pageable);
    Page<AuditLog> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
}
