package com.homekept.visit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link VisitEvent}.
 *
 * <p>Package-private — a visit's activity log is read only through
 * {@link VisitAdminService#listEvents}, never by another domain reaching in directly.
 */
interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {

    /**
     * Newest-first activity log for a single visit, for the admin console
     * ({@code GET /api/admin/visits/{id}/events}).
     *
     * @param visitId  the visit id
     * @param pageable page size cap (the service caps this — mirrors
     *                 {@code SubscriptionEventRepository.findBySubscriberIdOrderByCreatedAtDesc})
     * @return events ordered by {@code createdAt} descending
     */
    List<VisitEvent> findByVisitIdOrderByCreatedAtDesc(Long visitId, Pageable pageable);
}
