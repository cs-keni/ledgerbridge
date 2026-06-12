package com.ledgerbridge.risk.repository;

import com.ledgerbridge.risk.model.ProcessedTransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedTransactionEventRepository extends JpaRepository<ProcessedTransactionEvent, UUID> {
    boolean existsByTransactionId(UUID transactionId);
}
