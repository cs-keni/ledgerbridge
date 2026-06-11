package com.ledgerbridge.account.controller;

import com.ledgerbridge.account.dto.AccountResponse;
import com.ledgerbridge.account.dto.CreateAccountRequest;
import com.ledgerbridge.account.service.AccountService;
import com.ledgerbridge.auth.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new account")
    public AccountResponse createAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        return accountService.createAccount(principal.user().getId(), request);
    }

    @GetMapping
    @Operation(summary = "List all active accounts for the authenticated user")
    public List<AccountResponse> listAccounts(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.getAccountsByUser(principal.user().getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific account by ID")
    public AccountResponse getAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return accountService.getAccount(id, principal.user().getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Close an account")
    public AccountResponse closeAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return accountService.closeAccount(id, principal.user().getId());
    }
}
