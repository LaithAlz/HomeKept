package com.homekept.catalog;

/**
 * Picks classification for a service — maps to the {@code tier_class} CHECK constraint
 * in V2__catalog.sql and to the à la carte price bands in docs/pricing-and-visits.md:
 * BASIC $49 / MEDIUM $89 / PREMIUM $149.
 */
public enum TierClass {
    BASIC,
    MEDIUM,
    PREMIUM
}
