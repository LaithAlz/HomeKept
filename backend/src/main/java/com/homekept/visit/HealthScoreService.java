package com.homekept.visit;

import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.dto.HealthScoreFlaggedItem;
import com.homekept.visit.dto.HealthScoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Home Health Score v1 (#53).
 *
 * <h2>Rubric (computed on read)</h2>
 * <pre>
 *   score = clamp(100 - flagPenalty - checklistDeduction, 0, 100)
 *   flagPenalty        = sum over OPEN flags: URGENT 20, ATTENTION 10, INFO 3
 *   checklistDeduction = round(15 * (1 - completionRate of the most recent completed visit))
 *                        (0 when the subscriber has no completed visit yet)
 * </pre>
 *
 * <h2>Snapshots</h2>
 * <p>{@link #snapshotOnCompletion} writes one {@link HealthScoreSnapshot} per completed visit
 * so {@link #getHealthScore} can report a delta against the value at the previous visit.
 *
 * <h2>Domain boundary</h2>
 * <p>Resolves the subscriber via {@link SubscriberQueryService} (subscription domain's
 * service); all other reads are within the visit domain.
 */
@Service
public class HealthScoreService {

    static final int BASE_SCORE = 100;
    static final int CHECKLIST_BAND = 15;

    private final FlagRepository flagRepository;
    private final VisitRepository visitRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final HealthScoreSnapshotRepository snapshotRepository;
    private final SubscriberQueryService subscriberQueryService;

    public HealthScoreService(FlagRepository flagRepository,
                              VisitRepository visitRepository,
                              VisitServiceRepository visitServiceRepository,
                              HealthScoreSnapshotRepository snapshotRepository,
                              SubscriberQueryService subscriberQueryService) {
        this.flagRepository = flagRepository;
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.snapshotRepository = snapshotRepository;
        this.subscriberQueryService = subscriberQueryService;
    }

    /**
     * Returns the live score for the authenticated customer, plus the delta since the last
     * snapshot and the current OPEN flags.
     *
     * @param userId     the authenticated user's id (JWT principal)
     * @param propertyId optional property to scope to (multi-property portfolio); see
     *                   {@link com.homekept.subscription.SubscriberQueryService#resolveOwnedSubscriber}
     * @throws com.homekept.subscription.SubscriberNotFoundException
     *         if the user has no matching subscriber (→ 404, ownership rule)
     */
    @Transactional(readOnly = true)
    public HealthScoreResponse getHealthScore(Long userId, Long propertyId) {
        Long subscriberId = subscriberQueryService.resolveOwnedSubscriber(userId, propertyId).getId();

        int score = computeScore(subscriberId);
        int delta = snapshotRepository.findFirstBySubscriberIdOrderByComputedAtDesc(subscriberId)
                .map(prior -> score - prior.getScore())
                .orElse(0);

        List<HealthScoreFlaggedItem> flagged = openFlags(subscriberId).stream()
                .map(f -> new HealthScoreFlaggedItem(
                        f.getId(), f.getBody(), f.getSeverity().name(), f.getCreatedAt()))
                .toList();

        return new HealthScoreResponse(score, delta, Instant.now(), flagged);
    }

    /**
     * Computes and persists a snapshot of the subscriber's current score. Called from
     * {@link TechVisitService} on visit completion (within that transaction).
     */
    @Transactional
    public HealthScoreSnapshot snapshotOnCompletion(Long subscriberId) {
        return snapshotRepository.save(new HealthScoreSnapshot(subscriberId, computeScore(subscriberId)));
    }

    /**
     * Returns the live score for a subscriber the caller has already resolved/verified
     * ownership for. For cross-domain reads only (e.g. the subscription domain's
     * {@code GET /api/app/properties} portfolio summary) — mirrors the pattern in
     * {@link VisitQueryService}: the caller passes a {@code subscriberId} it already owns,
     * never a raw, untrusted HTTP parameter, so this does not reopen the IDOR concern that
     * {@link AppHealthScoreController} guards against.
     *
     * @param subscriberId the subscription-domain subscriber id
     * @return the current score (0..100)
     */
    @Transactional(readOnly = true)
    public int getScoreForSubscriber(Long subscriberId) {
        return computeScore(subscriberId);
    }

    // ── Rubric ──────────────────────────────────────────────────────────────────

    int computeScore(Long subscriberId) {
        int flagPenalty = openFlags(subscriberId).stream()
                .mapToInt(f -> severityWeight(f.getSeverity()))
                .sum();
        int checklistDeduction = checklistDeduction(subscriberId);
        return clamp(BASE_SCORE - flagPenalty - checklistDeduction, 0, 100);
    }

    private int severityWeight(FlagSeverity severity) {
        return switch (severity) {
            case URGENT -> 20;
            case ATTENTION -> 10;
            case INFO -> 3;
        };
    }

    /** Deduction from the most recent completed visit's checklist completion rate. */
    private int checklistDeduction(Long subscriberId) {
        return visitRepository
                .findFirstBySubscriberIdAndStatusOrderByScheduledForDescIdDesc(subscriberId, VisitStatus.COMPLETED)
                .map(visit -> {
                    List<VisitService> services = visitServiceRepository.findByVisitIdOrderByIdAsc(visit.getId());
                    if (services.isEmpty()) {
                        return 0;
                    }
                    long completed = services.stream().filter(VisitService::isCompleted).count();
                    double completionRate = (double) completed / services.size();
                    return (int) Math.round(CHECKLIST_BAND * (1.0 - completionRate));
                })
                .orElse(0);
    }

    private List<Flag> openFlags(Long subscriberId) {
        return flagRepository.findBySubscriberIdAndStatusOrderByCreatedAtDesc(subscriberId, FlagStatus.OPEN);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
