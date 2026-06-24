package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link Flag}.
 */
public interface FlagRepository extends JpaRepository<Flag, Long> {

    /** Returns all flags for a subscriber, newest first. */
    List<Flag> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId);

    /** Returns all flags for a subscriber with a given status. */
    List<Flag> findBySubscriberIdAndStatusOrderByCreatedAtDesc(Long subscriberId, FlagStatus status);
}
