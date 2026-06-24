package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link TodoItem}.
 */
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    /** Returns all todo items for a subscriber, newest first. */
    List<TodoItem> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId);

    /**
     * Returns todo items for a subscriber with a given status.
     * Used to fold OPEN items into scheduled visits.
     */
    List<TodoItem> findBySubscriberIdAndStatusOrderByCreatedAtAsc(Long subscriberId, TodoItemStatus status);

    /**
     * Returns OPEN or SCHEDULED todo items for a subscriber that are linked to a visit.
     * Used for the technician day-sheet (todos to be worked on during the visit).
     */
    List<TodoItem> findByVisitId(Long visitId);
}
