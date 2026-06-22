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
 * A customer "your list" item — a maintenance task submitted by the subscriber
 * to be carried out at an upcoming visit.
 *
 * <h2>Status</h2>
 * <ul>
 *   <li>{@code OPEN} — submitted; not yet folded into a scheduled visit</li>
 *   <li>{@code SCHEDULED} — folded into an upcoming visit as a
 *       {@link VisitService}({@code source=TODO}) row</li>
 *   <li>{@code DONE} — completed by the technician during a visit</li>
 *   <li>{@code DECLINED} — technician couldn't or shouldn't do it;
 *       {@code declineNote} explains why</li>
 * </ul>
 *
 * <p>Cross-domain FKs ({@code subscriberId}, {@code visitId}) are bare BIGINTs
 * per the domain-boundary rule.
 */
@Entity
@Table(name = "todo_item")
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → subscriber.id (subscription domain — bare BIGINT). */
    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoItemStatus status;

    /**
     * Set when the item is folded into a visit ({@code status = SCHEDULED}).
     * FK → visit.id (bare BIGINT).
     */
    @Column(name = "visit_id")
    private Long visitId;

    /** Technician's explanation when {@code status = DECLINED}. Nullable. */
    @Column(name = "decline_note", columnDefinition = "TEXT")
    private String declineNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TodoItem() {}

    public TodoItem(Long subscriberId, String body) {
        this.subscriberId = subscriberId;
        this.body = body;
        this.status = TodoItemStatus.OPEN;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public String getBody() { return body; }
    public TodoItemStatus getStatus() { return status; }
    public void setStatus(TodoItemStatus status) { this.status = status; }
    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }
    public String getDeclineNote() { return declineNote; }
    public void setDeclineNote(String declineNote) { this.declineNote = declineNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
