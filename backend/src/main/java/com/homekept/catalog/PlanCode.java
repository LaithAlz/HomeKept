package com.homekept.catalog;

/**
 * Plan tier codes — maps to the {@code code} CHECK constraint in V2__catalog.sql, as
 * amended by V12__remove_essential_and_founding.sql (which retired ESSENTIAL) and then
 * by V15__restore_essential_tier.sql (which reinstated it at the founder's request,
 * $89/mo · 4 visits, unchanged from its original V2 definition).
 *
 * <p>Declared cheapest-first: the ordinal is the tier ladder, so ESSENTIAL &lt; COMPLETE
 * &lt; PREMIER. {@code visit_template.min_tier} relies on that ordering being a floor
 * rather than an exclusive assignment — a template at ESSENTIAL applies to Essential and
 * every tier above it.
 */
public enum PlanCode {
    ESSENTIAL,
    COMPLETE,
    PREMIER
}
