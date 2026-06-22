package com.homekept.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A photo attached to a maintenance visit, stored in Cloudflare R2.
 *
 * <p>{@code storageKey} is the R2 object key, server-generated as
 * {@code visits/{visitId}/{uuid}} — the client never chooses arbitrary keys
 * (prevents path traversal and overwrite attacks). Signed download URLs are
 * generated on demand via {@link com.homekept.storage.StorageService#presignDownload}.
 *
 * <p>ON DELETE CASCADE from {@code visit} — deleting a visit removes its photos
 * (both the DB row and the R2 object; R2 cleanup is a founder follow-up / lifecycle
 * policy since the MVP scope is creation only).
 *
 * <p>No PII stored here — only the storage key (an opaque identifier), an optional
 * caption, and an optional taken_at timestamp.
 */
@Entity
@Table(name = "visit_photo")
public class VisitPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → visit.id. Cascade handled by DB (ON DELETE CASCADE). */
    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    /**
     * R2 object key, server-generated as {@code visits/{visitId}/{uuid}}.
     * NEVER set by the client — prevents path traversal.
     */
    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    /** Optional caption provided by the technician. */
    @Column(columnDefinition = "TEXT")
    private String caption;

    /** When the photo was taken on the technician's device (nullable). */
    @Column(name = "taken_at")
    private Instant takenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VisitPhoto() {}

    public VisitPhoto(Long visitId, String storageKey, String caption, Instant takenAt) {
        this.visitId = visitId;
        this.storageKey = storageKey;
        this.caption = caption;
        this.takenAt = takenAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getVisitId() { return visitId; }
    public String getStorageKey() { return storageKey; }
    public String getCaption() { return caption; }
    public Instant getTakenAt() { return takenAt; }
    public Instant getCreatedAt() { return createdAt; }
}
