package com.homekept.subscription;

import com.homekept.subscription.dto.AppPropertySummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing property portfolio (role: CUSTOMER).
 *
 * <p>Multi-property portfolio, Phase 1 (see docs/portfolio-multi-property-proposal.md): a
 * landlord/property-manager is just a CUSTOMER user who owns more than one
 * {@link Subscriber} — one per property, each independently billed. This is the one
 * endpoint that lists <em>every</em> property a user owns; every other {@code /api/app/*}
 * endpoint stays scoped to a single property at a time (optionally chosen via a
 * {@code propertyId} query parameter — see
 * {@link SubscriberQueryService#resolveOwnedSubscriber}).
 *
 * <p>The authenticated user's subscribers are resolved inside the service from the JWT
 * principal — the caller never supplies a user id (preventing IDOR).
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
public class AppPropertiesController {

    private final AppPropertiesService appPropertiesService;

    public AppPropertiesController(AppPropertiesService appPropertiesService) {
        this.appPropertiesService = appPropertiesService;
    }

    /**
     * GET /api/app/properties — one summary per property the authenticated user owns.
     *
     * <p>A user with no subscriber at all gets an empty array (not a 404) — "no
     * properties yet" is a valid state for this list endpoint.
     *
     * @param auth JWT principal — Long user id
     */
    @GetMapping("/api/app/properties")
    public ResponseEntity<List<AppPropertySummary>> listProperties(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(appPropertiesService.listProperties(userId));
    }
}
