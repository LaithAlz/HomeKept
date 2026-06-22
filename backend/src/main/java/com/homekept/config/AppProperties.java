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
        Stripe stripe
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
}
