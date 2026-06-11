package com.ledgerbridge.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerbridge.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public String computeHash(Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to compute idempotency hash", e);
        }
    }

    @Transactional(readOnly = true)
    public <T> Optional<T> getCached(String key, UUID userId, String requestHash, Class<T> responseType) {
        return repository.findByIdemKeyAndUserId(key, userId).map(stored -> {
            if (!stored.getRequestHash().equals(requestHash)) {
                throw new AppException(
                        "Idempotency key already used with a different request body",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
            try {
                return objectMapper.readValue(stored.getResponseJson(), responseType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached response", e);
            }
        });
    }

    // REQUIRES_NEW: idempotency storage must commit independently of the caller.
    // A race between two identical concurrent requests may hit the unique constraint —
    // that's a silent no-op; the first request wins and both return the same response.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(String key, UUID userId, String requestHash, Object response, int statusCode) {
        try {
            IdempotencyKey ikey = new IdempotencyKey();
            ikey.setIdemKey(key);
            ikey.setUserId(userId);
            ikey.setRequestHash(requestHash);
            ikey.setResponseJson(objectMapper.writeValueAsString(response));
            ikey.setStatusCode(statusCode);
            ikey.setExpiresAt(LocalDateTime.now().plusHours(24));
            repository.save(ikey);
        } catch (Exception e) {
            log.warn("Could not store idempotency key={}: {}", key, e.getMessage());
        }
    }
}
