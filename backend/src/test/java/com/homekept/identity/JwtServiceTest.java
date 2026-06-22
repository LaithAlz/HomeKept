package com.homekept.identity;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}. No Spring context — pure logic.
 */
class JwtServiceTest {

    private static final String SIGNING_KEY = "unit-test-signing-key-min-256-bits!!";

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
                new AppProperties.R2("", "", "", "", "")
        );
        jwtService = new JwtService(props);
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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService otherJwt = new JwtService(otherProps);

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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService shortJwt = new JwtService(shortProps);
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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService svc = new JwtService(props);
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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService svc = new JwtService(props);
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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService svc = new JwtService(props);
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
                new AppProperties.R2("", "", "", "", "")
        );
        JwtService svc = new JwtService(props);
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
}
