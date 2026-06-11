package com.ledgerbridge.risk.repository;

import com.ledgerbridge.risk.model.AlertStatus;
import com.ledgerbridge.risk.model.RiskAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskAlertRepository extends JpaRepository<RiskAlert, UUID> {
    Page<RiskAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);
    Page<RiskAlert> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByStatus(AlertStatus status);
    boolean existsByTransactionId(UUID transactionId);
}
