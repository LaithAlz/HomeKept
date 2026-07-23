package com.homekept.storage;

/**
 * Abstraction over file storage (Cloudflare R2, S3-compatible).
 *
 * <p>The backend never serves files directly — it generates signed URLs and the client
 * interacts with R2 directly. This keeps large binary payloads off the Spring Boot
 * instance at scale (arch doc §6.4).
 *
 * <p>Upload flow: caller requests a signed PUT URL → client uploads directly to R2 →
 * client confirms the upload by calling the confirm endpoint.
 *
 * <p>Download flow: backend generates a signed GET URL → client fetches from R2.
 *
 * <p>Storage keys are ALWAYS server-generated (prevents path traversal / overwrite attacks).
 * The scheme is {@code visits/{visitId}/{uuid}} for visit photos.
 */
public interface StorageService {

    /**
     * Generates a 15-minute signed PUT URL for direct client upload to R2.
     *
     * <p>The {@code contentLength} is bound into the signature, so R2 rejects any upload
     * whose body size does not match exactly. The caller must have already validated it
     * against the size cap — signing it here is defense-in-depth against a client that
     * lies about, or overshoots, its declared size.
     *
     * @param storageKey    the server-generated R2 object key (e.g. {@code visits/42/uuid-v4})
     * @param contentType   the MIME type of the object being uploaded (e.g. {@code image/jpeg})
     * @param contentLength the exact byte size of the object, signed into the PUT URL
     * @return a {@link PresignedUpload} containing the upload URL and the storage key
     * @throws StorageUnavailableException if R2 is not configured (dev/test without credentials)
     */
    PresignedUpload presignUpload(String storageKey, String contentType, long contentLength);

    /**
     * Generates a 15-minute signed GET URL for a stored object.
     *
     * @param storageKey the R2 object key
     * @return the signed GET URL string
     * @throws StorageUnavailableException if R2 is not configured
     */
    String presignDownload(String storageKey);

    /**
     * The result of a presign-upload request: the signed PUT URL the client should upload
     * to, and the storage key that must be confirmed after upload.
     */
    record PresignedUpload(String uploadUrl, String storageKey) {}
}
