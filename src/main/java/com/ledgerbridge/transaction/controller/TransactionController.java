package com.ledgerbridge.transaction.controller;

import com.ledgerbridge.auth.model.UserPrincipal;
import com.ledgerbridge.common.idempotency.IdempotencyService;
import com.ledgerbridge.transaction.dto.TransactionRequest;
import com.ledgerbridge.transaction.dto.TransactionResponse;
import com.ledgerbridge.transaction.dto.TransferRequest;
import com.ledgerbridge.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit money into an account")
    public TransactionResponse deposit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Client-generated UUID. Retries within 24h return the cached response.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        String requestHash = idempotencyKey != null ? idempotencyService.computeHash(request) : null;
        if (idempotencyKey != null) {
            Optional<TransactionResponse> cached = idempotencyService.getCached(
                    idempotencyKey, principal.user().getId(), requestHash, TransactionResponse.class);
            if (cached.isPresent()) return cached.get();
        }
        TransactionResponse response = transactionService.deposit(
                principal.user().getId(), request, MDC.get("correlationId"));
        if (idempotencyKey != null) {
            idempotencyService.store(idempotencyKey, principal.user().getId(), requestHash, response, 201);
        }
        return response;
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Withdraw money from an account")
    public TransactionResponse withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request
    ) {
        String requestHash = idempotencyKey != null ? idempotencyService.computeHash(request) : null;
        if (idempotencyKey != null) {
            Optional<TransactionResponse> cached = idempotencyService.getCached(
                    idempotencyKey, principal.user().getId(), requestHash, TransactionResponse.class);
            if (cached.isPresent()) return cached.get();
        }
        TransactionResponse response = transactionService.withdraw(
                principal.user().getId(), request, MDC.get("correlationId"));
        if (idempotencyKey != null) {
            idempotencyService.store(idempotencyKey, principal.user().getId(), requestHash, response, 201);
        }
        return response;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer money between two accounts")
    public TransactionResponse transfer(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        String requestHash = idempotencyKey != null ? idempotencyService.computeHash(request) : null;
        if (idempotencyKey != null) {
            Optional<TransactionResponse> cached = idempotencyService.getCached(
                    idempotencyKey, principal.user().getId(), requestHash, TransactionResponse.class);
            if (cached.isPresent()) return cached.get();
        }
        TransactionResponse response = transactionService.transfer(
                principal.user().getId(), request, MDC.get("correlationId"));
        if (idempotencyKey != null) {
            idempotencyService.store(idempotencyKey, principal.user().getId(), requestHash, response, 201);
        }
        return response;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transaction by ID")
    public TransactionResponse getTransaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return transactionService.getTransaction(id, principal.user().getId());
    }

    @GetMapping
    @Operation(summary = "List transactions for an account (paged, newest first)")
    public Page<TransactionResponse> listTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID accountId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return transactionService.getTransactionsByAccount(accountId, principal.user().getId(), pageable);
    }
}
