package com.ledgerbridge.risk.service;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.repository.CustomerRiskProfileRepository;
import com.ledgerbridge.transaction.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            CustomerRiskProfile profile = new CustomerRiskProfile();
            profile.setUserId(userId);
            return repository.save(profile);
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

        // Incremental frequency update for transaction hour
        String hour = String.valueOf(event.initiatedAt().getHour());
        var hours = profile.getTypicalTransactionHours();
        hours.merge(hour, 1.0,
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
}
