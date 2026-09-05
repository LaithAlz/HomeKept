package com.homekept.visit.dto;

/**
 * The property address embedded in {@link AdminVisitDetail} — "where" the visit happens.
 * Deliberately just the address fields (never access notes, which are decrypted nowhere
 * except the technician day-sheet — see {@code PropertyService#decryptAccessNotes}).
 */
public record AdminVisitPropertySummary(
        Long propertyId,
        String streetAddress,
        String unit,      // nullable
        String city,
        String postalCode
) {}
