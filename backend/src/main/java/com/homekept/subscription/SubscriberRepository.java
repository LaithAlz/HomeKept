package com.homekept.subscription;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Subscriber}.
 */
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    /** Count founding subscribers — used by the founding-rate availability check. */
    long countByFoundingRateTrue();

    /** Cursor-paginated list for the admin console (newest first). */
    List<Subscriber> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    /** First page (no cursor) for the admin console (newest first). */
    List<Subscriber> findAllByOrderByIdDesc(Pageable pageable);

    /** Find by user id — each user has at most one active subscriber at a time. */
    Optional<Subscriber> findByUserId(Long userId);
}
