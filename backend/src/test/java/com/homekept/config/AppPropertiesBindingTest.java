package com.homekept.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the V7 NPE: when the {@code app.r2.*} block is absent (as in the
 * test profile), the nested {@code R2} component must still bind to an all-defaults
 * instance — not null — so {@link com.homekept.storage.R2StorageService} degrades to 503
 * instead of failing the ApplicationContext on startup.
 */
class AppPropertiesBindingTest {

    @Test
    void r2_bindsToAllDefaults_whenBlockAbsent() {
        // Mimic the test profile: jwt/encryption/admin-seed present, NO app.r2.* keys.
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.timezone", "America/Toronto")
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.r2()).as("r2 must not be null when app.r2.* is absent").isNotNull();
        assertThat(props.r2().endpoint()).isEmpty();
        assertThat(props.r2().bucket()).isEmpty();
        assertThat(props.r2().region()).isEqualTo("auto");
    }

    @Test
    void r2_bindsProvidedValues_whenBlockPresent() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx")
                .withProperty("app.r2.endpoint", "https://example.r2.cloudflarestorage.com")
                .withProperty("app.r2.bucket", "homekept-photos");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.r2().endpoint()).isEqualTo("https://example.r2.cloudflarestorage.com");
        assertThat(props.r2().bucket()).isEqualTo("homekept-photos");
        assertThat(props.r2().region()).isEqualTo("auto");
    }

    /**
     * Regression guard for bug #120: before this fix, application.yml had no
     * {@code app.sendgrid.*} block at all, so {@code SENDGRID_API_KEY} bound to
     * nothing and {@link com.homekept.notification.SendGridEmailSender} silently
     * stayed off. Mirrors the r2 tests above: absent block still binds (not null),
     * degrading gracefully to blank defaults.
     */
    @Test
    void sendGrid_bindsToAllDefaults_whenBlockAbsent() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.timezone", "America/Toronto")
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.sendGrid()).as("sendGrid must not be null when app.sendgrid.* is absent").isNotNull();
        assertThat(props.sendGrid().apiKey()).isEmpty();
        assertThat(props.sendGrid().fromEmail()).isEmpty();
        assertThat(props.sendGrid().fromName()).isEqualTo("HomeKept");
    }

    @Test
    void sendGrid_bindsProvidedValues_whenBlockPresent() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx")
                .withProperty("app.sendgrid.api-key", "dummy-key-not-real")
                .withProperty("app.sendgrid.from-email", "no-reply@homekept.ca")
                .withProperty("app.sendgrid.from-name", "HomeKept Ops");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.sendGrid().apiKey()).isEqualTo("dummy-key-not-real");
        assertThat(props.sendGrid().fromEmail()).isEqualTo("no-reply@homekept.ca");
        assertThat(props.sendGrid().fromName()).isEqualTo("HomeKept Ops");
    }

    // Note: relaxed env-var-name binding (APP_SENDGRID_API_KEY) is Spring Boot's own
    // binder behavior and is exercised end to end in production via the
    // application.yml ${SENDGRID_API_KEY:} placeholder; it is not re-tested here (the
    // provided-value and absent-default cases above already prove app.sendgrid.* binds).

    @Test
    void frontendBaseUrl_bindsProvidedValue() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx")
                .withProperty("app.frontend-base-url", "https://homekept.ca");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.frontendBaseUrl()).isEqualTo("https://homekept.ca");
    }

    @Test
    void frontendBaseUrl_defaultsToLocalDevOrigin_whenAbsent() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.signing-key", "test-only-not-a-real-signing-key-placeholder-xx");

        AppProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app", AppProperties.class)
                .get();

        assertThat(props.frontendBaseUrl()).isEqualTo("http://localhost:8080");
    }
}
