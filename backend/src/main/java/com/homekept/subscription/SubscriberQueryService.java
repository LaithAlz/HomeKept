package com.homekept.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Each user has at most one active subscriber at a given time.
     *
     * @param userId the identity-domain user id
     * @return the subscriber, or empty if no subscriber exists for this user
     */
    @Transactional(readOnly = true)
    public Optional<Subscriber> findByUserId(Long userId) {
        return subscriberRepository.findByUserId(userId);
    }
}
