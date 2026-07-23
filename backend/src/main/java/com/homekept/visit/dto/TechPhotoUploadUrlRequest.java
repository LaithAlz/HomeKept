package com.homekept.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/tech/visits/{id}/photos/upload-url}.
 *
 * <p>The content type is validated server-side to be an image MIME type.
 *
 * <p>{@code contentLength} is the exact byte size of the file the client is about to
 * upload. It is (1) capped here at {@link #MAX_UPLOAD_BYTES} so an oversized request is
 * rejected with 400 before R2 is ever touched, and (2) signed into the presigned PUT URL
 * (see {@code R2StorageService#presignUpload}) so R2 itself rejects a body whose size does
 * not match — a client that lies about its size, or streams more bytes than it declared,
 * cannot slip a larger object past the cap. This closes the insider cost-DoS where a
 * technician could PUT arbitrarily large objects under the {@code visits/} prefix.
 */
public record TechPhotoUploadUrlRequest(
        @NotBlank String contentType,

        @NotNull
        @Min(value = 1, message = "contentLength must be at least 1 byte")
        @Max(value = MAX_UPLOAD_BYTES, message = "Photo exceeds the 25 MB upload limit")
        Long contentLength
) {
    /** Hard cap on a single photo upload: 25 MB. Modern phone photos are 2-8 MB. */
    public static final long MAX_UPLOAD_BYTES = 26_214_400L;
}
