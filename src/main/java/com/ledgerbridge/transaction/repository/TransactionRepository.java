package com.ledgerbridge.transaction.repository;

import com.ledgerbridge.transaction.model.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Page<LedgerTransaction> findByAccountIdOrderByInitiatedAtDesc(UUID accountId, Pageable pageable);

    // VelocityRule: single conditional-aggregation query for all three windows (decision D17)
    @Query("""
        SELECT
            COUNT(CASE WHEN t.initiatedAt >= :oneHourAgo  THEN 1 END) AS lastHour,
            COUNT(CASE WHEN t.initiatedAt >= :oneDayAgo   THEN 1 END) AS lastDay,
            COUNT(CASE WHEN t.initiatedAt >= :sevenDaysAgo THEN 1 END) AS lastWeek
        FROM LedgerTransaction t
        WHERE t.accountId = :accountId
          AND t.initiatedAt >= :sevenDaysAgo
        """)
    VelocityWindowCounts countVelocityWindows(
            UUID accountId,
            LocalDateTime oneHourAgo,
            LocalDateTime oneDayAgo,
            LocalDateTime sevenDaysAgo);

    // GraphPatternRule: distinct new counterparties in the last 24h not in the known set
    @Query("""
        SELECT COUNT(DISTINCT t.counterpartyAccountId)
        FROM LedgerTransaction t
        WHERE t.accountId = :accountId
          AND t.initiatedAt >= :since
          AND t.counterpartyAccountId IS NOT NULL
          AND CAST(t.counterpartyAccountId AS string) NOT IN :knownCounterparties
        """)
    long countDistinctNewCounterpartiesSince(UUID accountId, LocalDateTime since,
                                              java.util.List<String> knownCounterparties);

    // GraphPatternRule: round-trip detection — same amount sent and returned within window
    @Query("""
        SELECT COUNT(t) > 0
        FROM LedgerTransaction t
        WHERE t.counterpartyAccountId = :senderAccountId
          AND t.accountId = :receiverAccountId
          AND t.amount = :amount
          AND t.initiatedAt >= :since
          AND t.status = 'COMPLETED'
        """)
    boolean existsRoundTrip(UUID senderAccountId, UUID receiverAccountId,
                             java.math.BigDecimal amount, LocalDateTime since);

    interface VelocityWindowCounts {
        long getLastHour();
        long getLastDay();
        long getLastWeek();
    }
}
