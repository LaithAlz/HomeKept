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
 * A single checklist item on a {@link Visit} — one service to be performed.
 *
 * <p>The {@code source} field records how this item was added:
 * {@code TEMPLATE} (standing item from the visit template),
 * {@code PICK} (customer's included pick — burns allowance),
 * {@code EXTRA} (paid à la carte — never burns allowance),
 * {@code FLAGGED} (carried forward from an open flag on a prior visit),
 * {@code TODO} (customer to-do item from "your list").
 *
 * <p>FK columns are bare BIGINT; service-layer code never crosses domain
 * boundaries via entity references.
 *
 * <p>The visit_service table has ON DELETE CASCADE from visit — so deleting a
 * visit removes its service rows. This is the only cascade in the schema.
 */
@Entity
@Table(name = "visit_service")
public class VisitService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → visit.id. Mapped as bare BIGINT for domain isolation. */
    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    /** FK → service.id (catalog domain). */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitServiceSource source;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "technician_notes", columnDefinition = "TEXT")
    private String technicianNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VisitService() {}

    public VisitService(Long visitId, Long serviceId, VisitServiceSource source) {
        this.visitId = visitId;
        this.serviceId = serviceId;
        this.source = source;
        this.completed = false;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getVisitId() { return visitId; }
    public Long getServiceId() { return serviceId; }
    public VisitServiceSource getSource() { return source; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getTechnicianNotes() { return technicianNotes; }
    public void setTechnicianNotes(String technicianNotes) { this.technicianNotes = technicianNotes; }
    public Instant getCreatedAt() { return createdAt; }
}
