package com.ledgerbridge.transaction;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.model.AccountType;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.common.exception.AppException;
import com.ledgerbridge.transaction.dto.TransactionRequest;
import com.ledgerbridge.transaction.dto.TransactionResponse;
import com.ledgerbridge.transaction.dto.TransferRequest;
import com.ledgerbridge.transaction.event.TransactionCompletedEvent;
import com.ledgerbridge.transaction.model.TransactionType;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import com.ledgerbridge.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks TransactionService service;

    private static final UUID USER_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID   = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TO_ACCT_ID   = UUID.fromString("20000000-0000-0000-0000-000000000002");

    // ── deposit ──────────────────────────────────────────────────────────────

    @Test
    void deposit_success() {
        Account acct = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("200.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse result = service.deposit(USER_ID,
                new TransactionRequest(ACCOUNT_ID, new BigDecimal("50.00"), "FOOD", "Lunch"), null);

        assertThat(acct.getBalance()).isEqualByComparingTo("250.00");
        assertThat(result.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.amount()).isEqualByComparingTo("50.00");
        verify(eventPublisher).publishEvent(any(TransactionCompletedEvent.class));
    }

    @Test
    void deposit_accountNotFound_throws404() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.deposit(USER_ID, new TransactionRequest(ACCOUNT_ID, BigDecimal.ONE, null, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deposit_wrongOwner_throws404() {
        Account acct = activeAccount(ACCOUNT_ID, OTHER_USER, BigDecimal.TEN);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));

        assertThatThrownBy(() ->
                service.deposit(USER_ID, new TransactionRequest(ACCOUNT_ID, BigDecimal.ONE, null, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deposit_closedAccount_throws409() {
        Account acct = activeAccount(ACCOUNT_ID, USER_ID, BigDecimal.TEN);
        acct.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));

        assertThatThrownBy(() ->
                service.deposit(USER_ID, new TransactionRequest(ACCOUNT_ID, BigDecimal.ONE, null, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not active");
    }

    // ── withdraw ─────────────────────────────────────────────────────────────

    @Test
    void withdraw_success() {
        Account acct = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("300.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse result = service.withdraw(USER_ID,
                new TransactionRequest(ACCOUNT_ID, new BigDecimal("100.00"), null, null), "corr-123");

        assertThat(acct.getBalance()).isEqualByComparingTo("200.00");
        assertThat(result.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.correlationId()).isEqualTo("corr-123");
        verify(eventPublisher).publishEvent(any(TransactionCompletedEvent.class));
    }

    @Test
    void withdraw_insufficientFunds_throws422() {
        Account acct = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("10.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));

        assertThatThrownBy(() ->
                service.withdraw(USER_ID,
                        new TransactionRequest(ACCOUNT_ID, new BigDecimal("100.00"), null, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void withdraw_accountNotFound_throws404() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.withdraw(USER_ID, new TransactionRequest(ACCOUNT_ID, BigDecimal.ONE, null, null), null))
                .isInstanceOf(AppException.class);
    }

    // ── transfer ─────────────────────────────────────────────────────────────

    @Test
    void transfer_success() {
        // ACCOUNT_ID (10...) < TO_ACCT_ID (20...) so lock order is ACCOUNT_ID first
        Account from = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("500.00"));
        Account to   = activeAccount(TO_ACCT_ID, OTHER_USER, new BigDecimal("100.00"));
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdWithLock(TO_ACCT_ID)).thenReturn(Optional.of(to));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse result = service.transfer(USER_ID,
                new TransferRequest(ACCOUNT_ID, TO_ACCT_ID, new BigDecimal("200.00"), "Rent"), null);

        assertThat(from.getBalance()).isEqualByComparingTo("300.00");
        assertThat(to.getBalance()).isEqualByComparingTo("300.00");
        assertThat(result.type()).isEqualTo(TransactionType.TRANSFER_DEBIT);
    }

    @Test
    void transfer_sameAccount_throws400() {
        assertThatThrownBy(() ->
                service.transfer(USER_ID,
                        new TransferRequest(ACCOUNT_ID, ACCOUNT_ID, BigDecimal.ONE, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void transfer_fromAccountNotOwned_throws404() {
        Account from = activeAccount(ACCOUNT_ID, OTHER_USER, new BigDecimal("500.00"));
        Account to   = activeAccount(TO_ACCT_ID, USER_ID, BigDecimal.TEN);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdWithLock(TO_ACCT_ID)).thenReturn(Optional.of(to));

        assertThatThrownBy(() ->
                service.transfer(USER_ID,
                        new TransferRequest(ACCOUNT_ID, TO_ACCT_ID, BigDecimal.ONE, null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void transfer_insufficientFunds_throws422() {
        Account from = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("5.00"));
        Account to   = activeAccount(TO_ACCT_ID, OTHER_USER, BigDecimal.ZERO);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdWithLock(TO_ACCT_ID)).thenReturn(Optional.of(to));

        assertThatThrownBy(() ->
                service.transfer(USER_ID,
                        new TransferRequest(ACCOUNT_ID, TO_ACCT_ID, new BigDecimal("100.00"), null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void transfer_destinationClosed_throws409() {
        Account from = activeAccount(ACCOUNT_ID, USER_ID, new BigDecimal("500.00"));
        Account to   = activeAccount(TO_ACCT_ID, OTHER_USER, BigDecimal.ZERO);
        to.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdWithLock(TO_ACCT_ID)).thenReturn(Optional.of(to));

        assertThatThrownBy(() ->
                service.transfer(USER_ID,
                        new TransferRequest(ACCOUNT_ID, TO_ACCT_ID, new BigDecimal("10.00"), null), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not active");
    }

    // ── getTransaction ───────────────────────────────────────────────────────

    @Test
    void getTransaction_notFound_throws404() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(txId, USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── getTransactionsByAccount ─────────────────────────────────────────────

    @Test
    void getTransactionsByAccount_accountNotOwned_throws404() {
        Account acct = activeAccount(ACCOUNT_ID, OTHER_USER, BigDecimal.ZERO);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));

        assertThatThrownBy(() ->
                service.getTransactionsByAccount(ACCOUNT_ID, USER_ID, Pageable.unpaged()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getTransactionsByAccount_success() {
        Account acct = activeAccount(ACCOUNT_ID, USER_ID, BigDecimal.ZERO);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct));
        when(transactionRepository.findByAccountIdOrderByInitiatedAtDesc(ACCOUNT_ID, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));

        var page = service.getTransactionsByAccount(ACCOUNT_ID, USER_ID, Pageable.unpaged());

        assertThat(page).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account activeAccount(UUID id, UUID userId, BigDecimal balance) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(userId);
        a.setBalance(balance);
        a.setStatus(AccountStatus.ACTIVE);
        a.setType(AccountType.CHECKING);
        a.setCurrency("USD");
        return a;
    }
}
