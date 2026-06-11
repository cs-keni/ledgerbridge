package com.ledgerbridge.account;

import com.ledgerbridge.account.dto.AccountResponse;
import com.ledgerbridge.account.dto.CreateAccountRequest;
import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.model.AccountStatus;
import com.ledgerbridge.account.model.AccountType;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.account.service.AccountService;
import com.ledgerbridge.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @InjectMocks AccountService accountService;

    // ── createAccount ─────────────────────────────────────────────────────────

    @Test
    void createAccount_success_returnsResponse() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setBalance(BigDecimal.ZERO);
            a.setStatus(AccountStatus.ACTIVE);
            // simulate @PrePersist
            try { var f = Account.class.getDeclaredField("openedAt"); f.setAccessible(true); f.set(a, LocalDateTime.now()); }
            catch (Exception ignored) {}
            return a;
        });

        AccountResponse response = accountService.createAccount(userId, new CreateAccountRequest(AccountType.CHECKING, null));

        assertThat(response.type()).isEqualTo(AccountType.CHECKING);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).save(any());
    }

    @Test
    void createAccount_customCurrency_usesProvidedCurrency() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AccountResponse response = accountService.createAccount(userId, new CreateAccountRequest(AccountType.SAVINGS, "EUR"));

        assertThat(response.currency()).isEqualTo("EUR");
    }

    // ── getAccountsByUser ─────────────────────────────────────────────────────

    @Test
    void getAccountsByUser_returnsMappedList() {
        UUID userId = UUID.randomUUID();
        Account a1 = accountWith(UUID.randomUUID(), userId, AccountType.CHECKING);
        Account a2 = accountWith(UUID.randomUUID(), userId, AccountType.SAVINGS);
        when(accountRepository.findByUserIdAndStatus(userId, AccountStatus.ACTIVE)).thenReturn(List.of(a1, a2));

        List<AccountResponse> result = accountService.getAccountsByUser(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountResponse::type)
                .containsExactlyInAnyOrder(AccountType.CHECKING, AccountType.SAVINGS);
    }

    // ── getAccount ────────────────────────────────────────────────────────────

    @Test
    void getAccount_ownedByUser_returnsResponse() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = accountWith(accountId, userId, AccountType.CHECKING);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount(accountId, userId);

        assertThat(response.id()).isEqualTo(accountId);
    }

    @Test
    void getAccount_notFound_throwsNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAccount_wrongOwner_throwsNotFound() {
        // Ownership failure must look identical to "not found" — don't reveal existence
        UUID accountId = UUID.randomUUID();
        Account account = accountWith(accountId, UUID.randomUUID(), AccountType.CHECKING);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.getAccount(accountId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── closeAccount ──────────────────────────────────────────────────────────

    @Test
    void closeAccount_success_setsClosedStatus() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = accountWith(accountId, userId, AccountType.CHECKING);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.closeAccount(accountId, userId);

        assertThat(response.status()).isEqualTo(AccountStatus.CLOSED);
        assertThat(response.closedAt()).isNotNull();
    }

    @Test
    void closeAccount_alreadyClosed_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = accountWith(accountId, userId, AccountType.CHECKING);
        account.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void closeAccount_wrongOwner_throwsNotFound() {
        UUID accountId = UUID.randomUUID();
        Account account = accountWith(accountId, UUID.randomUUID(), AccountType.CHECKING);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.closeAccount(accountId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account accountWith(UUID id, UUID userId, AccountType type) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(userId);
        a.setAccountNumber("000000000001");
        a.setType(type);
        a.setStatus(AccountStatus.ACTIVE);
        a.setBalance(BigDecimal.ZERO);
        a.setCurrency("USD");
        return a;
    }
}
