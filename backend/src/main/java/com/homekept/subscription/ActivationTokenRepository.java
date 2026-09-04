package com.homekept.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link ActivationToken}.
 */
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    /** Finds a token by its SHA-256 hash (the value stored in the DB). */
    Optional<ActivationToken> findByTokenHash(String tokenHash);

    /**
     * Returns, for each requested booking id that has at least one activation token, the
     * booking id and the {@code created_at} of its most recently minted token — one row per
     * booking id, resolved in a single grouped query (no N+1 when batching across a page of
     * bookings).
     */
    @Query("SELECT t.bookingId AS bookingId, MAX(t.createdAt) AS latestCreatedAt "
            + "FROM ActivationToken t WHERE t.bookingId IN :bookingIds GROUP BY t.bookingId")
    List<LatestInviteAt> findLatestCreatedAtByBookingIdIn(@Param("bookingIds") Collection<Long> bookingIds);

    /** Projection for {@link #findLatestCreatedAtByBookingIdIn}. */
    interface LatestInviteAt {
        Long getBookingId();
        Instant getLatestCreatedAt();
    }

    /**
     * Atomically consumes the token: sets {@code consumed_at} only if it is still null.
     * Returns the number of rows updated — exactly 1 for the winner of a concurrent race,
     * 0 for any caller that arrives after the token is already consumed. This makes
     * single-use a DB-enforced invariant (no TOCTOU between a read-check and the write).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationToken t SET t.consumedAt = :now WHERE t.tokenHash = :hash AND t.consumedAt IS NULL")
    int consumeIfUnconsumed(@Param("hash") String hash, @Param("now") Instant now);
}
