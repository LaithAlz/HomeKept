package com.homekept.subscription;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Subscriber}.
 */
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    /** Count founding subscribers — used by the founding-rate availability check. */
    long countByFoundingRateTrue();

    /**
     * Acquires a Postgres transaction-scoped advisory lock on a fixed key (42) that
     * serialises concurrent founding-rate grants inside the webhook handler.
     *
     * <p>The lock is held until the current transaction commits or rolls back.
     * Callers MUST be inside an active {@code @Transactional} context.
     * Two transactions that both call this method will execute serially — the second
     * blocks until the first releases the lock on commit.
     *
     * <p>Key 42 is the founding-rate critical-section identifier. It has no external
     * significance — it just needs to be the same constant value across all callers
     * that want to serialize founding grants.
     */
    @Modifying
    @Query(value = "SELECT pg_advisory_xact_lock(42)", nativeQuery = true)
    void lockFoundingCounter();

    /** Cursor-paginated list for the admin console (newest first). */
    List<Subscriber> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /** First page (no cursor) for the admin console (newest first). */
    List<Subscriber> findAllByOrderByIdDesc(Pageable pageable);

    /** Find by user id — each user has at most one active subscriber at a time. */
    Optional<Subscriber> findByUserId(Long userId);

    /**
     * Find by Stripe subscription id.
     * Used by webhook handlers that receive a {@code customer.subscription.*} event
     * (which carries the Stripe subscription id, not the HomeKept subscriber id).
     */
    Optional<Subscriber> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Find by Stripe customer id.
     * Fallback lookup for webhook events that only carry a Stripe customer id.
     */
    Optional<Subscriber> findByStripeCustomerId(String stripeCustomerId);
}
