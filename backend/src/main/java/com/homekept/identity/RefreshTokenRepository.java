package com.homekept.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data repository for {@link RefreshToken}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes all non-revoked refresh tokens for a user (used on logout).
     *
     * <p>{@code flushAutomatically} flushes pending inserts before the bulk update so
     * freshly-created tokens are seen; {@code clearAutomatically} evicts the now-stale
     * managed entities afterward so subsequent reads in the same transaction reflect the
     * new {@code revokedAt} (a JPQL bulk update bypasses the persistence context).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Purge expired tokens to keep the table lean (called by a maintenance job).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
