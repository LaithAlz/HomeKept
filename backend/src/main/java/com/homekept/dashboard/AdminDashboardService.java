package com.homekept.dashboard;

import com.homekept.booking.BookingService;
import com.homekept.dashboard.dto.AdminDashboardResponse;
import com.homekept.subscription.SubscriptionAdminService;
import com.homekept.visit.VisitAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the admin dashboard aggregate ({@code GET /api/admin/dashboard}, issue #43)
 * from the other domains' own admin services.
 *
 * <h2>Domain boundaries</h2>
 * <p>This service calls {@link SubscriptionAdminService}, {@link BookingService}, and
 * {@link VisitAdminService} — each domain's own service — and never touches their
 * repositories or entities. It owns no data of its own.
 */
@Service
public class AdminDashboardService {

    private final SubscriptionAdminService subscriptionAdminService;
    private final BookingService bookingService;
    private final VisitAdminService visitAdminService;

    public AdminDashboardService(SubscriptionAdminService subscriptionAdminService,
                                 BookingService bookingService,
                                 VisitAdminService visitAdminService) {
        this.subscriptionAdminService = subscriptionAdminService;
        this.bookingService = bookingService;
        this.visitAdminService = visitAdminService;
    }

    /**
     * Returns the aggregate metrics. See {@link AdminDashboardResponse} for how each
     * field is computed.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        SubscriptionAdminService.SubscriptionMetrics subMetrics = subscriptionAdminService.getDashboardMetrics();
        long pendingWalkthroughs = bookingService.countPendingWalkthroughs();
        long upcomingVisits = visitAdminService.countUpcomingVisits();

        return new AdminDashboardResponse(
                subMetrics.activeSubscribers(),
                subMetrics.mrrCents(),
                pendingWalkthroughs,
                upcomingVisits,
                subMetrics.foundingRateSlotsRemaining()
        );
    }
}
