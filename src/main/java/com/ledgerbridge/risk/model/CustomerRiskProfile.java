package com.ledgerbridge.risk.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customer_risk_profile")
@Getter
@Setter
@NoArgsConstructor
public class CustomerRiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    // Welford's online algorithm state (decision D6)
    @Column(nullable = false)
    private int transactionCount = 0;

    @Column(precision = 19, scale = 4)
    private BigDecimal amountMean;

    @Column(name = "amount_m2", precision = 30, scale = 8)
    private BigDecimal amountM2;

    // Velocity baselines
    @Column(nullable = false)
    private double avgTransactionsPerHour = 0;

    @Column(nullable = false)
    private double avgTransactionsPerDay = 0;

    // Behavioral baselines as JSONB (decision D5, Hypersistence Utils)
    // key = hour string ("9", "14"), value = frequency
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Double> typicalTransactionHours = new HashMap<>();

    // key = MCC string ("5411"), value = frequency
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Double> typicalMerchantCategories = new HashMap<>();

    // list of known counterparty account ID strings
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> typicalCounterparties = new ArrayList<>();

    @Column(nullable = false)
    private double currentRiskScore = 0;

    @Column(nullable = false, length = 10)
    private String riskTier = "LOW";

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private int totalTransactionsAnalyzed = 0;

    @PrePersist
    @PreUpdate
    void touch() {
        lastUpdated = LocalDateTime.now();
    }
}
