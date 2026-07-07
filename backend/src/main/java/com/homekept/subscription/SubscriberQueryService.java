package com.homekept.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only service that exposes {@link Subscriber} lookups to other domains.
 *
 * <p>Other domains (e.g. {@code visit}) call this service instead of injecting
 * {@link SubscriberRepository} directly, which would violate the domain-boundary rule
 * (arch doc §1 — a domain may consume another domain's <em>service interface</em>, never
 * its repository or entities directly).
 *
 * <p>Returning the {@link Subscriber} entity across domain boundaries is the accepted
 * pattern in this codebase for lightweight cross-domain reads (e.g. {@code AuthService}
 * returns {@code User}). Callers must treat the returned entity as read-only — they must
 * not save it via another domain's repository.
 */
@Service
public class SubscriberQueryService {

    private final SubscriberRepository subscriberRepository;

    public SubscriberQueryService(SubscriberRepository subscriberRepository) {
        this.subscriberRepository = subscriberRepository;
    }

    /**
     * Finds a subscriber by its primary key.
     *
     * @param id the subscriber id
     * @return the subscriber, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Subscriber> findById(Long id) {
        return subscriberRepository.findById(id);
    }

    /**
     * Finds a subscriber by the owning user's id.
     *
     * @deprecated a user may own more than one subscriber (multi-property portfolio,
     * Phase 1 — docs/portfolio-multi-property-proposal.md). Prefer
     * {@link #findAllByUserId} or {@link #resolveOwnedSubscriber} in new code. Kept for
     * existing single-subscriber call sites.
     *
     * @param userId the identity-domain user id
     * @return the subscriber, or empty if no subscriber exists for this user
     */
    @Transactional(readOnly = true)
    public Optional<Subscriber> findByUserId(Long userId) {
        return subscriberRepository.findByUserId(userId);
    }

    /**
     * Finds every subscriber owned by a user, oldest first (lowest id first).
     *
     * <p>Multi-property portfolio (Phase 1): one user can own several subscribers, one per
     * property (a landlord is just a CUSTOMER who owns more than one). A single-property
     * user gets a one-element list.
     *
     * @param userId the identity-domain user id
     * @return the user's subscribers, oldest first; empty if the user owns none
     */
    @Transactional(readOnly = true)
    public List<Subscriber> findAllByUserId(Long userId) {
        return subscriberRepository.findAllByUserIdOrderByIdAsc(userId);
    }

    /**
     * Resolves the subscriber a per-property {@code /api/app/*} endpoint should act on,
     * scoped by an optional caller-supplied {@code propertyId}.
     *
     * <h2>Resolution rule</h2>
     * <ul>
     *   <li>{@code propertyId} given: returns the caller's subscriber for that property.
     *       If the user owns no subscriber there — including when the property belongs to
     *       a different user entirely — this throws {@link SubscriberNotFoundException}
     *       (→ 404). Ownership failures never distinguish "doesn't exist" from "not yours"
     *       (CLAUDE.md ownership-failure rule).</li>
     *   <li>{@code propertyId} omitted, caller owns exactly one subscriber: returns it.
     *       This is the pre-portfolio behaviour and stays fully backward compatible — a
     *       single-property customer's app never needs to learn about {@code propertyId}.</li>
     *   <li>{@code propertyId} omitted, caller owns several: returns the earliest-created
     *       (lowest id) subscriber as the default "primary" property, so an existing
     *       single-property client that hasn't been updated to send {@code propertyId} still
     *       gets a 200 (a sensible default) instead of a 400.</li>
     *   <li>Caller owns no subscriber at all: throws {@link SubscriberNotFoundException}.</li>
     * </ul>
     *
     * @param userId     the authenticated user's id (JWT principal)
     * @param propertyId optional property id to scope to; {@code null} applies the default rule
     * @return the resolved, ownership-checked subscriber
     * @throws SubscriberNotFoundException if no matching subscriber exists for this user (→ 404)
     */
    @Transactional(readOnly = true)
    public Subscriber resolveOwnedSubscriber(Long userId, Long propertyId) {
        if (propertyId != null) {
            return subscriberRepository.findByUserIdAndPropertyId(userId, propertyId)
                    .orElseThrow(() -> new SubscriberNotFoundException(
                            "No subscriber found for userId=" + userId + " propertyId=" + propertyId));
        }
        List<Subscriber> owned = subscriberRepository.findAllByUserIdOrderByIdAsc(userId);
        if (owned.isEmpty()) {
            throw new SubscriberNotFoundException("No subscriber row found for userId=" + userId);
        }
        // Oldest first — with exactly one subscriber this is simply that subscriber
        // (backward compatible); with several, it's the default "primary" property.
        return owned.get(0);
    }
}
