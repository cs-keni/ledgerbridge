package com.ledgerbridge.risk.service;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.repository.CustomerRiskProfileRepository;
import com.ledgerbridge.transaction.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRiskProfileService {

    private static final int MAX_COUNTERPARTIES = 50;

    private final CustomerRiskProfileRepository repository;

    @Transactional
    public CustomerRiskProfile getOrCreate(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            try {
                CustomerRiskProfile profile = new CustomerRiskProfile();
                profile.setUserId(userId);
                return repository.save(profile);
            } catch (DataIntegrityViolationException e) {
                // Concurrent insert raced us — re-fetch the winner's record
                return repository.findByUserId(userId)
                        .orElseThrow(() -> new IllegalStateException("Profile missing after concurrent insert", e));
            }
        });
    }

    /**
     * Welford's online update — call only when score < alert threshold (D19).
     * Increments count, updates mean/M2, hour/MCC frequency maps, and
     * typicalCounterparties (first-appearance policy, max 50 LRU eviction).
     */
    @Transactional
    public void updateProfile(CustomerRiskProfile profile, TransactionEvent event) {
        int newCount = profile.getTransactionCount() + 1;
        BigDecimal amount = event.amount();

        // Welford's online algorithm
        if (profile.getAmountMean() == null || newCount == 1) {
            profile.setAmountMean(amount);
            profile.setAmountM2(BigDecimal.ZERO);
        } else {
            BigDecimal delta  = amount.subtract(profile.getAmountMean());
            BigDecimal newMean = profile.getAmountMean()
                    .add(delta.divide(BigDecimal.valueOf(newCount), 8, RoundingMode.HALF_UP));
            BigDecimal delta2 = amount.subtract(newMean);
            BigDecimal newM2  = profile.getAmountM2().add(delta.multiply(delta2));
            profile.setAmountMean(newMean);
            profile.setAmountM2(newM2);
        }

        profile.setTransactionCount(newCount);
        profile.setTotalTransactionsAnalyzed(profile.getTotalTransactionsAnalyzed() + 1);

        // EWMA inter-arrival velocity baseline — skip first transaction (no prior arrival).
        // profile.lastUpdated reflects when the profile was last saved (previous transaction).
        // Clamp inter-arrival to [1min, 7d] to handle cold-start and long inactivity gaps.
        if (newCount >= 2 && profile.getLastUpdated() != null) {
            long seconds = Duration.between(profile.getLastUpdated(), event.initiatedAt()).toSeconds();
            seconds = Math.max(60, Math.min(seconds, 7L * 24 * 3600));
            double interArrivalHours = seconds / 3600.0;
            double alpha = 2.0 / (Math.min(newCount, 30) + 1.0);
            profile.setAvgTransactionsPerHour(
                profile.getAvgTransactionsPerHour() * (1 - alpha) + (1.0 / interArrivalHours) * alpha);
            profile.setAvgTransactionsPerDay(
                profile.getAvgTransactionsPerDay() * (1 - alpha) + (24.0 / interArrivalHours) * alpha);
        }

        // Incremental frequency update for transaction hour
        String hour = String.valueOf(event.initiatedAt().getHour());
        var hours = profile.getTypicalTransactionHours();
        hours.merge(hour, 1.0 / newCount,
                (existing, inc) -> ((existing * (newCount - 1)) + 1.0) / newCount);
        profile.setTypicalTransactionHours(hours);

        // Incremental frequency update for MCC
        String mcc = event.merchantCategory();
        if (mcc != null && !mcc.isBlank()) {
            var mccs = profile.getTypicalMerchantCategories();
            mccs.merge(mcc, 1.0 / newCount,
                    (existing, inc) -> ((existing * (newCount - 1)) + 1.0) / newCount);
            profile.setTypicalMerchantCategories(mccs);
        }

        // First-appearance counterparty policy (D19): add on first non-alerted transaction
        if (event.counterpartyAccountId() != null) {
            String counterpartyStr = event.counterpartyAccountId().toString();
            List<String> counterparties = profile.getTypicalCounterparties();
            if (!counterparties.contains(counterpartyStr)) {
                if (counterparties.size() >= MAX_COUNTERPARTIES) {
                    counterparties.remove(0); // LRU: evict oldest entry
                }
                counterparties.add(counterpartyStr);
                profile.setTypicalCounterparties(counterparties);
            }
        }

        repository.save(profile);
    }

    /**
     * Writes the latest risk score and derived tier to the profile.
     * Called after every evaluation regardless of alert status (TODOS.md Phase 5).
     */
    @Transactional
    public void saveRiskScore(CustomerRiskProfile profile, double score) {
        profile.setCurrentRiskScore(score);
        profile.setRiskTier(deriveRiskTier(score));
        repository.save(profile);
    }

    private String deriveRiskTier(double score) {
        if (score >= 0.8) return "CRITICAL";
        if (score >= 0.6) return "HIGH";
        if (score >= 0.3) return "MEDIUM";
        return "LOW";
    }
}
