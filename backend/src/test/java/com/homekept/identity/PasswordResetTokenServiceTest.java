package com.homekept.identity;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PasswordResetTokenService}'s enumeration-timing compensation delay
 * (#115 finding 1). No Spring context — pure logic. {@code mintDummy()} never touches the
 * repository, so a {@code null} repository is safe to construct the service with here.
 */
class PasswordResetTokenServiceTest {

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,
                true, // devMode
                new AppProperties.Cors(List.of()),
                new AppProperties.Jwt("unit-test-signing-key-min-256-bits!!", 900L, 604800L),
                new AppProperties.Encryption(""),
                new AppProperties.AdminSeed("", ""),
                new AppProperties.Stripe("", "", "", "", ""),
                new AppProperties.R2("", "", "", "", ""), "http://localhost:8080", new AppProperties.SendGrid("", "", "HomeKept")
        );
        service = new PasswordResetTokenService(null, props);
    }

    // ── computeJitteredDelayMs (deterministic — no sleeping) ──────────────────

    @Test
    void computeJitteredDelayMs_alwaysWithinConfiguredBounds() {
        for (int i = 0; i < 500; i++) {
            long delay = service.computeJitteredDelayMs();
            assertThat(delay).isBetween(
                    PasswordResetTokenService.MIN_DUMMY_DELAY_MS,
                    PasswordResetTokenService.MAX_DUMMY_DELAY_MS);
        }
    }

    @Test
    void computeJitteredDelayMs_isJittered_notConstant() {
        // A constant delay would itself become a distinguishing signal (see mintDummy's
        // Javadoc) — assert repeated samples aren't all identical.
        Set<Long> samples = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            samples.add(service.computeJitteredDelayMs());
        }
        assertThat(samples.size()).isGreaterThan(1);
    }

    // ── mintDummy (behavioral contract: the dummy path actually applies the delay) ──

    @Test
    void mintDummy_blocksForAtLeastTheConfiguredMinimumDelay() {
        long start = System.nanoTime();
        service.mintDummy();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Tolerance below the nominal minimum for timer-resolution jitter — this asserts the
        // behavioral contract (the delay is applied), not a precise/flaky wall-clock number.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(PasswordResetTokenService.MIN_DUMMY_DELAY_MS - 20);
    }
}
