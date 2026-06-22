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
 * A free-text note left on a visit by a technician or admin.
 *
 * <p>ON DELETE CASCADE from {@code visit} — deleting a visit removes its notes.
 * {@code authorUserId} is a bare BIGINT (no JPA FK across domain boundaries).
 *
 * <p>Notes are not PII-bearing by themselves (no names, no access codes), but
 * technicians may write free text so the body must not appear in logs.
 */
@Entity
@Table(name = "visit_note")
public class VisitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → visit.id. Cascade handled by DB (ON DELETE CASCADE). */
    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    /** ID of the user who wrote the note (bare BIGINT — no cross-domain FK). */
    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VisitNote() {}

    public VisitNote(Long visitId, Long authorUserId, String body) {
        this.visitId = visitId;
        this.authorUserId = authorUserId;
        this.body = body;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getVisitId() { return visitId; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
