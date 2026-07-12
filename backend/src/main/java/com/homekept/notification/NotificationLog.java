package com.homekept.notification;

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
 * The reminder-send dedupe ledger (#89), mapped to the V10 migration's {@code notification_log}
 * table.
 *
 * <p>Every reminder {@link ReminderScheduler} sends is recorded here first, via
 * {@link NotificationLogService#recordIfFirst}. The table's {@code UNIQUE (notification_type,
 * target_type, target_id)} constraint guarantees a given reminder for a given entity is
 * recorded at most once, even across app restarts, overlapping scheduler ticks, or multiple
 * app instances — {@code recordIfFirst} inserts blind and treats a unique-violation as
 * "already sent, skip" (the same insert-blind pattern {@code RescheduleService.createRequest}
 * uses for the PENDING-reschedule partial-unique-index guard).
 *
 * <p>Append-only — there is no status to transition, so this entity has no state machine and
 * no setters beyond construction.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 48)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private NotificationTargetType targetType;

    /** Bare id — points at different parent tables depending on {@link #targetType}; no FK. */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected NotificationLog() {}

    public NotificationLog(NotificationType notificationType, NotificationTargetType targetType, Long targetId) {
        this.notificationType = notificationType;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public NotificationType getNotificationType() { return notificationType; }
    public NotificationTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Instant getSentAt() { return sentAt; }
}
