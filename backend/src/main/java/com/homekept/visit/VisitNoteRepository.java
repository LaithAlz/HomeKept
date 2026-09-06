package com.homekept.visit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link VisitNote}.
 *
 * <p>Package-private — a visit's notes are read/written only through
 * {@link VisitNoteService} (shared by the admin and technician surfaces), never by another
 * domain reaching in directly.
 */
interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    /**
     * Newest-first notes for a single visit, for the admin console
     * ({@code GET /api/admin/visits/{id}/notes}) and the technician app
     * ({@code GET /api/tech/visits/{id}/notes}).
     *
     * @param visitId  the visit id
     * @param pageable page size cap (the service caps this — mirrors
     *                 {@code VisitEventRepository.findByVisitIdOrderByCreatedAtDesc})
     * @return notes ordered by {@code createdAt} descending
     */
    List<VisitNote> findByVisitIdOrderByCreatedAtDesc(Long visitId, Pageable pageable);
}
