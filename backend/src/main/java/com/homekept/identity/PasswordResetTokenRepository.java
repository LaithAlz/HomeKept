package com.homekept.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link PasswordResetToken}.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a token by its SHA-256 hash (the value stored in the DB), ignoring purpose.
     * Production code must use {@link #findByTokenHashAndPurpose} instead — this purpose-blind
     * lookup exists only for tests that assert on hash-only storage.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Finds a token by hash AND purpose. A hash that exists but under a different purpose is
     * treated exactly like a nonexistent hash (empty) — this is what makes a wrong-purpose
     * token indistinguishable from an invalid one in {@code PasswordResetTokenService.validate}.
     */
    Optional<PasswordResetToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /**
     * Atomically consumes the token: sets {@code consumed_at} only if it is still null.
     * Returns the number of rows updated — exactly 1 for the winner of a concurrent race,
     * 0 for any caller that arrives after the token is already consumed. This makes
     * single-use a DB-enforced invariant (no TOCTOU between a read-check and the write).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now WHERE t.tokenHash = :hash AND t.consumedAt IS NULL")
    int consumeIfUnconsumed(@Param("hash") String hash, @Param("now") Instant now);

    /**
     * Invalidates every other outstanding (unconsumed) token of the given purpose belonging
     * to a user, by marking them consumed. Scoped to one purpose so, for example, resending a
     * staff invite never touches a password-reset token the same person separately requested
     * (and vice versa) — see call sites in {@code PasswordResetTokenService}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now "
            + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.consumedAt IS NULL")
    int consumeAllUnconsumedForUserAndPurpose(@Param("userId") Long userId,
            @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);

    /**
     * Returns, for each requested user id that has at least one token row of the given
     * purpose, the user id and the {@code created_at} of its most recently minted token —
     * one row per user id, resolved in a single grouped query (no N+1 when batching across
     * the technician roster). Used by the technician admin roster's {@code invitedAt} column
     * (scoped to {@link TokenPurpose#STAFF_INVITE} — a technician's own password reset must
     * never show up there); see {@code PasswordResetTokenService#latestInviteAtByUserIds}.
     */
    @Query("SELECT t.user.id AS userId, MAX(t.createdAt) AS latestCreatedAt "
            + "FROM PasswordResetToken t WHERE t.user.id IN :userIds AND t.purpose = :purpose "
            + "GROUP BY t.user.id")
    List<LatestInviteAt> findLatestCreatedAtByUserIdInAndPurpose(@Param("userIds") Collection<Long> userIds,
            @Param("purpose") TokenPurpose purpose);

    /** Projection for {@link #findLatestCreatedAtByUserIdInAndPurpose}. */
    interface LatestInviteAt {
        Long getUserId();
        Instant getLatestCreatedAt();
    }
}
