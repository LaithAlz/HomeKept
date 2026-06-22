package com.homekept.subscription;

import com.homekept.catalog.PlanTier;
import com.homekept.config.AppProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
                                        boolean foundingRate, String idempotencyKey) {
        String priceId = resolvePriceId(plan, cycle, foundingRate);
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalStateException(
                    "PlanTier " + plan.getCode() + " has no Stripe price id for cycle=" + cycle
                            + " foundingRate=" + foundingRate + ". Set it in the DB before checkout.");
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(String.valueOf(subscriber.getId()))
                .putMetadata("subscriberId", String.valueOf(subscriber.getId()))
                .putMetadata("planTierId", String.valueOf(plan.getId()))
                .putMetadata("foundingRate", String.valueOf(foundingRate))
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
            log.info("Stripe checkout session created subscriberId={} planCode={} cycle={} foundingRate={}",
                    subscriber.getId(), plan.getCode(), cycle, foundingRate);
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
        String idempotencyKey = sha256Hex("portal:" + stripeCustomerId + ":"
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Selects the correct Stripe price id from the plan tier based on billing cycle
     * and whether the founding rate applies.
     *
     * <p>Founding rate uses the founding price id regardless of cycle (it is a
     * monthly-only special rate per docs/pricing-and-visits.md).
     */
    private String resolvePriceId(PlanTier plan, BillingCycle cycle, boolean foundingRate) {
        if (foundingRate) {
            return plan.getStripePriceIdFounding();
        }
        return switch (cycle) {
            case MONTHLY -> plan.getStripePriceIdMonthly();
            case ANNUAL  -> plan.getStripePriceIdAnnual();
        };
    }

    /**
     * Produces a deterministic SHA-256 hex digest of the input string.
     * Used to build idempotency keys that are safe to pass to Stripe (max 255 chars).
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec — never thrown in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
