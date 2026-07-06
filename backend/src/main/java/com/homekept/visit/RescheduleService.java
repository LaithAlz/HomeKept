package com.homekept.visit;

import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.dto.AdminRescheduleRequestItem;
import com.homekept.visit.dto.RescheduleRequestResponse;
import com.homekept.visit.exception.RescheduleRequestConflictException;
import com.homekept.visit.exception.RescheduleRequestNotFoundException;
import com.homekept.visit.exception.VisitNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Customer self-serve reschedule requests and the admin confirm/decline loop (#54).
 *
 * <h2>Customer</h2>
 * <p>{@link #createRequest} verifies the visit belongs to the authenticated subscriber
 * (ownership → 404) and is SCHEDULED, then records a PENDING {@link RescheduleRequest} with
 * the proposed slots. Duplicate PENDING requests for one visit are prevented by the DB
 * partial unique index — we insert blind and translate the violation to a 409, avoiding a
 * pre-check query.
 *
 * <h2>Admin</h2>
 * <p>{@link #confirm} reschedules the visit via {@link VisitAdminService#rescheduleVisit}
 * (the visit state machine creates the replacement visit) and records CONFIRMED +
 * {@code confirmedVisitId}. {@link #decline} records DECLINED with the admin's note.
 *
 * <h2>Domain boundary</h2>
 * <p>Resolves the subscriber via {@link SubscriberQueryService} (subscription domain's
 * service), never its repository. All visit access stays within this domain.
 */
@Service
public class RescheduleService {

    private static final Logger log = LoggerFactory.getLogger(RescheduleService.class);

    private final RescheduleRequestRepository rescheduleRequestRepository;
    private final RescheduleRequestSlotRepository slotRepository;
    private final VisitRepository visitRepository;
    private final VisitAdminService visitAdminService;
    private final SubscriberQueryService subscriberQueryService;

    public RescheduleService(RescheduleRequestRepository rescheduleRequestRepository,
                             RescheduleRequestSlotRepository slotRepository,
                             VisitRepository visitRepository,
                             VisitAdminService visitAdminService,
                             SubscriberQueryService subscriberQueryService) {
        this.rescheduleRequestRepository = rescheduleRequestRepository;
        this.slotRepository = slotRepository;
        this.visitRepository = visitRepository;
        this.visitAdminService = visitAdminService;
        this.subscriberQueryService = subscriberQueryService;
    }

    // ── Customer ────────────────────────────────────────────────────────────────

    /**
     * Creates a PENDING reschedule request for one of the authenticated customer's visits.
     *
     * @param userId         the authenticated user's id (JWT principal)
     * @param propertyId     optional property to scope to (multi-property portfolio)
     * @param visitId        the visit to reschedule
     * @param preferredSlots 1..N proposed start times
     * @return the created request
     * @throws VisitNotFoundException             if the visit is not owned by the subscriber (404)
     * @throws RescheduleRequestConflictException if the visit is not SCHEDULED, or a PENDING
     *                                            request already exists for it (409)
     */
    @Transactional
    public RescheduleRequestResponse createRequest(Long userId, Long propertyId, Long visitId,
                                                   List<Instant> preferredSlots) {
        Long subscriberId = resolveSubscriberId(userId, propertyId);

        Visit visit = visitRepository.findByIdAndSubscriberId(visitId, subscriberId)
                .orElseThrow(() -> {
                    log.debug("reschedule_visit_not_owned visitId={} subscriberId={}", visitId, subscriberId);
                    return new VisitNotFoundException(visitId);
                });

        if (visit.getStatus() != VisitStatus.SCHEDULED) {
            throw new RescheduleRequestConflictException(
                    "Only a scheduled visit can be rescheduled.");
        }

        RescheduleRequest request = new RescheduleRequest(visitId, subscriberId);
        try {
            // saveAndFlush forces the partial-unique-index check now so a duplicate PENDING
            // request fails here (→ 409) instead of after we have inserted slot rows.
            request = rescheduleRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new RescheduleRequestConflictException(
                    "You already have a pending reschedule request for this visit.");
        }

        for (Instant slot : preferredSlots) {
            slotRepository.save(new RescheduleRequestSlot(request.getId(), slot));
        }

        log.info("reschedule_request_created requestId={} visitId={} subscriberId={} slots={}",
                request.getId(), visitId, subscriberId, preferredSlots.size());

        return new RescheduleRequestResponse(
                request.getId(),
                request.getVisitId(),
                request.getStatus().name(),
                loadSlots(request.getId()),
                request.getCreatedAt());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** Lists PENDING reschedule requests for the admin queue, oldest first. */
    @Transactional(readOnly = true)
    public List<AdminRescheduleRequestItem> listPending() {
        return rescheduleRequestRepository.findByStatusOrderByIdAsc(RescheduleRequestStatus.PENDING)
                .stream()
                .map(this::toAdminItem)
                .toList();
    }

    /**
     * Confirms a reschedule request: reschedules the visit to {@code scheduledFor} (the
     * visit state machine creates the replacement visit) and marks the request CONFIRMED.
     *
     * @throws RescheduleRequestNotFoundException if the request does not exist (404)
     * @throws RescheduleRequestConflictException if the request is already resolved (409)
     */
    @Transactional
    public AdminRescheduleRequestItem confirm(Long requestId, Instant scheduledFor, String adminNote) {
        RescheduleRequest request = requirePending(requestId);

        // Reschedules the underlying visit (RESCHEDULED old + new SCHEDULED). Throws
        // IllegalVisitTransitionException (409) if the visit is no longer reschedulable.
        Visit newVisit = visitAdminService.rescheduleVisit(request.getVisitId(), scheduledFor);

        request.setStatus(RescheduleRequestStatus.CONFIRMED);
        request.setConfirmedVisitId(newVisit.getId());
        request.setAdminNote(adminNote);
        rescheduleRequestRepository.save(request);

        log.info("reschedule_request_confirmed requestId={} oldVisitId={} newVisitId={}",
                request.getId(), request.getVisitId(), newVisit.getId());

        return toAdminItem(request);
    }

    /**
     * Declines a reschedule request with a required note.
     *
     * @throws RescheduleRequestNotFoundException if the request does not exist (404)
     * @throws RescheduleRequestConflictException if the request is already resolved (409)
     */
    @Transactional
    public AdminRescheduleRequestItem decline(Long requestId, String adminNote) {
        RescheduleRequest request = requirePending(requestId);

        request.setStatus(RescheduleRequestStatus.DECLINED);
        request.setAdminNote(adminNote);
        rescheduleRequestRepository.save(request);

        log.info("reschedule_request_declined requestId={} visitId={}",
                request.getId(), request.getVisitId());

        return toAdminItem(request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RescheduleRequest requirePending(Long requestId) {
        RescheduleRequest request = rescheduleRequestRepository.findById(requestId)
                .orElseThrow(() -> new RescheduleRequestNotFoundException(requestId));
        if (request.getStatus() != RescheduleRequestStatus.PENDING) {
            throw new RescheduleRequestConflictException(
                    "This reschedule request has already been resolved.");
        }
        return request;
    }

    private Long resolveSubscriberId(Long userId, Long propertyId) {
        return subscriberQueryService.resolveOwnedSubscriber(userId, propertyId).getId();
    }

    private List<Instant> loadSlots(Long requestId) {
        return slotRepository.findByRescheduleRequestIdOrderByPreferredSlotAsc(requestId)
                .stream()
                .map(RescheduleRequestSlot::getPreferredSlot)
                .toList();
    }

    private AdminRescheduleRequestItem toAdminItem(RescheduleRequest request) {
        return new AdminRescheduleRequestItem(
                request.getId(),
                request.getVisitId(),
                request.getSubscriberId(),
                request.getStatus().name(),
                loadSlots(request.getId()),
                request.getAdminNote(),
                request.getConfirmedVisitId(),
                request.getCreatedAt());
    }
}
