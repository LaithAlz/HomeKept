package com.homekept.dashboard.dto;

/**
 * Aggregate metrics for the admin console home / operational dashboard (issue #43).
 *
 * <p>Every field is computed from existing data — nothing here is fabricated or stubbed.
 * See {@code AdminDashboardService.getDashboard()} for exactly how each is derived.
 *
 * <ul>
 *   <li>{@code activeSubscribers} — count of subscribers with status ACTIVE.</li>
 *   <li>{@code mrrCents} — integer cents; sum of the current monthly price (founding rate
 *       if applicable) across ACTIVE subscribers only.</li>
 *   <li>{@code pendingWalkthroughs} — count of walk-through bookings still PENDING
 *       (booked but not yet confirmed by admin).</li>
 *   <li>{@code upcomingVisits} — count of SCHEDULED visits with {@code scheduledFor} at
 *       or after now.</li>
 *   <li>{@code foundingRateSlotsRemaining} — founding-rate cap (15) minus the count of
 *       founding-rate subscribers; never negative.</li>
 * </ul>
 *
 * <p>Deliberately omitted (would require data that does not exist yet): an "at-risk
 * subscribers" count — there is no {@code SubscriberStatus.AT_RISK} (or equivalent) value
 * to count; PAYMENT_ISSUE is a distinct, already-countable status but is not the same
 * concept the mock UI used, so it is not included here to avoid inventing a metric.
 */
public record AdminDashboardResponse(
        long activeSubscribers,
        int mrrCents,
        long pendingWalkthroughs,
        long upcomingVisits,
        long foundingRateSlotsRemaining
) {}
