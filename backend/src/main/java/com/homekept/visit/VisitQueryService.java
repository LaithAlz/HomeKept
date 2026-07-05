package com.homekept.visit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Read-only service that exposes narrow {@link Visit} lookups to other domains.
 *
 * <p>Other domains (e.g. subscription's billing page) call this service instead of
 * injecting {@link VisitRepository} directly, which would violate the domain-boundary rule
 * (a domain may consume another domain's service interface, never its repository or
 * entities directly).
 *
 * <p>Callers pass a {@code subscriberId} they already own/resolved themselves (e.g. the
 * subscription domain's own {@code Subscriber} row) — this is not a caller-supplied,
 * untrusted value from an HTTP request, so it does not reopen the IDOR concern that
 * {@link AppVisitController} guards against by only ever accepting a {@code userId}.
 */
@Service
public class VisitQueryService {

    private final VisitRepository visitRepository;

    public VisitQueryService(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    /**
     * Returns the {@code scheduledFor} instant of the subscriber's next SCHEDULED visit.
     *
     * @param subscriberId the subscription-domain subscriber id
     * @return the next scheduled visit's date, or empty if none is scheduled
     */
    @Transactional(readOnly = true)
    public Optional<Instant> findNextScheduledVisitDate(Long subscriberId) {
        return visitRepository
                .findFirstBySubscriberIdAndStatusOrderByScheduledForAscIdAsc(subscriberId, VisitStatus.SCHEDULED)
                .map(Visit::getScheduledFor);
    }
}
