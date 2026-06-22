package com.homekept.visit;

/**
 * Lifecycle status of a {@link Flag}.
 *
 * <ul>
 *   <li>{@code OPEN} — just created; not yet folded into a scheduled visit</li>
 *   <li>{@code SCHEDULED} — folded into an upcoming visit as a
 *       {@link VisitService}({@code source=FLAGGED}) row</li>
 *   <li>{@code RESOLVED} — addressed (completed at a visit)</li>
 *   <li>{@code REFERRED} — referred to a licensed trade partner</li>
 * </ul>
 */
public enum FlagStatus {
    OPEN,
    SCHEDULED,
    RESOLVED,
    REFERRED
}
