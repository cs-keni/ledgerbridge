package com.ledgerbridge.account.service;

import com.ledgerbridge.account.dto.AccountResponse;
import com.ledgerbridge.account.dto.CreateAccountRequest;
import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setUserId(userId);
        account.setType(request.type());
        account.setCurrency(request.currency() != null ? request.currency() : "USD");
        try {
            return AccountResponse.from(accountRepository.save(account));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent threads generated the same number and both passed the
            // pre-check — retry once with a new number (probability ~10^-24 of repeat).
            account.setAccountNumber(generateAccountNumber());
            return AccountResponse.from(accountRepository.save(account));
        }
    }

    public List<AccountResponse> getAccountsByUser(UUID userId) {
        return accountRepository.findByUserIdAndStatus(userId, AccountStatus.ACTIVE)
                .stream().map(AccountResponse::from).toList();
    }

    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .map(AccountResponse::from)
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public AccountResponse closeAccount(UUID accountId, UUID userId) {
        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AppException("Account is already closed", HttpStatus.CONFLICT);
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        return AccountResponse.from(accountRepository.save(account));
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%012d", ThreadLocalRandom.current().nextLong(0, 1_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
