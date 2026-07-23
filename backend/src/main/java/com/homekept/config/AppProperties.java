package com.homekept.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Typed configuration binding for the {@code app.*} namespace in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("America/Toronto") String timezone,
        /**
         * Belt-and-suspenders Secure cookie flag. Set APP_SECURE_COOKIES=true in prod.
         * forward-headers-strategy also makes HttpServletRequest.isSecure() return true
         * behind the TLS-terminating proxy, so both defences are active in production.
         */
        @DefaultValue("false") boolean secureCookies,
        /**
         * When false (the default), the app refuses to start if the JWT signing key is
         * blank, shorter than 32 bytes, or equals the well-known dev sentinel value.
         * Set APP_DEV_MODE=true only for local development.
         */
        @DefaultValue("false") boolean devMode,
        Cors cors,
        Jwt jwt,
        Encryption encryption,
        AdminSeed adminSeed,
        Stripe stripe,
        // @DefaultValue so the binder constructs an all-defaults R2 even when no
        // app.r2.* keys are present (e.g. the test profile). R2 is designed to degrade
        // gracefully — every field defaults to blank and R2StorageService returns 503 —
        // so a missing block must NOT null out the component and NPE on startup. Unlike
        // jwt/encryption/adminSeed, which intentionally fail fast when absent.
        @DefaultValue R2 r2,
        /**
         * Public base URL of the frontend, used to build links in transactional emails
         * (e.g. the activation magic link). Set FRONTEND_BASE_URL in production
         * (e.g. https://homekept.ca). Defaults to the local dev origin.
         */
        @DefaultValue("http://localhost:8080") String frontendBaseUrl,
        // @DefaultValue for the same reason as r2: SendGrid degrades gracefully (blank
        // api-key/from-email → log-and-skip), so a missing app.sendgrid block must bind
        // to defaults rather than null the component and NPE on startup.
        @DefaultValue SendGrid sendGrid,
        // @DefaultValue for the same reason as r2/sendgrid: PostHog analytics degrades
        // gracefully (blank api-key → capture is a no-op), so a missing app.analytics block
        // must bind to defaults rather than null the component and NPE on startup.
        @DefaultValue Analytics analytics
) {

    public record Cors(
            @DefaultValue("http://localhost:5173") List<String> allowedOrigins
    ) {}

    public record Jwt(
            String signingKey,
            @DefaultValue("900") long accessTokenExpirySeconds,
            @DefaultValue("604800") long refreshTokenExpirySeconds
    ) {}

    public record Encryption(
            @DefaultValue("") String accessNotesKey
    ) {}

    /**
     * Admin seed config. When both email and password are non-blank and no user
     * with that email already exists, {@link com.homekept.identity.AdminSeeder}
     * creates an ADMIN user on startup. Idempotent: existing users are never touched.
     */
    public record AdminSeed(
            @DefaultValue("") String email,
            @DefaultValue("") String password
    ) {}

    /**
     * Stripe integration config. All values are sourced from environment variables.
     *
     * <p>In production, set:
     * <ul>
     *   <li>{@code STRIPE_SECRET_KEY} — live secret key (sk_live_...)</li>
     *   <li>{@code STRIPE_WEBHOOK_SECRET} — webhook signing secret (whsec_...)</li>
     *   <li>{@code STRIPE_SUCCESS_URL} — where to redirect after successful checkout</li>
     *   <li>{@code STRIPE_CANCEL_URL} — where to redirect on checkout cancellation</li>
     *   <li>{@code STRIPE_PORTAL_RETURN_URL} — where to return from the billing portal</li>
     * </ul>
     *
     * <p>If {@code secretKey} is blank on startup, a warning is logged. Stripe API calls
     * will fail at runtime but the app does not hard-fail — dev and test environments
     * often run without a real Stripe key.
     */
    public record Stripe(
            @DefaultValue("") String secretKey,
            @DefaultValue("") String webhookSecret,
            @DefaultValue("http://localhost:8080/app?checkout=success") String successUrl,
            @DefaultValue("http://localhost:8080/plans?checkout=cancel") String cancelUrl,
            @DefaultValue("http://localhost:8080/app/billing") String portalReturnUrl
    ) {}

    /**
     * Cloudflare R2 (S3-compatible) storage config for visit photos.
     *
     * <p>In production, set:
     * <ul>
     *   <li>{@code R2_ENDPOINT} — R2 S3-compatible endpoint URL
     *       (e.g. {@code https://<account_id>.r2.cloudflarestorage.com})</li>
     *   <li>{@code R2_BUCKET} — bucket name</li>
     *   <li>{@code R2_ACCESS_KEY_ID} — R2 access key id</li>
     *   <li>{@code R2_SECRET_ACCESS_KEY} — R2 secret access key (never log or commit)</li>
     *   <li>{@code R2_REGION} — region hint; R2 uses {@code auto} by default</li>
     * </ul>
     *
     * <p>If the endpoint or bucket is blank, {@link com.homekept.storage.R2StorageService}
     * will return a graceful 503 rather than NPE — R2 keys are a founder follow-up.
     * The app does not hard-fail on blank R2 config so dev/test environments work without
     * real credentials. The secret access key is NEVER logged.
     */
    public record R2(
            @DefaultValue("") String endpoint,
            @DefaultValue("") String bucket,
            @DefaultValue("") String accessKeyId,
            @DefaultValue("") String secretAccessKey,
            @DefaultValue("auto") String region
    ) {}

    /**
     * SendGrid transactional-email config for the {@code notification} domain.
     *
     * <p>In production set:
     * <ul>
     *   <li>{@code SENDGRID_API_KEY} — SendGrid API key (never log or commit)</li>
     *   <li>{@code SENDGRID_FROM_EMAIL} — verified sender address (e.g. no-reply@homekept.ca)</li>
     *   <li>{@code SENDGRID_FROM_NAME} — sender display name (defaults to HomeKept)</li>
     * </ul>
     *
     * <p>If {@code apiKey} or {@code fromEmail} is blank, {@link com.homekept.notification.SendGridEmailSender}
     * logs a warning and skips the send (no hard failure) — dev/test/CI run without a real
     * key. The api key is NEVER logged.
     */
    public record SendGrid(
            @DefaultValue("") String apiKey,
            @DefaultValue("") String fromEmail,
            @DefaultValue("HomeKept") String fromName
    ) {}

    /**
     * PostHog product-analytics config for the {@code analytics} domain (arch doc §5.7).
     *
     * <p>In production set:
     * <ul>
     *   <li>{@code POSTHOG_API_KEY} — PostHog <em>project</em> API key. This is a publishable
     *       key (safe in a client bundle), not a secret, but is still env-config, never
     *       hardcoded (arch doc §5.7). Blank → analytics capture is a silent no-op.</li>
     *   <li>{@code POSTHOG_HOST} — capture endpoint host. Defaults to the PostHog US Cloud
     *       ingestion host. PIPEDA requires protection, not residency (note it in the
     *       privacy policy).</li>
     * </ul>
     *
     * <p>If {@code apiKey} is blank, {@link com.homekept.analytics.PostHogAnalyticsService}
     * treats every {@code capture}/{@code alias} as a no-op (logged at debug) — dev, test,
     * and CI run without a real key and must not emit network calls. Analytics is strictly
     * best-effort: a capture failure is swallowed and never propagates into the business
     * transaction that triggered it.
     */
    public record Analytics(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://us.i.posthog.com") String host
    ) {}
}
