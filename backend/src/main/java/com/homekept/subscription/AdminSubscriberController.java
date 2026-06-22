package com.homekept.subscription;

import com.homekept.subscription.dto.AdminSubscriberDetail;
import com.homekept.subscription.dto.AdminSubscriberListItem;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
