package com.homekept.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data repository for {@link PasswordResetToken}.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Finds a token by its SHA-256 hash (the value stored in the DB). */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

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
     * Invalidates every other outstanding (unconsumed) reset token belonging to a user, by
     * marking them consumed. Called after a successful reset so an earlier, still-unexpired
     * reset link can't be replayed once the password has already been changed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now WHERE t.user.id = :userId AND t.consumedAt IS NULL")
    int consumeAllUnconsumedForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
