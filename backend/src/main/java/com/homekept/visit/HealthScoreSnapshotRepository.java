package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link HealthScoreSnapshot}.
 */
public interface HealthScoreSnapshotRepository extends JpaRepository<HealthScoreSnapshot, Long> {

    /** The most recent snapshot for a subscriber — the prior value for the dashboard delta. */
    Optional<HealthScoreSnapshot> findFirstBySubscriberIdOrderByComputedAtDesc(Long subscriberId);
}
