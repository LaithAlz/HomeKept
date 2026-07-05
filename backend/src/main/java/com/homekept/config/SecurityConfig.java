package com.homekept.config;

import com.homekept.identity.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 7 configuration (lambda DSL — no deprecated 6.x patterns).
 *
 * <h2>CSRF decision</h2>
 * <p>This API uses cookie-based auth with {@code SameSite=Lax}. The frontend is served
 * at {@code homekept.ca} and the API at {@code api.homekept.ca} — same registrable
 * domain ("same-site") so {@code SameSite=Lax} cookies are sent on same-site navigations
 * and same-site fetch calls with {@code credentials: "include"}, but NOT on cross-site
 * sub-resource requests. This means a third-party site cannot trigger a state-mutating
 * request that carries our cookies.
 *
 * <p>We disable Spring's built-in CSRF token mechanism because:
 * <ol>
 *   <li>{@code SameSite=Lax} provides the CSRF protection for this same-site setup.</li>
 *   <li>All state-mutating endpoints require a valid JWT access token (15-min expiry),
 *       giving a second layer of protection.</li>
 *   <li>Spring's CSRF token scheme requires either session state or a synchronised token,
 *       both of which conflict with a stateless JWT architecture.</li>
 *   <li>The {@code /api/auth/login} POST carries a JSON body (not
 *       {@code application/x-www-form-urlencoded}), which is not forgeable by a simple
 *       HTML form from a different site.</li>
 * </ol>
 *
 * <p><b>Residual risk:</b> if the API is ever accessed from a cross-site context
 * (different registrable domain) the SameSite protection degrades. The escape-hatch
 * (proxying through the Cloudflare Worker so the browser sees one origin) is noted in
 * arch doc §5.1 and should be implemented before any such deployment.
 *
 * <h2>Session management</h2>
 * <p>STATELESS — Spring Security does not create or use an HTTP session. All auth state
 * is in the JWT access cookie (short-lived) and the refresh token row in Postgres.
 *
 * <h2>Method security</h2>
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} annotations. Future
 * domains use {@code @PreAuthorize("hasRole('ADMIN')")} etc. on their service/controller
 * methods as the second authorisation layer (filter is the first).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppProperties appProperties;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AppProperties appProperties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.appProperties = appProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF disabled — rationale in class Javadoc above.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public allowlist (arch doc §5.1 + api-contract.md)
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        // Forgot/reset password (api-contract.md §Auth) — must be reachable without
                        // a session. forgot is rate-limited 5/IP/hour in AuthController; reset's
                        // token is HMAC-signed + single-use (PasswordResetTokenService).
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset").permitAll()
                        // Logout is public so a user with an expired access token can still
                        // revoke their refresh tokens. The handler resolves the user from
                        // the refresh cookie directly; it does not require a valid access token.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Public catalog endpoints — pricing page reads (GET only; arch doc §5.1)
                        .requestMatchers(HttpMethod.GET, "/api/catalog/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/picks").permitAll()
                        // Public booking form submission (arch doc §5.1, api-contract.md)
                        // Rate-limited 3/IP/hour in BookingController; CASL consent enforced in DTO.
                        .requestMatchers(HttpMethod.POST, "/api/bookings/walkthrough").permitAll()
                        // Activation magic-link flow (api-contract.md §Activation)
                        // Rate-limited 10/IP/hour in ActivationController; token is HMAC-signed + single-use.
                        .requestMatchers(HttpMethod.POST, "/api/activation/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/activation/complete").permitAll()
                        // Stripe webhook — public; signature is verified by StripeWebhookController
                        // using the STRIPE_WEBHOOK_SECRET (Stripe-Signature header, HMAC-SHA256).
                        // Auth cookies are never sent for this endpoint (Stripe does not send them).
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler())
                )
                // Disable form login and HTTP Basic — we use JWT cookies only
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept"));
        config.setAllowCredentials(true); // required for cookie-based auth across origins
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Returns 401 (Unauthorized) — no auth cookie / invalid token. */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException e) -> {
            res.setContentType("application/json");
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write(errorJson("UNAUTHORIZED", "Authentication required"));
        };
    }

    /**
     * Returns 403 (Forbidden) — authenticated but insufficient role.
     * Per CLAUDE.md: 403 = wrong role, 404 = not found or not yours (ownership).
     */
    private AccessDeniedHandler forbiddenHandler() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.access.AccessDeniedException e) -> {
            res.setContentType("application/json");
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.getWriter().write(errorJson("FORBIDDEN", "Insufficient permissions"));
        };
    }

    /**
     * Builds the API-contract error envelope for the filter-level handlers, which run
     * before the controller layer and so never reach {@code GlobalExceptionHandler}.
     * {@code request_id} is freshly generated (not echoed from the client's X-Request-Id
     * header) because this writes raw JSON — echoing an attacker-controlled header would
     * be a JSON-injection vector. The generated id is hex-only and safe to inline.
     */
    private String errorJson(String code, String message) {
        String requestId = "req_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"request_id\":\"" + requestId + "\"}}";
    }
}
