package com.homekept.subscription;

import com.homekept.catalog.PlanTier;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound Stripe API seam.
 *
 * <p>Interface so that tests can inject a mock and avoid hitting the live Stripe API.
 * The real implementation is {@link StripeServiceImpl}.
 *
 * <p>Every method that creates a Stripe resource includes an idempotency key.
 * Keys are deterministic hashes of the inputs so repeated calls return the same result.
 */
public interface StripeService {

    /**
     * Creates a Stripe Checkout Session for a subscription.
     *
     * <p>The session is configured with:
     * <ul>
     *   <li>{@code mode=subscription}</li>
     *   <li>The correct Stripe price id from the {@link PlanTier} (monthly / annual)</li>
     *   <li>Metadata: {@code subscriberId}, {@code planTierId}</li>
     *   <li>{@code client_reference_id} set to the subscriber id (string)</li>
     *   <li>Success and cancel redirect URLs from {@code app.stripe.*}</li>
     *   <li>The subscriber's existing Stripe customer id (if any) to avoid customer duplication</li>
     * </ul>
     *
     * <p>The {@code idempotencyKey} is supplied by the caller and must be deterministic
     * for the same operation (e.g. a hash of subscriberId + planCode + cycle).
     *
     * @param subscriber     the subscriber initiating checkout
     * @param plan           the plan tier to subscribe to
     * @param cycle          MONTHLY or ANNUAL
     * @param idempotencyKey deterministic idempotency key for this checkout attempt
     * @return the Stripe-hosted checkout URL to redirect the customer to
     * @throws com.stripe.exception.StripeException on Stripe API errors
     */
    String createCheckoutSession(Subscriber subscriber, PlanTier plan, BillingCycle cycle,
                                 String idempotencyKey);

    /**
     * Creates a Stripe Billing Portal session.
     *
     * <p>The portal lets the customer self-serve: change their plan, update their payment
     * method, or cancel their subscription. The return URL is configured via
     * {@code app.stripe.portal-return-url}.
     *
     * @param stripeCustomerId the Stripe customer id (cus_...)
     * @return the Stripe-hosted portal URL
     * @throws com.stripe.exception.StripeException on Stripe API errors
     */
    String createPortalSession(String stripeCustomerId);

    /**
     * Schedules cancellation at the end of the current billing period
     * (Stripe {@code cancel_at_period_end=true}). The customer keeps service through the
     * period they have already paid for; auto-renewal stops.
     *
     * <p>Stripe emits {@code customer.subscription.deleted} when the period ends, which the
     * webhook handler uses to transition the subscriber to CANCELLED. Local state is not
     * changed here. The churn reason is captured by the caller, not Stripe.
     *
     * @param stripeSubscriptionId the Stripe subscription id (sub_...)
     * @param idempotencyKey       deterministic key for this cancel attempt
     * @throws RuntimeException wrapping any {@link com.stripe.exception.StripeException}
     */
    void cancelSubscriptionAtPeriodEnd(String stripeSubscriptionId, String idempotencyKey);

    /**
     * Cancels a subscription immediately (Stripe {@code Subscription.cancel}, no proration
     * or refund params — Stripe's default behaviour applies).
     *
     * <p>Stripe emits {@code customer.subscription.deleted} right away, which the webhook
     * handler uses to transition the subscriber to CANCELLED. Local state is not changed
     * here — this is an admin-only action; the customer self-serve cancel only ever
     * schedules cancellation at period end ({@link #cancelSubscriptionAtPeriodEnd}).
     *
     * @param stripeSubscriptionId the Stripe subscription id (sub_...)
     * @param idempotencyKey       deterministic key for this cancel attempt
     * @throws RuntimeException wrapping any {@link com.stripe.exception.StripeException}
     */
    void cancelSubscriptionNow(String stripeSubscriptionId, String idempotencyKey);

    /**
     * Constructs and verifies a Stripe webhook event from the raw HTTP body and signature.
     *
     * <p>Delegates to {@code com.stripe.net.Webhook.constructEvent(...)} using the
     * webhook secret from {@code app.stripe.webhook-secret}.
     *
     * @param payload   raw request body (must be the unmodified bytes Stripe sent)
     * @param sigHeader value of the {@code Stripe-Signature} HTTP header
     * @return the verified {@link Event}
     * @throws SignatureVerificationException if the signature does not match — caller
     *                                        should return 400 to Stripe
     */
    Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException;

    /**
     * Lists a customer's most recent invoices (billing history), newest first.
     *
     * <p>Graceful degradation: returns an empty list — never throws — when
     * {@code app.stripe.secret-key} is blank (same pattern as
     * {@link com.homekept.notification.SendGridEmailSender} /
     * {@link com.homekept.storage.R2StorageService}). Callers are responsible for skipping
     * this call entirely when the subscriber has no Stripe customer id yet.
     *
     * @param stripeCustomerId the Stripe customer id (cus_...)
     * @param limit            max number of invoices to return
     * @return invoice summaries, newest first (possibly empty, never {@code null})
     * @throws RuntimeException wrapping any {@link com.stripe.exception.StripeException}
     */
    List<StripeInvoiceSummary> listInvoices(String stripeCustomerId, int limit);

    /**
     * Finds a customer's default payment method (the card Stripe will charge next),
     * resolved from {@code customer.invoice_settings.default_payment_method}.
     *
     * <p>Graceful degradation: returns {@link Optional#empty()} — never throws — when
     * {@code app.stripe.secret-key} is blank, or when the customer has no default payment
     * method set, or when the default payment method is not a card. Callers are responsible
     * for skipping this call entirely when the subscriber has no Stripe customer id yet.
     *
     * @param stripeCustomerId the Stripe customer id (cus_...)
     * @return the default card summary, or empty
     * @throws RuntimeException wrapping any {@link com.stripe.exception.StripeException}
     */
    Optional<StripePaymentMethodSummary> findDefaultPaymentMethod(String stripeCustomerId);

    /** A thin projection of a Stripe {@code Invoice} for the customer billing history view. */
    record StripeInvoiceSummary(
            String id,
            String number,
            Instant createdAt,
            int amountPaidCents,
            String currency,
            String status,
            String hostedInvoiceUrl,
            String invoicePdf
    ) {}

    /** A thin, PII-minimal projection of a Stripe {@code PaymentMethod}'s card details. */
    record StripePaymentMethodSummary(
            String brand,
            String last4,
            int expMonth,
            int expYear
    ) {}
}
