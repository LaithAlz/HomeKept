package com.homekept.subscription;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link SubscriptionEvent}.
 *
 * <p>Used for idempotency lookups (by Stripe event id), persisting new events, and the
 * admin subscriber activity history ({@code GET /api/admin/subscribers/{id}/events}). All
 * other reads go through the Stripe API or the subscriber entity directly.
 */
interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    /**
     * Find an existing event row by its Stripe event id.
     * Used by the webhook handler to short-circuit duplicate deliveries before processing.
     *
     * @param stripeEventId the Stripe event id (e.g. {@code evt_1Abc...})
     * @return the existing row, or empty if this event has not been processed yet
     */
    Optional<SubscriptionEvent> findByStripeEventId(String stripeEventId);

    /**
     * Newest-first activity history for a single subscriber, for the admin console.
     *
     * @param subscriberId the subscriber id
     * @param pageable     page size cap (the controller caps this at 100)
     * @return events ordered by {@code createdAt} descending
     */
    List<SubscriptionEvent> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId, Pageable pageable);

    /**
     * The single most recent event for a subscriber (ties broken by id, since
     * {@code createdAt} timestamps can collide within the same millisecond in tests/rapid
     * double-clicks). Used by the cancel duplicate-request guard — see
     * {@code SubscriptionSelfServeService#cancelSubscriber}.
     *
     * @param subscriberId the subscriber id
     * @return the newest event row, or empty if the subscriber has none yet
     */
    boolean existsBySubscriberIdAndEventType(Long subscriberId, String eventType);
}
