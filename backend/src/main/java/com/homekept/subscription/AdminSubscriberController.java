package com.homekept.subscription;

import com.homekept.subscription.dto.AdminCancelSubscriptionRequest;
import com.homekept.subscription.dto.AdminSubscriberDetail;
import com.homekept.subscription.dto.AdminSubscriberListItem;
import com.homekept.subscription.dto.SubscriptionActionResponse;
import com.homekept.subscription.dto.SubscriptionEventItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only subscriber endpoints.
 *
 * <p>ADMIN role enforced by {@code @PreAuthorize} (second gate after the JWT filter).
 * These endpoints fall under {@code .anyRequest().authenticated()} in SecurityConfig.
 *
 * <p>No PII in responses — IDs, enums, integer cents, and booleans only.
 * Access notes are never decrypted here; only {@code hasAccessNotes} is surfaced.
 */
@RestController
@RequestMapping("/api/admin/subscribers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriberController {

    private final SubscriptionAdminService subscriptionAdminService;

    public AdminSubscriberController(SubscriptionAdminService subscriptionAdminService) {
        this.subscriptionAdminService = subscriptionAdminService;
    }

    /**
     * GET /api/admin/subscribers?cursor=&limit=
     * Cursor-paginated subscriber list for the admin console (newest first).
     * - {@code cursor}: optional id cursor (exclusive upper bound)
     * - {@code limit}: optional page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<List<AdminSubscriberListItem>> listSubscribers(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(subscriptionAdminService.listSubscribers(cursor, limit));
    }

    /**
     * GET /api/admin/subscribers/{id}
     * Full subscriber detail including property summary (access notes never decrypted).
     * Returns 404 if not found (ownership-failure rule: never 403).
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminSubscriberDetail> getSubscriber(@PathVariable Long id) {
        AdminSubscriberDetail detail = subscriptionAdminService.getSubscriberDetail(id);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * POST /api/admin/subscribers/{id}/cancel
     * Cancels the subscriber's subscription — at period end by default, or immediately when
     * {@code immediately: true}. Records the reason as a {@code MANUAL} subscription_event
     * before calling Stripe. The CANCELLED status itself is applied later by the
     * {@code customer.subscription.deleted} webhook.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionActionResponse> cancelSubscriber(
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelSubscriptionRequest request) {
        return ResponseEntity.ok(
                subscriptionAdminService.cancelSubscriber(id, request.reason(), request.immediately()));
    }

    /**
     * POST /api/admin/subscribers/{id}/pause
     * Pauses billing (eligible only from ACTIVE) — same Stripe call as the customer
     * self-serve pause.
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<SubscriptionActionResponse> pauseSubscriber(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionAdminService.pauseSubscriber(id));
    }

    /**
     * POST /api/admin/subscribers/{id}/resume
     * Resumes billing (eligible only from PAUSED) — same Stripe call as the customer
     * self-serve resume.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionActionResponse> resumeSubscriber(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionAdminService.resumeSubscriber(id));
    }

    /**
     * GET /api/admin/subscribers/{id}/events
     * The subscriber's activity history (Stripe webhook deliveries + manual actions),
     * newest first, capped at 100 rows.
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<SubscriptionEventItem>> listSubscriberEvents(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionAdminService.listSubscriberEvents(id));
    }
}
