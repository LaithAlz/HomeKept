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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A scheduled or historical maintenance visit for a subscriber's property.
 *
 * <p>This is the most-touched entity in the system — every paying subscriber generates
 * 4-24 rows per year.
 *
 * <p>Status transitions are enforced by {@link VisitStateMachine}. No code may write
 * {@code status} without first verifying the transition is legal.
 *
 * <p>Cross-domain FK columns ({@code subscriberId}, {@code propertyId},
 * {@code technicianId}, {@code visitTemplateId}) are stored as bare {@code BIGINT}
 * columns. Service-layer code crosses domain boundaries via service interfaces only.
 *
 * <p>{@code materialsCostCents} is integer cents — never float. Filled at completion.
 *
 * <p>See arch doc §2.6 and §4.2.
 */
@Entity
@Table(name = "visit")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → subscriber.id (subscription domain). */
    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    /** FK → property.id (property domain). Denormalized for query speed. */
    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /**
     * FK → users.id (technician role). Nullable until admin assigns a technician.
     * No DB FK yet — technician slice not built. Stored as raw BIGINT.
     */
    @Column(name = "technician_id")
    private Long technicianId;

    /**
     * FK → visit_template.id. Nullable — null for EXTRA / WARRANTY / WALKTHROUGH visits
     * that were not generated from a template.
     */
    @Column(name = "visit_template_id")
    private Long visitTemplateId;

    /** When the visit is scheduled to happen (UTC). Admin adjusts; not the subscriber. */
    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    /** Expected duration in minutes (set at creation; defaults to 120). */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Actual duration in minutes — filled at completion. */
    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    /**
     * Materials cost in integer cents — filled at completion.
     * At-cost materials used during the visit (see docs/pricing-and-visits.md §Materials).
     * Never a float.
     */
    @Column(name = "materials_cost_cents")
    private Integer materialsCostCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitType type;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Visit() {}

    public Visit(Long subscriberId, Long propertyId, Long visitTemplateId,
                 Instant scheduledFor, int durationMinutes, VisitType type) {
        this.subscriberId = subscriberId;
        this.propertyId = propertyId;
        this.visitTemplateId = visitTemplateId;
        this.scheduledFor = scheduledFor;
        this.durationMinutes = durationMinutes;
        this.type = type;
        this.status = VisitStatus.SCHEDULED;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public Long getPropertyId() { return propertyId; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public Long getVisitTemplateId() { return visitTemplateId; }
    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getActualDurationMinutes() { return actualDurationMinutes; }
    public void setActualDurationMinutes(Integer actualDurationMinutes) { this.actualDurationMinutes = actualDurationMinutes; }
    public Integer getMaterialsCostCents() { return materialsCostCents; }
    public void setMaterialsCostCents(Integer materialsCostCents) { this.materialsCostCents = materialsCostCents; }
    public VisitStatus getStatus() { return status; }

    /**
     * Sets the visit status. Callers MUST verify the transition with
     * {@link VisitStateMachine#canTransition} before calling this setter.
     */
    public void setStatus(VisitStatus status) { this.status = status; }

    public VisitType getType() { return type; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
