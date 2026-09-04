package com.homekept.catalog;

/**
 * Plan tier codes — maps to the {@code code} CHECK constraint in V2__catalog.sql (as
 * amended by V11__remove_essential_and_founding.sql, which retired ESSENTIAL and made
 * COMPLETE the base tier).
 */
public enum PlanCode {
    COMPLETE,
    PREMIER
}
