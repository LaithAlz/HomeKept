package com.homekept;

import com.homekept.storage.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Top-level test configuration that registers a {@code @Primary} {@link StorageService}
 * stub for tests that exercise photo endpoints without real R2 credentials.
 *
 * <p>Import with {@code @Import(FakeStorageServiceConfig.class)} only on tests that
 * exercise the photo upload/confirm flow.  All other tests leave R2 unconfigured; the
 * real {@link com.homekept.storage.R2StorageService} degrades to 503, which is the
 * correct behaviour when no R2 env-vars are set.
 *
 * <p>This is a top-level {@code @TestConfiguration} class (not nested) because nested
 * {@code @TestConfiguration} classes inside a {@code @SpringBootTest} class are
 * unreliable in Spring Boot 4.x — the bean registration can be silently skipped.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeStorageServiceConfig {

    /** Canned upload URL returned by the fake for every presign-upload call. */
    public static final String FAKE_UPLOAD_URL = "https://r2.test/put";

    /** Canned download URL returned by the fake for every presign-download call. */
    public static final String FAKE_DOWNLOAD_URL = "https://r2.test/get";

    @Bean
    @Primary
    StorageService fakeStorageService() {
        return new StorageService() {

            /**
             * Returns a canned {@link PresignedUpload} with the supplied storage key so
             * the photo-endpoint happy path does not need real R2 credentials.
             */
            @Override
            public PresignedUpload presignUpload(String storageKey, String contentType, long contentLength) {
                return new PresignedUpload(FAKE_UPLOAD_URL, storageKey);
            }

            /**
             * Returns a canned download URL.  The storage key is accepted without
             * verification because tests only call this indirectly (if at all).
             */
            @Override
            public String presignDownload(String storageKey) {
                return FAKE_DOWNLOAD_URL;
            }
        };
    }
}
