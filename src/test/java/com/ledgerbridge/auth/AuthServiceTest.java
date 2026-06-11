package com.ledgerbridge.auth;

import com.ledgerbridge.auth.dto.AuthResponse;
import com.ledgerbridge.auth.dto.LoginRequest;
import com.ledgerbridge.auth.dto.RegisterRequest;
import com.ledgerbridge.auth.model.RefreshToken;
import com.ledgerbridge.auth.model.Role;
import com.ledgerbridge.auth.model.User;
import com.ledgerbridge.auth.repository.RefreshTokenRepository;
import com.ledgerbridge.auth.repository.UserRepository;
import com.ledgerbridge.auth.service.AuthService;
import com.ledgerbridge.auth.service.JwtService;
import com.ledgerbridge.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, jwtService, passwordEncoder,
                900_000L, 604_800_000L
        );
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_returnsTokenPair() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = userWith(UUID.randomUUID(), "alice@example.com", Role.USER);
        when(userRepository.save(any())).thenReturn(saved);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(saved)).thenReturn("access-token");

        AuthResponse response = authService.register(new RegisterRequest(
                "alice@example.com", "password123", "Alice", "Smith"
        ));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(any());
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void register_emailConflict_throwsConflict() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "password123", "Alice", "Smith")
        ))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsTokenPair() {
        User user = userWith(UUID.randomUUID(), "bob@example.com", Role.USER);
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");

        AuthResponse response = authService.login(new LoginRequest("bob@example.com", "secret"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "pw")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = userWith(UUID.randomUUID(), "carol@example.com", Role.USER);
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("carol@example.com", "wrong")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_disabledUser_throwsUnauthorized() {
        User user = userWith(UUID.randomUUID(), "dave@example.com", Role.USER);
        user.setEnabled(false);
        when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("dave@example.com", "pw")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        UUID familyId = UUID.randomUUID();
        User user = userWith(UUID.randomUUID(), "alice@example.com", Role.USER);
        RefreshToken stored = tokenFor(user, familyId, false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");

        AuthResponse response = authService.refresh("any-raw-token");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(stored.isRevoked()).isTrue();

        // Verify new token saved with same familyId
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken newToken = captor.getAllValues().get(1);
        assertThat(newToken.getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void refresh_tokenNotFound_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        User user = userWith(UUID.randomUUID(), "alice@example.com", Role.USER);
        RefreshToken expired = tokenFor(user, UUID.randomUUID(), false, LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_replayAttack_revokesFamilyAndThrowsUnauthorized() {
        UUID familyId = UUID.randomUUID();
        User user = userWith(UUID.randomUUID(), "alice@example.com", Role.USER);
        RefreshToken revoked = tokenFor(user, familyId, true, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));
        doNothing().when(refreshTokenRepository).revokeAllByFamilyId(familyId);

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_knownToken_revokesIt() {
        User user = userWith(UUID.randomUUID(), "alice@example.com", Role.USER);
        RefreshToken token = tokenFor(user, UUID.randomUUID(), false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.logout("valid-raw-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_unknownToken_silentlyNoops() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("unknown-token"); // must not throw

        verify(refreshTokenRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User userWith(UUID id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        u.setEnabled(true);
        return u;
    }

    private RefreshToken tokenFor(User user, UUID familyId, boolean revoked, LocalDateTime expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setFamilyId(familyId);
        t.setTokenHash("some-hash");
        t.setRevoked(revoked);
        t.setExpiresAt(expiresAt);
        return t;
    }
}
