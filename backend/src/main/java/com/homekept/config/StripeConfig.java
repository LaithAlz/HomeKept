package com.homekept.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Initialises the Stripe Java SDK at startup.
 *
 * <p>Sets {@code com.stripe.Stripe.apiKey} from {@code app.stripe.secret-key} (env
 * {@code STRIPE_SECRET_KEY}).  If the key is blank the app does NOT hard-fail — dev
 * and test environments run without a live key — but a prominent WARNING is logged so
 * the condition is never silently missed in production.
 *
 * <p><b>Production requirement:</b> {@code STRIPE_SECRET_KEY} must be set to the live
 * secret key (sk_live_...) before any Stripe API call is made.  Similarly,
 * {@code STRIPE_WEBHOOK_SECRET} (whsec_...) must be set for webhook signature
 * verification to succeed.
 *
 * <p><b>Webhook secret guard:</b> when {@code dev-mode=false} (production), a blank
 * {@code STRIPE_WEBHOOK_SECRET} causes an {@link IllegalStateException} at startup.
 * An empty HMAC key is accepted by the JCE, which would allow any party who knows the
 * empty-string key to forge valid webhook signatures; the app must fail closed rather
 * than process forged events. In dev/test mode the guard is skipped and a warning is
 * logged instead.
 */
@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    private final AppProperties appProperties;

    public StripeConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void configureStripe() {
        configureApiKey();
        validateWebhookSecret();
    }

    private void configureApiKey() {
        String secretKey = appProperties.stripe().secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("STRIPE_SECRET_KEY is not configured. Stripe API calls will fail at runtime. "
                    + "Set STRIPE_SECRET_KEY in the environment before processing payments.");
            return;
        }
        com.stripe.Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialised (key prefix: {}...)",
                secretKey.length() > 8 ? secretKey.substring(0, 8) : "[short]");
    }

    /**
     * Startup guard for the webhook signing secret.
     *
     * <p>A blank {@code STRIPE_WEBHOOK_SECRET} is an authentication hole: the Stripe SDK's
     * {@code Webhook.constructEvent} will accept any payload signed with the empty-string
     * key when the configured secret is empty, because the JCE accepts a zero-length HMAC
     * key. In production this would allow anyone to forge valid webhook events.
     *
     * <p>When {@code dev-mode=false}: throws {@link IllegalStateException} if the webhook
     * secret is blank — the application must not start.
     * When {@code dev-mode=true}: logs a prominent warning and continues (allows local
     * development without a real Stripe account).
     */
    private void validateWebhookSecret() {
        String webhookSecret = appProperties.stripe().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            if (appProperties.devMode()) {
                log.warn("STRIPE_WEBHOOK_SECRET is not configured. Webhook signature verification "
                        + "will fail at runtime. Set STRIPE_WEBHOOK_SECRET before processing "
                        + "live webhook events. (Allowed in dev-mode=true.)");
            } else {
                throw new IllegalStateException(
                        "STRIPE_WEBHOOK_SECRET must not be blank in production. "
                        + "A blank webhook secret allows forged events to pass signature verification. "
                        + "Set STRIPE_WEBHOOK_SECRET (whsec_...) in the environment, "
                        + "or set APP_DEV_MODE=true for local development only.");
            }
        }
    }
}
