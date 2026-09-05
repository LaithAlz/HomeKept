package com.homekept.subscription;

import com.homekept.catalog.PlanTier;
import com.homekept.common.Hashing;
import com.homekept.config.AppProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerRetrieveParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.common.EmptyParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Real Stripe API implementation of {@link StripeService}.
 *
 * <p>All Stripe writes include a deterministic idempotency key so repeated calls
 * (e.g. from a double-click or network retry) produce the same result on Stripe's side.
 *
 * <p>No money arithmetic in this class — price selection is done by the caller, which
 * picks the correct Stripe price id from the {@link PlanTier} entity. This class only
 * passes through what it is given.
 *
 * <p>Never log the secret key or any cardholder data. The key is set once in
 * {@link com.homekept.config.StripeConfig} and never read back here.
 */
@Service
public class StripeServiceImpl implements StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeServiceImpl.class);

    private final AppProperties appProperties;

    public StripeServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public String createCheckoutSession(Subscriber subscriber, PlanTier plan, BillingCycle cycle,
                                        String idempotencyKey) {
        String priceId = resolvePriceId(plan, cycle);
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalStateException(
                    "PlanTier " + plan.getCode() + " has no Stripe price id for cycle=" + cycle
                            + ". Set it in the DB before checkout.");
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(String.valueOf(subscriber.getId()))
                .putMetadata("subscriberId", String.valueOf(subscriber.getId()))
                .putMetadata("planTierId", String.valueOf(plan.getId()))
                // Chosen billing cycle, echoed back on checkout.session.completed so the
                // activation analytics event reports the billed cycle (the subscriber row
                // still holds its default until the later customer.subscription.updated sync).
                .putMetadata("billingCycle", cycle.name())
                .setSuccessUrl(appProperties.stripe().successUrl())
                .setCancelUrl(appProperties.stripe().cancelUrl())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                );

        // Reuse an existing Stripe customer if this subscriber already has one.
        if (subscriber.getStripeCustomerId() != null && !subscriber.getStripeCustomerId().isBlank()) {
            params.setCustomer(subscriber.getStripeCustomerId());
        }

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            com.stripe.model.checkout.Session session =
                    com.stripe.model.checkout.Session.create(params.build(), options);
            log.info("Stripe checkout session created subscriberId={} planCode={} cycle={}",
                    subscriber.getId(), plan.getCode(), cycle);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed subscriberId={} stripeCode={}",
                    subscriber.getId(), e.getCode());
            throw new RuntimeException("Stripe checkout session creation failed", e);
        }
    }

    @Override
    public String createPortalSession(String stripeCustomerId) {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(stripeCustomerId)
                        .setReturnUrl(appProperties.stripe().portalReturnUrl())
                        .build();

        // Deterministic idempotency key: customer id + return URL.
        String idempotencyKey = Hashing.sha256Hex("portal:" + stripeCustomerId + ":"
                + appProperties.stripe().portalReturnUrl());

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params, options);
            log.info("Stripe billing portal session created stripeCustomerId={}",
                    stripeCustomerId);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Stripe portal session creation failed stripeCode={}", e.getCode());
            throw new RuntimeException("Stripe portal session creation failed", e);
        }
    }

    @Override
    public void pauseSubscription(String stripeSubscriptionId, String idempotencyKey) {
        // pause_collection.behavior=void: no invoices are collected while paused.
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setPauseCollection(
                        SubscriptionUpdateParams.PauseCollection.builder()
                                .setBehavior(SubscriptionUpdateParams.PauseCollection.Behavior.VOID)
                                .build())
                .build();
        updateSubscription(stripeSubscriptionId, params, idempotencyKey, "pause");
    }

    @Override
    public void resumeSubscription(String stripeSubscriptionId, String idempotencyKey) {
        // Clearing pause_collection (empty) resumes normal collection.
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setPauseCollection(EmptyParam.EMPTY)
                .build();
        updateSubscription(stripeSubscriptionId, params, idempotencyKey, "resume");
    }

    @Override
    public void cancelSubscriptionAtPeriodEnd(String stripeSubscriptionId, String idempotencyKey) {
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        updateSubscription(stripeSubscriptionId, params, idempotencyKey, "cancel_at_period_end");
    }

    @Override
    public void cancelSubscriptionNow(String stripeSubscriptionId, String idempotencyKey) {
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            // No proration/refund params — Stripe's default cancel behaviour applies.
            subscription.cancel(SubscriptionCancelParams.builder().build(), options);
            log.info("Stripe subscription action=cancel_now stripeSubscriptionId={}", stripeSubscriptionId);
        } catch (StripeException e) {
            log.error("Stripe subscription action=cancel_now failed stripeSubscriptionId={} stripeCode={}",
                    stripeSubscriptionId, e.getCode());
            throw new RuntimeException("Stripe subscription cancel_now failed", e);
        }
    }

    /**
     * Retrieves the subscription and applies the given update with an idempotency key.
     * Centralises the retrieve → update → error-wrap flow shared by pause/resume/cancel.
     * The retrieve is a GET (no idempotency key needed); only the update is guarded.
     */
    private void updateSubscription(String stripeSubscriptionId, SubscriptionUpdateParams params,
                                    String idempotencyKey, String action) {
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            subscription.update(params, options);
            log.info("Stripe subscription action={} stripeSubscriptionId={}", action, stripeSubscriptionId);
        } catch (StripeException e) {
            log.error("Stripe subscription action={} failed stripeSubscriptionId={} stripeCode={}",
                    action, stripeSubscriptionId, e.getCode());
            throw new RuntimeException("Stripe subscription " + action + " failed", e);
        }
    }

    @Override
    public Event constructWebhookEvent(String payload, String sigHeader)
            throws SignatureVerificationException {
        String webhookSecret = appProperties.stripe().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Belt-and-suspenders: the startup guard in StripeConfig already rejects blank
            // secrets in production. This check ensures that even a misconfigured test or
            // a bean-construction ordering edge case cannot bypass verification with an
            // empty HMAC key (the JCE accepts empty keys and would verify against "" silently).
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET is blank — refusing to verify webhook signature. "
                    + "A blank secret would accept any payload signed with the empty key. "
                    + "Set STRIPE_WEBHOOK_SECRET before processing webhook events.");
        }
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    @Override
    public List<StripeInvoiceSummary> listInvoices(String stripeCustomerId, int limit) {
        if (!isConfigured()) {
            return List.of();
        }

        InvoiceListParams params = InvoiceListParams.builder()
                .setCustomer(stripeCustomerId)
                .setLimit((long) limit)
                .build();

        try {
            InvoiceCollection invoices = Invoice.list(params);
            return invoices.getData().stream()
                    .map(this::toInvoiceSummary)
                    .toList();
        } catch (StripeException e) {
            log.error("Stripe list invoices failed stripeCode={}", e.getCode());
            throw new RuntimeException("Stripe list invoices failed", e);
        }
    }

    @Override
    public Optional<StripePaymentMethodSummary> findDefaultPaymentMethod(String stripeCustomerId) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        CustomerRetrieveParams params = CustomerRetrieveParams.builder()
                .addExpand("invoice_settings.default_payment_method")
                .build();

        try {
            Customer customer = Customer.retrieve(stripeCustomerId, params, null);
            PaymentMethod paymentMethod = customer.getInvoiceSettings() != null
                    ? customer.getInvoiceSettings().getDefaultPaymentMethodObject()
                    : null;

            if (paymentMethod == null || paymentMethod.getCard() == null) {
                return Optional.empty();
            }

            PaymentMethod.Card card = paymentMethod.getCard();
            return Optional.of(new StripePaymentMethodSummary(
                    card.getBrand(),
                    card.getLast4(),
                    card.getExpMonth() != null ? card.getExpMonth().intValue() : 0,
                    card.getExpYear() != null ? card.getExpYear().intValue() : 0
            ));
        } catch (StripeException e) {
            log.error("Stripe find default payment method failed stripeCode={}", e.getCode());
            throw new RuntimeException("Stripe find default payment method failed", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * True when {@code app.stripe.secret-key} is configured. Guards {@link #listInvoices}
     * and {@link #findDefaultPaymentMethod} so a blank key degrades to an empty result
     * instead of the Stripe SDK making a doomed call with no API key set (same pattern as
     * {@link com.homekept.notification.SendGridEmailSender#send}).
     */
    private boolean isConfigured() {
        String secretKey = appProperties.stripe().secretKey();
        return secretKey != null && !secretKey.isBlank();
    }

    private StripeInvoiceSummary toInvoiceSummary(Invoice invoice) {
        return new StripeInvoiceSummary(
                invoice.getId(),
                invoice.getNumber(),
                invoice.getCreated() != null ? Instant.ofEpochSecond(invoice.getCreated()) : null,
                invoice.getAmountPaid() != null ? invoice.getAmountPaid().intValue() : 0,
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getHostedInvoiceUrl(),
                invoice.getInvoicePdf()
        );
    }

    /**
     * Selects the correct Stripe price id from the plan tier based on billing cycle.
     */
    private String resolvePriceId(PlanTier plan, BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> plan.getStripePriceIdMonthly();
            case ANNUAL  -> plan.getStripePriceIdAnnual();
        };
    }
}
