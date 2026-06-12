package com.ledgerbridge.risk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_transaction_event")
public class ProcessedTransactionEvent {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public ProcessedTransactionEvent() {}

    public ProcessedTransactionEvent(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getTransactionId() { return transactionId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
