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
 * A point-in-time Home Health Score for a subscriber, written once per completed visit (#53).
 *
 * <p>The score itself is computed on read by {@link HealthScoreService}; these snapshots exist
 * only so the dashboard can show a delta against the value at the previous visit. Disposable
 * derived history (subscriber FK is ON DELETE CASCADE).
 */
@Entity
@Table(name = "health_score_snapshot")
public class HealthScoreSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    /** 0..100, computed at the moment the triggering visit was completed. */
    @Column(nullable = false)
    private int score;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    protected HealthScoreSnapshot() {}

    public HealthScoreSnapshot(Long subscriberId, int score) {
        this.subscriberId = subscriberId;
        this.score = score;
    }

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public int getScore() { return score; }
    public Instant getComputedAt() { return computedAt; }
}
