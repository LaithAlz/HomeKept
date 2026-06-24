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
 * A customer's request to reschedule an existing visit (#54). The proposed times live in
 * {@link RescheduleRequestSlot} child rows (one-to-many via a bare {@code reschedule_request_id}
 * column — no JPA relationship, per the codebase's domain-first, FK-as-column convention).
 *
 * <p>An admin confirms (rescheduling the visit via the visit state machine, which creates
 * the replacement visit recorded in {@code confirmedVisitId}) or declines with a note.
 *
 * <p>A partial unique index ({@code idx_reschedule_request_pending_visit}) enforces at most
 * one PENDING request per visit at the DB level — the service inserts without a pre-check
 * and treats a {@code DataIntegrityViolationException} as a 409.
 */
@Entity
@Table(name = "reschedule_request")
public class RescheduleRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → visit.id — the visit the customer wants moved. */
    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    /** FK → subscriber.id — denormalized for ownership scoping (the 404 rule). */
    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RescheduleRequestStatus status;

    /** Admin's note on confirm/decline. Null while PENDING. */
    @Column(name = "admin_note")
    private String adminNote;

    /** FK → visit.id of the replacement visit created on confirm. Null while PENDING/DECLINED. */
    @Column(name = "confirmed_visit_id")
    private Long confirmedVisitId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RescheduleRequest() {}

    /** Creates a PENDING request for the given visit/subscriber. */
    public RescheduleRequest(Long visitId, Long subscriberId) {
        this.visitId = visitId;
        this.subscriberId = subscriberId;
        this.status = RescheduleRequestStatus.PENDING;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getVisitId() { return visitId; }
    public Long getSubscriberId() { return subscriberId; }
    public RescheduleRequestStatus getStatus() { return status; }
    public void setStatus(RescheduleRequestStatus status) { this.status = status; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public Long getConfirmedVisitId() { return confirmedVisitId; }
    public void setConfirmedVisitId(Long confirmedVisitId) { this.confirmedVisitId = confirmedVisitId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
