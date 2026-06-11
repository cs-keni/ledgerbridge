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

    // VelocityRule: native SQL to avoid Hibernate's LocalDateTime→UTC conversion when
    // hibernate.jdbc.time_zone=UTC is active (JPQL parameters would be shifted by JVM offset).
    @Query(value = """
        SELECT
            COUNT(CASE WHEN initiated_at >= :oneHourAgo   THEN 1 END) AS lasthour,
            COUNT(CASE WHEN initiated_at >= :oneDayAgo    THEN 1 END) AS lastday,
            COUNT(CASE WHEN initiated_at >= :sevenDaysAgo THEN 1 END) AS lastweek
        FROM ledger_transaction
        WHERE account_id = :accountId
          AND initiated_at >= :sevenDaysAgo
        """, nativeQuery = true)
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

    // GraphPatternRule: native SQL to avoid Hibernate's LocalDateTime→UTC conversion
    // on the 2-hour window (same timezone mismatch issue as countVelocityWindows).
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM ledger_transaction
            WHERE counterparty_account_id = :senderAccountId
              AND account_id             = :receiverAccountId
              AND amount                 = :amount
              AND initiated_at           >= :since
              AND status                 = 'COMPLETED'
        )
        """, nativeQuery = true)
    boolean existsRoundTrip(UUID senderAccountId, UUID receiverAccountId,
                             java.math.BigDecimal amount, LocalDateTime since);

    interface VelocityWindowCounts {
        long getLastHour();
        long getLastDay();
        long getLastWeek();
    }
}
