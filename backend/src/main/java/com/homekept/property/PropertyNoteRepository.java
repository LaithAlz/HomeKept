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
 */
interface PropertyNoteRepository extends JpaRepository<PropertyNote, Long> {

    /**
     * Newest-first notes for a single property, for the admin console
     * ({@code GET /api/admin/properties/{propertyId}/notes}) and the technician app
     * ({@code GET /api/tech/properties/{propertyId}/notes}). Matches the
     * {@code (property_id, created_at DESC)} index the V18 migration creates for exactly
     * this read pattern.
     *
     * @param propertyId the property id
     * @param pageable   page size cap (the service caps this)
     * @return notes ordered by {@code createdAt} descending
     */
    List<PropertyNote> findByPropertyIdOrderByCreatedAtDesc(Long propertyId, Pageable pageable);
}
