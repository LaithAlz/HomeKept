package com.homekept.subscription.dto;

/**
 * Minimal property summary embedded in {@link AdminSubscriberDetail}.
 *
 * <p>Never includes decrypted access notes — only the {@code hasAccessNotes} boolean flag.
 *
 * <p>{@code propertyId} lets the admin console target
 * {@code PATCH /api/admin/properties/{propertyId}/sku} for the SKU sheet fields.
 * The SKU fields ({@code hvacFilterSizes}, {@code smokeCoDetectorModels},
 * {@code humidifierModel}, {@code waterHeaterAgeYears}, {@code waterHeaterFlushEligible})
 * are technician-prep data captured by the walk-through and refined over subsequent
 * visits; all are {@code null} until an admin (or a later technician-facing slice) sets them.
 */
public record AdminSubscriberPropertySummary(
        Long propertyId,
        String streetAddress,
        String city,
        String postalCode,
        String propertyType,
        boolean hasAccessNotes,
        String hvacFilterSizes,
        String smokeCoDetectorModels,
        String humidifierModel,
        Integer waterHeaterAgeYears,
        Boolean waterHeaterFlushEligible
) {}
