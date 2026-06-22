package com.homekept.subscription;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe webhook events.
 *
 * <p>This endpoint is PUBLIC (no JWT required). Stripe signs every request with an
 * HMAC-SHA256 signature delivered in the {@code Stripe-Signature} header.
 * {@link StripeService#constructWebhookEvent} verifies that signature. A blank
 * {@code STRIPE_WEBHOOK_SECRET} is also rejected here (throws before SDK call) so forged
 * events are never processed even in misconfigured environments.
 *
 * <h2>Response codes</h2>
 * <ul>
 *   <li><b>200</b> — event handled, ignored, duplicate (sequential or concurrent),
 *       or out-of-order state machine skip. Stripe will not retry.</li>
 *   <li><b>400</b> — signature verification failed (bad signature, stale timestamp, or
 *       blank webhook secret). Stripe does NOT retry on 400, which is intentional —
 *       a bad sig means the payload was not from Stripe and should be discarded.</li>
 *   <li><b>5xx</b> — unexpected processing error. Stripe retries on 5xx, which is what
 *       we want for transient failures (DB down, etc.). Exceptions from
 *       {@link StripeWebhookService} propagate here and Spring returns 500.</li>
 * </ul>
 *
 * <p>Concurrent duplicate detection: {@link StripeWebhookService} persists the
 * {@code subscription_event} row inside the same transaction as the state change using
 * {@code saveAndFlush}. If two deliveries race, the second hits the unique index and
 * throws {@link DataIntegrityViolationException}. This controller catches that exception
 * and returns 200 (the first delivery already applied — not a server error).
 *
 * <p>The raw body must be read as a {@code String} here. Spring must NOT try to parse the
 * body as JSON before it arrives at this method — the signature covers the exact raw bytes,
 * and any JSON reformatting would invalidate it. This is guaranteed by
 * {@code @RequestBody String payload}.
 */
@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeService stripeService;
    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeService stripeService,
                                   StripeWebhookService webhookService) {
        this.stripeService = stripeService;
        this.webhookService = webhookService;
    }

    /**
     * POST /api/webhooks/stripe
     *
     * @param payload   raw request body — MUST be the unmodified bytes Stripe sent
     * @param sigHeader value of the {@code Stripe-Signature} header Stripe attaches
     * @return 200 on success (handled, ignored, or duplicate); 400 on bad signature
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = stripeService.constructWebhookEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.warn("webhook_bad_signature — rejecting event");
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            // Thrown by StripeServiceImpl when the webhook secret is blank (belt-and-suspenders).
            // Treat as a configuration-level auth failure: refuse the request (400) so Stripe
            // stops delivery. The startup guard in StripeConfig prevents this path in production
            // (dev-mode=false); this path only fires if the guard was bypassed somehow.
            log.warn("webhook_rejected — {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.debug("webhook_received type={} stripeEventId={}", event.getType(), event.getId());

        // StripeWebhookService handles idempotency, dispatch, and state transitions.
        // Unexpected exceptions propagate and produce 500 (Stripe retries).
        //
        // DataIntegrityViolationException means a concurrent duplicate delivery hit the
        // unique index on stripe_event_id before the first delivery committed. The entire
        // transaction (state change + event row) was rolled back for the loser. Return 200
        // so Stripe does not retry — the winner already applied the event correctly.
        try {
            webhookService.handle(event, payload);
        } catch (DataIntegrityViolationException e) {
            log.debug("webhook_concurrent_duplicate stripeEventId={} — already applied by concurrent delivery",
                    event.getId());
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok().build();
    }
}
