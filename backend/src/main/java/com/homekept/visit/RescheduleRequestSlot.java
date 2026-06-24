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
 * One proposed time slot on a {@link RescheduleRequest} (1..N per request). Stored as rows
 * rather than a JSONB array per arch doc §3. {@code preferredSlot} is a specific proposed
 * start time (TIMESTAMPTZ / Instant, UTC stored, America/Toronto rendered).
 */
@Entity
@Table(name = "reschedule_request_slot")
public class RescheduleRequestSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → reschedule_request.id (bare column, per the FK-as-column convention). */
    @Column(name = "reschedule_request_id", nullable = false)
    private Long rescheduleRequestId;

    @Column(name = "preferred_slot", nullable = false)
    private Instant preferredSlot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RescheduleRequestSlot() {}

    public RescheduleRequestSlot(Long rescheduleRequestId, Instant preferredSlot) {
        this.rescheduleRequestId = rescheduleRequestId;
        this.preferredSlot = preferredSlot;
    }

    public Long getId() { return id; }
    public Long getRescheduleRequestId() { return rescheduleRequestId; }
    public Instant getPreferredSlot() { return preferredSlot; }
    public Instant getCreatedAt() { return createdAt; }
}
