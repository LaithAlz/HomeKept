package com.homekept.visit;

/**
 * Severity of a {@link Flag} raised by a technician.
 *
 * <ul>
 *   <li>{@code INFO} — informational observation; customer is aware, no action required</li>
 *   <li>{@code ATTENTION} — needs attention at the next scheduled visit</li>
 *   <li>{@code URGENT} — act soon; may trigger an out-of-cycle visit or referral</li>
 * </ul>
 */
public enum FlagSeverity {
    INFO,
    ATTENTION,
    URGENT
}
