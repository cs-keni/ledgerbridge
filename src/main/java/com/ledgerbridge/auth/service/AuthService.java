package com.ledgerbridge.auth.service;

import com.ledgerbridge.auth.dto.AuthResponse;
import com.ledgerbridge.auth.dto.LoginRequest;
import com.ledgerbridge.auth.dto.RegisterRequest;
import com.ledgerbridge.auth.model.RefreshToken;
import com.ledgerbridge.auth.model.Role;
import com.ledgerbridge.auth.model.User;
import com.ledgerbridge.auth.repository.RefreshTokenRepository;
import com.ledgerbridge.auth.repository.UserRepository;
import com.ledgerbridge.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(Role.USER);
        user = userRepository.save(user);
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) {
            throw new AppException("Account is disabled", HttpStatus.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
        return issueTokenPair(user, UUID.randomUUID());
    }

    // noRollbackFor ensures the family revocation commits even when we throw on replay.
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse refresh(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.isRevoked()) {
            // Replay attack: a token that was already rotated has been presented again.
            // Someone has a copy of a stale token — revoke the entire family to protect the account.
            log.warn("Replay attack detected: family={} user={}", stored.getFamilyId(), stored.getUser().getId());
            refreshTokenRepository.revokeAllByFamilyId(stored.getFamilyId());
            throw new AppException("Session compromised. Please log in again.", HttpStatus.UNAUTHORIZED);
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException("Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokenPair(stored.getUser(), stored.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    private AuthResponse issueTokenPair(User user, UUID familyId) {
        String rawRefresh = generateRawRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawRefresh));
        token.setFamilyId(familyId);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000));
        refreshTokenRepository.save(token);

        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                accessToken,
                rawRefresh,
                "Bearer",
                accessTokenExpirationMs / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
