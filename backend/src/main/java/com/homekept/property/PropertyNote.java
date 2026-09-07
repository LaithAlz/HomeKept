package com.homekept.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A threaded technician/admin note about a PROPERTY, kept for tracking across visits (V18's
 * {@code property_note} table). Mirrors {@code com.homekept.visit.VisitNote} deliberately —
 * same shape, same read/paginate/render rules.
 *
 * <p>Standing knowledge about the home the NEXT technician needs before they arrive,
 * whichever visit that is — "furnace filter sits behind the stairs", "back gate sticks, lift
 * while pushing" — as distinct from a {@code VisitNote}, which is about one attendance. See
 * the V18 migration's comment block for the full "why a table, why not a column, why not
 * just visit_note" reasoning.
 *
 * <p>{@code authorUserId} is a bare {@code BIGINT} (no FK to {@code users}, matching
 * {@code visit_note}'s own convention: deliberately no cross-domain hard FK) and must always
 * come from the authenticated principal, never from request input.
 *
 * <h2>Access</h2>
 * <p>An admin may read/write any property's notes. A technician may read/write notes ONLY on
 * a property they have a genuine visit assignment at — see
 * {@code TechVisitService#requirePropertyAccessibleToTechnician}. Ownership failures return
 * 404, never 403.
 *
 * <h2>This is NOT access_notes</h2>
 * <p>{@code property.access_notes} (V4) is encrypted BYTEA holding lockbox/alarm codes and
 * key locations, decrypted only server-side for the assigned technician. This table is
 * plaintext and is shown wherever the property is shown. Anything that would let a stranger
 * enter the house belongs in {@code access_notes}, never here.
 *
 * <p>ON DELETE CASCADE from {@code property} — deleting a property removes its notes. There
 * is deliberately no delete/edit operation on an individual note — see
 * {@link PropertyService#addNote}'s javadoc for why.
 */
@Entity
@Table(name = "property_note")
public class PropertyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → property.id. Cascade handled by DB (ON DELETE CASCADE). */
    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /** The authenticated principal who wrote this note — NEVER taken from request input. */
    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PropertyNote() {}

    public PropertyNote(Long propertyId, Long authorUserId, String body) {
        this.propertyId = propertyId;
        this.authorUserId = authorUserId;
        this.body = body;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
