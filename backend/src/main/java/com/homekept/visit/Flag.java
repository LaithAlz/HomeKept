package com.homekept.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A persistent observation flag from the technician's "observe → photograph → flag → refer" loop.
 *
 * <p>OPEN flags fold into the next scheduled visit as {@code VisitService(source=FLAGGED)}
 * rows, and feed the Home Health Score's {@code flagged} list.
 *
 * <h2>Severity</h2>
 * <ul>
 *   <li>{@code INFO} — informational observation, no action required</li>
 *   <li>{@code ATTENTION} — needs attention at the next visit</li>
 *   <li>{@code URGENT} — act soon (may trigger an out-of-cycle visit)</li>
 * </ul>
 *
 * <h2>Status</h2>
 * <ul>
 *   <li>{@code OPEN} — just created; not yet folded into a visit</li>
 *   <li>{@code SCHEDULED} — folded into a future visit</li>
 *   <li>{@code RESOLVED} — addressed</li>
 *   <li>{@code REFERRED} — referred to a licensed trade</li>
 * </ul>
 *
 * <p>Cross-domain FKs ({@code subscriberId}, {@code originVisitId}) are bare BIGINTs;
 * the service layer never crosses domain boundaries via entity references.
 */
@Entity
@Table(name = "flag")
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → subscriber.id (subscription domain — bare BIGINT). */
    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    /** FK → visit.id. Nullable — flags can exist outside a specific visit context. */
    @Column(name = "origin_visit_id")
    private Long originVisitId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagStatus status;

    /**
     * Optional R2 storage key for a photo attached to the flag.
     * Server-generated key scheme {@code visits/{visitId}/{uuid}} — same as {@link VisitPhoto}.
     */
    @Column(name = "photo_storage_key", columnDefinition = "TEXT")
    private String photoStorageKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Flag() {}

    public Flag(Long subscriberId, Long originVisitId, String body, FlagSeverity severity) {
        this.subscriberId = subscriberId;
        this.originVisitId = originVisitId;
        this.body = body;
        this.severity = severity;
        this.status = FlagStatus.OPEN;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public Long getOriginVisitId() { return originVisitId; }
    public String getBody() { return body; }
    public FlagSeverity getSeverity() { return severity; }
    public FlagStatus getStatus() { return status; }
    public void setStatus(FlagStatus status) { this.status = status; }
    public String getPhotoStorageKey() { return photoStorageKey; }
    public void setPhotoStorageKey(String photoStorageKey) { this.photoStorageKey = photoStorageKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
