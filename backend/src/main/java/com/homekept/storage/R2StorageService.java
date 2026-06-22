package com.homekept.storage;

import com.homekept.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Cloudflare R2 implementation of {@link StorageService} using the AWS SDK S3 presigner.
 *
 * <h2>Configuration</h2>
 * <p>Reads from {@code app.r2.*} bound via {@link AppProperties.R2}. In production:
 * <ul>
 *   <li>{@code R2_ENDPOINT} — R2 S3-compatible endpoint</li>
 *   <li>{@code R2_BUCKET} — bucket name</li>
 *   <li>{@code R2_ACCESS_KEY_ID} / {@code R2_SECRET_ACCESS_KEY} — R2 API token pair</li>
 *   <li>{@code R2_REGION} — defaults to {@code auto}</li>
 * </ul>
 *
 * <h2>Graceful degradation</h2>
 * <p>If the endpoint or bucket is blank, the presigner is not initialised and each method
 * throws {@link StorageUnavailableException} (maps to 503). The app does NOT fail to start
 * — R2 credentials are a founder follow-up (same pattern as Stripe). The secret access key
 * is NEVER logged.
 *
 * <h2>Presigner lifecycle</h2>
 * <p>The {@link S3Presigner} is built once at startup (lazy singleton via {@link PostConstruct})
 * and reused for all presign calls. It is thread-safe per the AWS SDK documentation.
 * Path-style access is required for R2 (R2 does not support virtual-hosted style).
 *
 * <h2>Signed URL TTL</h2>
 * <p>Both upload (PUT) and download (GET) URLs expire after 15 minutes per the API contract
 * (arch doc §6.4).
 */
@Service
public class R2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final AppProperties.R2 r2Config;

    /** Initialised in {@link #init()} if R2 is configured; null otherwise. */
    private S3Presigner presigner;

    public R2StorageService(AppProperties appProperties) {
        this.r2Config = appProperties.r2();
    }

    @PostConstruct
    void init() {
        if (r2Config.endpoint().isBlank() || r2Config.bucket().isBlank()) {
            // Warn, but do not fail startup. Dev/test environments operate without R2.
            log.warn("R2StorageService: R2_ENDPOINT or R2_BUCKET is blank — " +
                     "photo upload/download will return 503. Set R2_* env vars in production.");
            this.presigner = null;
            return;
        }

        if (r2Config.accessKeyId().isBlank() || r2Config.secretAccessKey().isBlank()) {
            log.warn("R2StorageService: R2_ACCESS_KEY_ID or R2_SECRET_ACCESS_KEY is blank — " +
                     "photo upload/download will return 503. Set R2_* env vars in production.");
            this.presigner = null;
            return;
        }

        try {
            // The secret is consumed here and intentionally not stored in a field or logged.
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    r2Config.accessKeyId(),
                    r2Config.secretAccessKey()
            );

            this.presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(r2Config.endpoint()))
                    .region(Region.of(r2Config.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(
                            software.amazon.awssdk.services.s3.S3Configuration.builder()
                                    .pathStyleAccessEnabled(true) // required for R2
                                    .build()
                    )
                    .build();

            log.info("R2StorageService: initialised — bucket={} endpoint={}",
                    r2Config.bucket(), r2Config.endpoint());
        } catch (Exception e) {
            // Don't crash startup for a storage init failure.
            log.error("R2StorageService: failed to initialise presigner — " +
                      "photo endpoints will return 503", e);
            this.presigner = null;
        }
    }

    /**
     * Generates a 15-minute signed PUT URL.
     *
     * @param storageKey  server-generated R2 key (e.g. {@code visits/42/uuid})
     * @param contentType MIME type of the upload (validated by the caller)
     * @return presigned upload details
     * @throws StorageUnavailableException if R2 is not configured
     */
    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType) {
        requirePresigner();

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(r2Config.bucket())
                .key(storageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = presigner.presignPutObject(presignRequest)
                .url()
                .toString();

        log.debug("r2_presign_upload storageKey={}", storageKey);
        return new PresignedUpload(uploadUrl, storageKey);
    }

    /**
     * Generates a 15-minute signed GET URL.
     *
     * @param storageKey the R2 object key
     * @return signed GET URL string
     * @throws StorageUnavailableException if R2 is not configured
     */
    @Override
    public String presignDownload(String storageKey) {
        requirePresigner();

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(r2Config.bucket())
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(getRequest)
                .build();

        String url = presigner.presignGetObject(presignRequest)
                .url()
                .toString();

        log.debug("r2_presign_download storageKey={}", storageKey);
        return url;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requirePresigner() {
        if (presigner == null) {
            throw new StorageUnavailableException(
                    "R2 storage is not configured. Set R2_ENDPOINT, R2_BUCKET, " +
                    "R2_ACCESS_KEY_ID, and R2_SECRET_ACCESS_KEY environment variables.");
        }
    }
}
