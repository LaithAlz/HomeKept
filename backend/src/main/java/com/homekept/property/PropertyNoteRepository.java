package com.homekept.property;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link PropertyNote}.
 *
 * <p>Package-private — a property's notes are read/written only through
 * {@link PropertyService}, never by another domain (or the technician-facing caller in the
 * visit domain) reaching in directly.
 *
 * <p>Both list methods order by {@code createdAt DESC, id DESC} — {@code id} is a tiebreaker,
 * not a whim: two notes can share a {@code createdAt} microsecond, and without a tiebreaker
 * that means an arbitrary (and non-reproducible) order between them, which also makes cursor
 * pagination unsound (a stable cursor needs a total order to page on). {@code id} is
 * guaranteed unique, so appending it makes the order total. Served by the
 * {@code (property_id, created_at DESC)} index the V18 migration creates; the WHERE clause
 * for the cursor page still filters on the simpler, PK-backed {@code id < :cursor} — see
 * {@code VisitNoteRepository}'s matching javadoc for why that is sound even though the
 * display order is the compound key.
 */
interface PropertyNoteRepository extends JpaRepository<PropertyNote, Long> {

    /**
     * First page (no cursor): a property's notes, newest first, for the admin console
     * ({@code GET /api/admin/properties/{propertyId}/notes}) and the technician app
     * ({@code GET /api/tech/properties/{propertyId}/notes}).
     *
     * @param propertyId the property id
     * @param pageable   page size (the service requests {@code limit + 1} to detect whether
     *                   a further page exists without a separate count query)
     * @return notes ordered by {@code createdAt} descending, {@code id} descending
     */
    List<PropertyNote> findByPropertyIdOrderByCreatedAtDescIdDesc(Long propertyId, Pageable pageable);

    /**
     * Subsequent page: a property's notes with {@code id} less than the previous page's
     * cursor (see this repository's class javadoc for why cursoring on {@code id} alone is
     * sound even though the display order includes {@code createdAt}).
     *
     * @param propertyId the property id
     * @param cursor     exclusive upper bound on {@code id} (the previous page's last note id)
     * @param pageable   page size (the service requests {@code limit + 1})
     * @return notes ordered by {@code createdAt} descending, {@code id} descending
     */
    List<PropertyNote> findByPropertyIdAndIdLessThanOrderByCreatedAtDescIdDesc(
            Long propertyId, Long cursor, Pageable pageable);
}
