package com.homekept.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

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
}
