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
 * A threaded operational note on a visit, written by an admin OR the assigned technician
 * (V7's {@code visit_note} table — created by that migration but never wired up until this
 * slice). Mirrors {@code com.homekept.property.PropertyNote} deliberately — same shape, same
 * read/paginate/render rules — the two differ only in what they're about (one visit vs. the
 * standing property).
 *
 * <p>This is a log — many entries, each by an author, each about one visit — NOT the same
 * thing as {@code Visit#completionNotes}/{@code Visit#materialsNotes}, which are single
 * fields the technician fills in once at completion. A visit note can be added at any point
 * in the visit's lifecycle, by an admin or the assigned technician, and there can be any
 * number of them.
 *
 * <p>{@code authorUserId} is a bare {@code BIGINT} (no FK to {@code users} — see the V7
 * migration comment) and must always come from the authenticated principal, never from
 * request input.
 *
 * <h2>Access</h2>
 * <p>An admin may read/write any visit's notes. A technician may read/write notes ONLY on a
 * visit assigned to them ({@code Visit#technicianId} match) — see
 * {@code TechVisitService#requireOwnedVisit}. Ownership failures return 404, never 403.
 *
 * <p>ON DELETE CASCADE from {@code visit} — deleting a visit removes its notes. There is
 * deliberately no delete/edit operation on an individual note — see
 * {@link VisitNoteService#addNote}'s javadoc for why.
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

    /** The authenticated principal who wrote this note — NEVER taken from request input. */
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
