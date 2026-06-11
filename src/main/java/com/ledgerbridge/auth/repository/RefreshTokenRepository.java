package com.ledgerbridge.auth.repository;

import com.ledgerbridge.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
    void revokeAllByUserId(UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.familyId = :familyId")
    void revokeAllByFamilyId(UUID familyId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    void deleteExpiredBefore(LocalDateTime cutoff);
}
