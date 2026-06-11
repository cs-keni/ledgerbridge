package com.ledgerbridge.transaction.service;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.common.exception.AppException;
import com.ledgerbridge.transaction.dto.TransactionRequest;
import com.ledgerbridge.transaction.dto.TransactionResponse;
import com.ledgerbridge.transaction.dto.TransferRequest;
import com.ledgerbridge.transaction.event.TransactionCompletedEvent;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.LedgerTransaction;
import com.ledgerbridge.transaction.model.TransactionStatus;
import com.ledgerbridge.transaction.model.TransactionType;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TransactionResponse deposit(UUID userId, TransactionRequest request, String correlationId) {
        Account account = requireOwnedActiveAccount(request.accountId(), userId);
        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);

        LedgerTransaction tx = buildTx(account.getId(), null, TransactionType.DEPOSIT,
                request.amount(), account.getCurrency(), request.merchantCategory(),
                request.description(), correlationId);
        tx = transactionRepository.save(tx);
        publishEvent(tx, account.getUserId());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(UUID userId, TransactionRequest request, String correlationId) {
        Account account = requireOwnedActiveAccount(request.accountId(), userId);
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new AppException("Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        account.setBalance(account.getBalance().subtract(request.amount()));
        accountRepository.save(account);

        LedgerTransaction tx = buildTx(account.getId(), null, TransactionType.WITHDRAWAL,
                request.amount(), account.getCurrency(), request.merchantCategory(),
                request.description(), correlationId);
        tx = transactionRepository.save(tx);
        publishEvent(tx, account.getUserId());
        return TransactionResponse.from(tx);
    }

    // Locks accounts in ascending UUID order to prevent deadlocks (decision D13).
    @Transactional
    public TransactionResponse transfer(UUID userId, TransferRequest request, String correlationId) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new AppException("Cannot transfer to the same account", HttpStatus.BAD_REQUEST);
        }

        UUID firstId = request.fromAccountId().compareTo(request.toAccountId()) < 0
                ? request.fromAccountId() : request.toAccountId();
        UUID secondId = request.fromAccountId().compareTo(request.toAccountId()) < 0
                ? request.toAccountId() : request.fromAccountId();

        Account first = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));
        Account second = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));

        Account fromAccount = first.getId().equals(request.fromAccountId()) ? first : second;
        Account toAccount = first.getId().equals(request.fromAccountId()) ? second : first;

        if (!fromAccount.getUserId().equals(userId)) {
            throw new AppException("Account not found", HttpStatus.NOT_FOUND);
        }
        requireActiveStatus(fromAccount);
        requireActiveStatus(toAccount);

        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new AppException("Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.amount()));
        toAccount.setBalance(toAccount.getBalance().add(request.amount()));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        LedgerTransaction debit = buildTx(fromAccount.getId(), toAccount.getId(),
                TransactionType.TRANSFER_DEBIT, request.amount(), fromAccount.getCurrency(),
                null, request.description(), correlationId);
        LedgerTransaction credit = buildTx(toAccount.getId(), fromAccount.getId(),
                TransactionType.TRANSFER_CREDIT, request.amount(), toAccount.getCurrency(),
                null, request.description(), correlationId);
        debit = transactionRepository.save(debit);
        credit = transactionRepository.save(credit);

        publishEvent(debit, fromAccount.getUserId());
        publishEvent(credit, toAccount.getUserId());

        return TransactionResponse.from(debit);
    }

    public TransactionResponse getTransaction(UUID txId, UUID userId) {
        LedgerTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new AppException("Transaction not found", HttpStatus.NOT_FOUND));
        // Ownership: verify the account belongs to the requesting user
        accountRepository.findById(tx.getAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new AppException("Transaction not found", HttpStatus.NOT_FOUND));
        return TransactionResponse.from(tx);
    }

    public Page<TransactionResponse> getTransactionsByAccount(UUID accountId, UUID userId, Pageable pageable) {
        accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));
        return transactionRepository
                .findByAccountIdOrderByInitiatedAtDesc(accountId, pageable)
                .map(TransactionResponse::from);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Account requireOwnedActiveAccount(UUID accountId, UUID userId) {
        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new AppException("Account not found", HttpStatus.NOT_FOUND));
        requireActiveStatus(account);
        return account;
    }

    private void requireActiveStatus(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException("Account is not active", HttpStatus.CONFLICT);
        }
    }

    private LedgerTransaction buildTx(UUID accountId, UUID counterpartyId, TransactionType type,
                                       java.math.BigDecimal amount, String currency,
                                       String merchantCategory, String description,
                                       String correlationId) {
        LedgerTransaction tx = new LedgerTransaction();
        tx.setTransactionNumber(generateTxNumber());
        tx.setAccountId(accountId);
        tx.setCounterpartyAccountId(counterpartyId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setMerchantCategory(merchantCategory);
        tx.setDescription(description);
        tx.setCorrelationId(correlationId);
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now());
        return tx;
    }

    private void publishEvent(LedgerTransaction tx, UUID userId) {
        eventPublisher.publishEvent(new TransactionCompletedEvent(new TransactionEvent(
                tx.getId(), tx.getAccountId(), userId,
                tx.getCounterpartyAccountId(), tx.getType(), tx.getAmount(),
                tx.getCurrency(), tx.getMerchantCategory(), tx.getCorrelationId(),
                tx.getInitiatedAt()
        )));
    }

    private String generateTxNumber() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
