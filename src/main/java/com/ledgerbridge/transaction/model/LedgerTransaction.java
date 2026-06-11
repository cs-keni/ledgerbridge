package com.ledgerbridge.transaction.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_transaction")
@Getter
@Setter
@NoArgsConstructor
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String transactionNumber;

    @Column(nullable = false)
    private UUID accountId;

    @Column
    private UUID counterpartyAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(length = 10)
    private String merchantCategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime initiatedAt;

    @Column
    private LocalDateTime completedAt;

    @Column(length = 36)
    private String correlationId;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String deviceFingerprint;

    @PrePersist
    void prePersist() {
        if (initiatedAt == null) {
            initiatedAt = LocalDateTime.now();
        }
    }
}
