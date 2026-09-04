package com.homekept.subscription;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Subscriber}.
 */
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    /** Cursor-paginated list for the admin console (newest first). */
    List<Subscriber> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /** First page (no cursor) for the admin console (newest first). */
    List<Subscriber> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * All subscribers in the given status. Used by the admin dashboard aggregate to
     * compute the active-subscriber count and MRR sum in one query.
     */
    List<Subscriber> findByStatus(SubscriberStatus status);

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
