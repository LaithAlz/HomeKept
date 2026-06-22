package com.homekept.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link SubscriptionEvent}.
 *
 * <p>Only used for idempotency lookups (by Stripe event id) and for persisting new
 * events. All other reads go through the Stripe API or the subscriber entity directly.
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
}
