package com.homekept.visit;

/**
 * Type of a {@link Visit}.
 *
 * <ul>
 *   <li>{@code ROUTINE} — part of the subscriber's plan cadence, generated from a template.</li>
 *   <li>{@code EXTRA} — subscriber-requested add-on (paid à la carte).</li>
 *   <li>{@code WARRANTY} — re-do of a recent visit; no extra charge.</li>
 *   <li>{@code WALKTHROUGH} — the pre-subscription assessment; shares this table with production visits.</li>
 * </ul>
 *
 * <p>See arch doc §2.6.
 */
public enum VisitType {
    ROUTINE,
    EXTRA,
    WARRANTY,
    WALKTHROUGH
}
