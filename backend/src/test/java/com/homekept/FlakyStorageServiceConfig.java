package com.homekept;

import com.homekept.storage.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Top-level test configuration that registers a {@code @Primary} {@link StorageService}
 * stub whose {@code presignDownload} throws a plain {@link RuntimeException} (NOT
 * {@code StorageUnavailableException}) for any storage key containing
 * {@link #FAILING_KEY_MARKER}, and returns a canned URL for every other key.
 *
 * <p>Used to test that a single photo's signing failure for a reason OTHER than "R2 is
 * unconfigured" (e.g. an AWS SDK error, a malformed/legacy storage key) degrades to that
 * one photo being skipped, rather than a 500 for the whole {@code GET /api/app/visits/{id}}
 * response.
 *
 * <p>Top-level (not nested) for the same reason as {@link FakeStorageServiceConfig}: nested
 * {@code @TestConfiguration} classes inside a {@code @SpringBootTest} class are unreliable
 * in Spring Boot 4.x — the bean registration can be silently skipped.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FlakyStorageServiceConfig {

    /** Canned download URL returned for any storage key that does not contain the marker. */
    public static final String FAKE_DOWNLOAD_URL = "https://r2.test/get";

    /** Any storage key containing this substring triggers a simulated non-R2-outage signing failure. */
    public static final String FAILING_KEY_MARKER = "throws-signing-error";

    @Bean
    @Primary
    StorageService flakyStorageService() {
        return new StorageService() {

            @Override
            public PresignedUpload presignUpload(String storageKey, String contentType) {
                throw new UnsupportedOperationException("not used in this test");
            }

            /**
             * Throws a plain {@link RuntimeException} for keys containing
             * {@link #FAILING_KEY_MARKER} — simulating a signing failure that is NOT
             * {@code StorageUnavailableException} (e.g. an SDK error) — and returns a
             * canned URL otherwise.
             */
            @Override
            public String presignDownload(String storageKey) {
                if (storageKey.contains(FAILING_KEY_MARKER)) {
                    throw new RuntimeException("simulated signing failure (not R2-unconfigured)");
                }
                return FAKE_DOWNLOAD_URL;
            }
        };
    }
}
