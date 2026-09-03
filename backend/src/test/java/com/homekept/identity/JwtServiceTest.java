package com.homekept.identity;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}. No Spring context — pure logic.
 */
class JwtServiceTest {

    private static final String SIGNING_KEY = "unit-test-signing-key-min-256-bits!!";

    /** Same mapper type/construction {@link JwtService} receives from Spring in production. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,  // secureCookies
                true,   // devMode — relaxes the key strength guard in unit tests
                new AppProperties.Cors(java.util.List.of("http://localhost:5173")),
                new AppProperties.Jwt(SIGNING_KEY, 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        jwtService = new JwtService(props, OBJECT_MAPPER);
    }

    @Test
    void issueAndValidate_roundtrip_succeeds() {
        User user = testUser(42L, "alice@example.com", Role.CUSTOMER);
        String token = jwtService.issueAccessToken(user);

        Optional<Map<String, Object>> claims = jwtService.validateAndParseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().get("sub")).isEqualTo("42");
        assertThat(claims.get().get("email")).isEqualTo("alice@example.com");
        assertThat(claims.get().get("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void validate_tamperedPayload_returnsEmpty() {
        User user = testUser(1L, "alice@example.com", Role.CUSTOMER);
        String token = jwtService.issueAccessToken(user);

        // Tamper with the middle section
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".dGFtcGVyZWQ." + parts[2];

        assertThat(jwtService.validateAndParseClaims(tampered)).isEmpty();
    }

    @Test
    void validate_wrongKey_returnsEmpty() {
        // Token signed with a different key
        AppProperties otherProps = new AppProperties(
                "America/Toronto",
                false,
                true,   // devMode
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt("completely-different-signing-key-32b!!", 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService otherJwt = new JwtService(otherProps, OBJECT_MAPPER);

        User user = testUser(1L, "bob@example.com", Role.ADMIN);
        String token = otherJwt.issueAccessToken(user);

        assertThat(jwtService.validateAndParseClaims(token)).isEmpty();
    }

    @Test
    void validate_null_returnsEmpty() {
        assertThat(jwtService.validateAndParseClaims(null)).isEmpty();
    }

    @Test
    void validate_garbageString_returnsEmpty() {
        assertThat(jwtService.validateAndParseClaims("not.a.jwt.at.all")).isEmpty();
        assertThat(jwtService.validateAndParseClaims("garbage")).isEmpty();
    }

    @Test
    void validate_expiredToken_returnsEmpty() throws Exception {
        // Issue a token with 0-second expiry by manipulating the exp claim manually.
        // Since JwtService has a fixed expiry, we test by inspecting a token whose
        // exp has already passed. We do this by issuing with a 1-second expiry service.
        AppProperties shortProps = new AppProperties(
                "America/Toronto",
                false,
                true,   // devMode
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt(SIGNING_KEY, -1L, 604800L), // -1 second = already expired
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService shortJwt = new JwtService(shortProps, OBJECT_MAPPER);
        User user = testUser(1L, "bob@example.com", Role.ADMIN);
        String expiredToken = shortJwt.issueAccessToken(user);

        // Validate with the normal service (same key) — should reject expired
        assertThat(jwtService.validateAndParseClaims(expiredToken)).isEmpty();
    }

    @Test
    void token_containsExpectedClaims() {
        User user = testUser(99L, "admin@example.com", Role.ADMIN);
        String token = jwtService.issueAccessToken(user);

        Optional<Map<String, Object>> claims = jwtService.validateAndParseClaims(token);
        assertThat(claims).isPresent();
        assertThat(claims.get()).containsKeys("sub", "email", "role", "iat", "exp");
        assertThat(claims.get().get("sub")).isEqualTo("99");
        assertThat(claims.get().get("role")).isEqualTo("ADMIN");
    }

    // ── Jackson migration: round-trip + wire-format pinning ─────────────────────

    @Test
    void roundTrip_parsedClaimsEqualIssuedValues() {
        long before = System.currentTimeMillis() / 1000;
        User user = testUser(7L, "carol@example.com", Role.CUSTOMER);
        String token = jwtService.issueAccessToken(user);
        long after = System.currentTimeMillis() / 1000;

        Optional<Map<String, Object>> claimsOpt = jwtService.validateAndParseClaims(token);
        assertThat(claimsOpt).isPresent();
        Map<String, Object> claims = claimsOpt.get();

        assertThat(claims.get("sub")).isEqualTo("7");
        assertThat(claims.get("email")).isEqualTo("carol@example.com");
        assertThat(claims.get("role")).isEqualTo("CUSTOMER");

        long iat = ((Number) claims.get("iat")).longValue();
        long exp = ((Number) claims.get("exp")).longValue();
        assertThat(iat).isBetween(before, after);
        // 900L is the accessTokenExpirySeconds configured in setUp().
        assertThat(exp).isEqualTo(iat + 900L);
    }

    @Test
    void validate_unexpectedClaimType_returnsEmpty() throws Exception {
        // A structurally valid JWT whose "exp" claim is a JSON string instead of a number.
        String headerB64 = base64UrlEncode(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"sub\":\"1\",\"email\":\"eve@example.com\",\"role\":\"CUSTOMER\","
                + "\"iat\":1700000000,\"exp\":\"abc\"}";
        String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        String signature = hmacSha256Base64Url(signingInput, SIGNING_KEY);
        String token = signingInput + "." + signature;

        assertThat(jwtService.validateAndParseClaims(token)).isEmpty();
    }

    @Test
    void headerWireFormat_matchesHistoricalHandRolledBytes() {
        // Pinned against the pre-Jackson hand-rolled implementation's HEADER_B64 constant,
        // computed from {"alg":"HS256","typ":"JWT"} with no whitespace.
        User user = testUser(1L, "pin@example.com", Role.CUSTOMER);
        String token = jwtService.issueAccessToken(user);
        String headerB64 = token.split("\\.")[0];

        assertThat(headerB64).isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }

    @Test
    void payloadWireFormat_matchesHistoricalHandRolledBytes() {
        // Pinned against the pre-Jackson hand-rolled implementation's buildPayloadJson output
        // for fixed inputs: {"sub":"42","email":"alice@example.com","role":"CUSTOMER",
        // "iat":1700000000,"exp":1700000900} — same key order, same quoting, no whitespace.
        String payloadJson = jwtService.buildPayloadJson(
                "42", "alice@example.com", "CUSTOMER", 1700000000L, 1700000900L);

        assertThat(payloadJson).isEqualTo(
                "{\"sub\":\"42\",\"email\":\"alice@example.com\",\"role\":\"CUSTOMER\","
                + "\"iat\":1700000000,\"exp\":1700000900}");

        String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        assertThat(payloadB64).isEqualTo(
                "eyJzdWIiOiI0MiIsImVtYWlsIjoiYWxpY2VAZXhhbXBsZS5jb20iLCJyb2xlIjoiQ1VTVE9NRVIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMDkwMH0");
    }

    // ── Startup guard ─────────────────────────────────────────────────────────

    @Test
    void startupGuard_rejectsShortKey_whenDevModeIsFalse() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,
                false, // dev-mode OFF — guard is active
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt("short-key", 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService svc = new JwtService(props, OBJECT_MAPPER);
        // validateKeyStrength() is called by @PostConstruct; call it directly in unit test
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                svc::validateKeyStrength,
                "Expected rejection of a short key in prod mode"
        );
    }

    @Test
    void startupGuard_rejectsSentinelKey_whenDevModeIsFalse() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,
                false, // dev-mode OFF
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt(JwtService.DEV_SENTINEL_KEY, 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService svc = new JwtService(props, OBJECT_MAPPER);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                svc::validateKeyStrength
        );
    }

    @Test
    void startupGuard_allowsSentinelKey_whenDevModeIsTrue() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,
                true, // dev-mode ON — guard relaxed
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt(JwtService.DEV_SENTINEL_KEY, 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService svc = new JwtService(props, OBJECT_MAPPER);
        // Must not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(svc::validateKeyStrength);
    }

    @Test
    void startupGuard_allowsStrongKey_whenDevModeIsFalse() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,
                false,
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt("a-strong-non-sentinel-key-that-is-definitely-32-bytes!!", 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept"),
                new AppProperties.Analytics("", "https://us.i.posthog.com")
        );
        JwtService svc = new JwtService(props, OBJECT_MAPPER);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(svc::validateKeyStrength);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User testUser(Long id, String email, Role role) {
        User user = new User(email, "hash", "First", "Last", role, UserStatus.ACTIVE);
        // Reflectively set ID since there's no public setter (entity manages its PK)
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Independently computes an HS256 signature so tests can craft arbitrary token payloads. */
    private static String hmacSha256Base64Url(String signingInput, String key) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(sig);
    }
}
