package com.homekept.visit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link VisitService}.
 */
public interface VisitServiceRepository extends JpaRepository<VisitService, Long> {

    /** Returns all checklist items for a visit, ordered for display. */
    List<VisitService> findByVisitIdOrderByIdAsc(Long visitId);

    /** Batch equivalent of {@link #findByVisitIdOrderByIdAsc} — one query for a page of visits. */
    List<VisitService> findByVisitIdInOrderByIdAsc(Collection<Long> visitIds);
}
