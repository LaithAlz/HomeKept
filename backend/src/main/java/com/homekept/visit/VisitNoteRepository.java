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
 *
 * <p>Both list methods order by {@code createdAt DESC, id DESC} — {@code id} is a tiebreaker,
 * not a whim: two notes can share a {@code createdAt} microsecond, and without a tiebreaker
 * that means an arbitrary (and non-reproducible) order between them, which also makes cursor
 * pagination unsound (a stable cursor needs a total order to page on). {@code id} is
 * guaranteed unique, so appending it makes the order total. Served by
 * {@code idx_visit_note_visit_created} (V19); the WHERE clause for the cursor page still
 * filters on the simpler, PK-backed {@code id < :cursor} — see
 * {@link VisitNoteService}'s javadoc for why that is sound even though the display order is
 * the compound key.
 */
interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    /**
     * First page (no cursor): a visit's notes, newest first, for the admin console
     * ({@code GET /api/admin/visits/{id}/notes}) and the technician app
     * ({@code GET /api/tech/visits/{id}/notes}).
     *
     * @param visitId  the visit id
     * @param pageable page size (the service requests {@code limit + 1} to detect whether a
     *                 further page exists without a separate count query)
     * @return notes ordered by {@code createdAt} descending, {@code id} descending
     */
    List<VisitNote> findByVisitIdOrderByCreatedAtDescIdDesc(Long visitId, Pageable pageable);

    /**
     * Subsequent page: a visit's notes with {@code id} less than the previous page's cursor
     * (see this repository's class javadoc for why cursoring on {@code id} alone is
     * sound even though the display order includes {@code createdAt}).
     *
     * @param visitId  the visit id
     * @param cursor   exclusive upper bound on {@code id} (the previous page's last note id)
     * @param pageable page size (the service requests {@code limit + 1})
     * @return notes ordered by {@code createdAt} descending, {@code id} descending
     */
    List<VisitNote> findByVisitIdAndIdLessThanOrderByCreatedAtDescIdDesc(
            Long visitId, Long cursor, Pageable pageable);
}
