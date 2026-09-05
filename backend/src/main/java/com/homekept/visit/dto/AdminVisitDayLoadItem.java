package com.homekept.visit.dto;

/**
 * One row of {@code GET /api/admin/visits/day-load} — the admin Routes month-sidebar
 * aggregate. One entry per local calendar day (in the configured render zone) that has at
 * least one SCHEDULED visit; days with none are omitted entirely rather than sent as zero.
 *
 * <p>Deliberately honest-counts-only: {@code total} and {@code unassigned} are real counts
 * of SCHEDULED visits. There is no capacity/percentage/"slots free" field here and there
 * must never be one — the backend does not model technician working hours, so a fabricated
 * availability signal would be worse than none.
 *
 * @param day        local calendar date, "YYYY-MM-DD" (e.g. "2026-09-08")
 * @param total      count of SCHEDULED visits scheduled for this local day
 * @param unassigned count of those with no technician assigned ({@code technician_id IS NULL})
 */
public record AdminVisitDayLoadItem(String day, long total, long unassigned) {
}
