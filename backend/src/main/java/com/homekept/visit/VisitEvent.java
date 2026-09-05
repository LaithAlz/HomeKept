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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Append-only per-visit activity log (V14 migration) — mirrors
 * {@link com.homekept.subscription.SubscriptionEvent} deliberately (same
 * id / entity_id / event_type / payload / source / created_at shape).
 *
 * <p>Replaces the old "reschedule creates a new SCHEDULED visit" model (former arch doc
 * §4.2): a visit is now rescheduled in place, and the before/after plus every other
 * lifecycle action (technician assigned/changed, cancelled) is recorded here instead, so
 * the admin visit list stays one row per real visit and the history moves to the visit's
 * own detail view. See {@link VisitAdminService} for the event-recording call sites.
 *
 * <p>{@code payload} is JSONB — one of the JSONB columns allowed in the schema (arch doc
 * §3), holding the event's specifics, e.g. for {@code RESCHEDULED}:
 * {@code {"from": "...", "to": "..."}}. {@code eventType} is intentionally NOT an enum —
 * matching {@code SubscriptionEvent.eventType} — so a new kind of event never needs a
 * migration.
 *
 * <p>{@code byUserId} is the acting user (the admin who clicked Reschedule, the technician
 * who completed the visit, or — for a reschedule that fulfills a customer's reschedule
 * request — the customer whose request it was, even though an admin executed the confirm).
 * {@code null} for {@code SYSTEM} events.
 */
@Entity
@Table(name = "visit_event")
public class VisitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSONB payload — event-specific detail. Null when an event carries no extra data. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String payload;

    /** FK → users.id. Null for SYSTEM events (ON DELETE SET NULL in the DB). */
    @Column(name = "by_user_id")
    private Long byUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitEventSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VisitEvent() {}

    public VisitEvent(Long visitId, String eventType, String payload, Long byUserId, VisitEventSource source) {
        this.visitId = visitId;
        this.eventType = eventType;
        this.payload = payload;
        this.byUserId = byUserId;
        this.source = source;
    }

    public Long getId() { return id; }
    public Long getVisitId() { return visitId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Long getByUserId() { return byUserId; }
    public VisitEventSource getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
