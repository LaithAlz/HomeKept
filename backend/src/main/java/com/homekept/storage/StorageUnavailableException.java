package com.homekept.storage;

/**
 * Thrown when R2 storage is not configured (missing endpoint, bucket, or credentials).
 *
 * <p>Maps to HTTP 503 Service Unavailable. The app does not hard-fail on startup when
 * R2 credentials are absent — dev and test environments work without real credentials.
 * Callers that need storage will receive this exception and surface a clean 503 to the
 * client rather than an NPE.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }
}
