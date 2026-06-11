package com.ledgerbridge.account.repository;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);
    Optional<Account> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);
}
