package com.homekept.visit;

/**
 * How a {@link VisitService} row was added to a visit checklist.
 *
 * <ul>
 *   <li>{@code TEMPLATE} — seeded from the visit template's standing items at scheduling time.</li>
 *   <li>{@code PICK} — customer chose this as an included pick (burns allowance).</li>
 *   <li>{@code EXTRA} — paid à la carte; never burns the included-picks allowance.</li>
 *   <li>{@code FLAGGED} — carried forward from an OPEN flag on a prior visit.</li>
 *   <li>{@code TODO} — customer to-do item from "your list" folded into this visit.</li>
 * </ul>
 */
public enum VisitServiceSource {
    TEMPLATE,
    PICK,
    EXTRA,
    FLAGGED,
    TODO
}
