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
import java.util.Map;

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
     * The FK constraint (fk_visit_technician) is added by the V7 migration.
     */
    @Column(name = "technician_id")
    private Long technicianId;

    /**
     * FK → visit_template.id. Nullable — null for EXTRA / WARRANTY / WALKTHROUGH visits
     * that were not generated from a template.
     */
    @Column(name = "visit_template_id")
    private Long visitTemplateId;

    /**
     * Which yearly occurrence of {@link #visitTemplateId} this visit is (V17 migration).
     *
     * <p>Set by whichever path created the visit — see
     * {@link VisitSchedulingService#scheduleInitialVisits} for the template-driven case, and
     * {@link TechVisitService#incompleteVisit} for the INCOMPLETE follow-up, which inherits
     * the SAME value as the visit it replaces (a follow-up IS the same occurrence, not a new
     * one). Once a row HAS a value, a reschedule (see {@code VisitAdminService}) MUST NEVER
     * move it: it identifies which occurrence the row is, not where it currently sits on the
     * calendar.
     *
     * <p><strong>V17 does not backfill.</strong> A migration cannot verify that no pre-V16
     * reschedule ever landed a replacement visit's {@code scheduledFor} in a different
     * calendar year than the occurrence it actually was (pre-V16 reschedule created a new row
     * carrying the same template at a new date, with nothing constraining that date to the
     * same year) — guessing a year from {@code scheduledFor} at backfill time risked stamping
     * the WRONG year and silently suppressing a real future occurrence forever, which is
     * worse than the bug this column fixes. So instead:
     * <ul>
     *   <li>Every row that predates this column starts {@code null}.</li>
     *   <li>A {@code null}, templated row gets its year assigned LAZILY, exactly once, the
     *       first time it is rescheduled in place — computed from wherever {@code
     *       scheduledFor} currently sits (the last instant that's still guaranteed to be the
     *       true occurrence, since the row has never been moved before) — see
     *       {@code VisitAdminService#rescheduleInternal}. After that assignment the row
     *       behaves exactly like a new one: never moved again.</li>
     *   <li>Until then, {@code VisitRepository}'s idempotency guard falls back to the pre-V16
     *       window rule for {@code null}-year rows, which is correct for a row that (by
     *       construction) has never been moved in place — see
     *       {@code VisitRepository#existsAlreadyScheduledForOccurrence}.</li>
     * </ul>
     *
     * <p>{@code null} also for every visit with no template ({@code visitTemplateId == null},
     * e.g. admin-created via {@code POST /api/admin/visits}) — there is no occurrence to
     * record, and the guard is keyed on {@code visitTemplateId} too, so such a row is never a
     * candidate match regardless.
     */
    @Column(name = "template_occurrence_year")
    private Integer templateOccurrenceYear;

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

    /** Optional description of materials used during this visit. Filled at completion. */
    @Column(name = "materials_notes", columnDefinition = "TEXT")
    private String materialsNotes;

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

    public Integer getTemplateOccurrenceYear() { return templateOccurrenceYear; }

    /**
     * Sets which yearly occurrence of the template this visit is. Callers: a visit creation
     * path (the scheduler, the INCOMPLETE follow-up path, or a test fixture simulating one),
     * OR a reschedule assigning a legacy ({@code null}-year) row's occurrence for the first
     * time, from where it currently sits, immediately before moving it. Once a row has a
     * non-null value, NEVER call this to change it — see this field's javadoc for why.
     */
    public void setTemplateOccurrenceYear(Integer templateOccurrenceYear) {
        this.templateOccurrenceYear = templateOccurrenceYear;
    }

    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public int getDurationMinutes() { return durationMinutes; }
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
    public String getMaterialsNotes() { return materialsNotes; }
    public void setMaterialsNotes(String materialsNotes) { this.materialsNotes = materialsNotes; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Resolves the human-readable name shown to both the customer (app) and the
     * technician (day sheet) — the single source of truth for "what this visit is called"
     * so the two views can never drift.
     *
     * <ul>
     *   <li>Template-driven visits (ROUTINE with {@code visitTemplateId} set): the
     *       template's {@code name} (e.g. "Fall winterization" — see the visit calendar
     *       in docs/pricing-and-visits.md).</li>
     *   <li>EXTRA visits (à-la-carte add-ons): "Extra visit".</li>
     *   <li>WALKTHROUGH visits: "Walk-through".</li>
     *   <li>WARRANTY visits: "Warranty visit".</li>
     *   <li>Any other ROUTINE with no template: "Routine visit" (admin-created).</li>
     * </ul>
     *
     * @param templateNames a pre-loaded map of {@code visitTemplateId} → template name.
     *                      Callers batch-load this for a page of visits to avoid an N+1
     *                      query per visit; a visit with no matching entry falls back to
     *                      the type-based name below.
     */
    public String resolveDisplayName(Map<Long, String> templateNames) {
        if (visitTemplateId != null) {
            String name = templateNames.get(visitTemplateId);
            if (name != null) {
                return name;
            }
        }
        return switch (type) {
            case EXTRA -> "Extra visit";
            case WALKTHROUGH -> "Walk-through";
            case WARRANTY -> "Warranty visit";
            case ROUTINE -> "Routine visit";
        };
    }
}
