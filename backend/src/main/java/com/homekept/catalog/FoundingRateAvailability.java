package com.homekept.catalog;

/**
 * Seam that answers: are there founding-rate slots still available (global cap: 15)?
 *
 * <p>The founding rate on a plan tier requires TWO conditions to both be true:
 * <ol>
 *   <li>The tier has a {@code founding_monthly_price_cents} seeded ({@link PlanTier#hasFoundingPrice()}).</li>
 *   <li>This method returns {@code true} — fewer than 15 founding subscribers exist.</li>
 * </ol>
 *
 * <p>The subscription slice (issue #55) replaces {@link DefaultFoundingRateAvailability}
 * with a real implementation that counts {@code subscriber.founding_rate = true} rows.
 * Until that domain exists this interface lives in {@code com.homekept.catalog} because
 * only the catalog response currently consumes it.
 */
public interface FoundingRateAvailability {

    /**
     * Returns {@code true} if there are founding-member slots remaining under the 15-subscriber cap.
     * Implementations must be cheap (no remote calls) — this is called per tier per plans request.
     */
    boolean foundingSlotsRemaining();
}
