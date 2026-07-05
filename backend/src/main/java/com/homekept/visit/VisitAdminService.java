package com.homekept.visit;

import com.homekept.catalog.CatalogService;
import com.homekept.subscription.SubscriberNotFoundException;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.dto.AdminCreateVisitRequest;
import com.homekept.visit.dto.AdminPatchVisitRequest;
import com.homekept.visit.dto.AdminVisitListItem;
import com.homekept.visit.dto.AdminVisitResponse;
import com.homekept.visit.dto.VisitServiceItem;
import com.homekept.visit.exception.IllegalVisitTransitionException;
import com.homekept.visit.exception.InvalidVisitRequestException;
import com.homekept.visit.exception.VisitNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin visit management: create, patch (reschedule / cancel / assign technician).
 *
 * <p>Rules:
 * <ul>
 *   <li>Every status write goes through {@link VisitStateMachine#canTransition}.</li>
 *   <li>Reschedule: marks the old visit RESCHEDULED (terminal), creates a fresh
 *       SCHEDULED visit copying the subscriber, property, template, services, and type.
 *       This preserves history per arch doc §4.2.</li>
 *   <li>Money is integer cents — {@code materialsCostCents} uses INTEGER, never float.</li>
 *   <li>No PII in logs — IDs and enums only.</li>
 * </ul>
 *
 * <h2>Domain boundaries</h2>
 * <p>Validates the subscriber exists via {@link SubscriberQueryService} (subscription
 * domain's service) — never by calling the subscription repository directly.
 * Service name lookups go through {@link CatalogService}; the catalog repository is
 * never called directly from this package.
 */
@Service
public class VisitAdminService {

    private static final Logger log = LoggerFactory.getLogger(VisitAdminService.class);

    static final int DEFAULT_DURATION_MINUTES = 120;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final VisitRepository visitRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final VisitStateMachine stateMachine;
    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;

    public VisitAdminService(VisitRepository visitRepository,
                             VisitServiceRepository visitServiceRepository,
                             VisitStateMachine stateMachine,
                             SubscriberQueryService subscriberQueryService,
                             CatalogService catalogService) {
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.stateMachine = stateMachine;
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
    }

    /**
     * Creates a new visit for a subscriber. If {@code serviceIds} are provided they are
     * attached as {@code VisitService} rows with source=TEMPLATE (standing items seeded
     * from a known template) or source=EXTRA for ad-hoc admin additions. All admin-
     * provided service IDs are tagged TEMPLATE since they are provided at creation time
     * in the same way a template would supply them.
     *
     * @param request validated create request
     * @return the created visit
     */
    @Transactional
    public AdminVisitResponse createVisit(AdminCreateVisitRequest request) {
        // Validate subscriber exists via subscription-domain service (never the repo directly).
        var subscriber = subscriberQueryService.findById(request.subscriberId())
                .orElseThrow(() -> new SubscriberNotFoundException("Subscriber not found: " + request.subscriberId()));

        // Validate service IDs via CatalogService (never the catalog repository directly).
        List<Long> serviceIds = request.serviceIds() != null ? request.serviceIds() : List.of();
        if (!serviceIds.isEmpty()) {
            List<Long> missing = catalogService.findUnknownServiceIds(serviceIds);
            if (!missing.isEmpty()) {
                throw new InvalidVisitRequestException("Unknown service IDs: " + missing);
            }
        }

        Visit visit = new Visit(
                subscriber.getId(),
                subscriber.getPropertyId(),
                null,   // no template — admin-created visits are not template-driven
                request.scheduledFor(),
                request.durationMinutes() != null ? request.durationMinutes() : DEFAULT_DURATION_MINUTES,
                VisitType.ROUTINE
        );

        if (request.technicianUserId() != null) {
            visit.setTechnicianId(request.technicianUserId());
        }

        Visit saved = visitRepository.save(visit);

        List<VisitService> createdServices = new ArrayList<>();
        for (Long svcId : serviceIds) {
            VisitService vs = new VisitService(saved.getId(), svcId, VisitServiceSource.TEMPLATE);
            createdServices.add(visitServiceRepository.save(vs));
        }

        log.info("admin_visit_created visitId={} subscriberId={} services={}",
                saved.getId(), subscriber.getId(), createdServices.size());

        return toResponse(saved, toServiceItemsFromEntities(createdServices));
    }

    /**
     * Returns a cursor-paginated list of visits for the admin console.
     * Ordered by id descending (newest first). If {@code status} is provided, filters
     * by that status; otherwise returns all statuses.
     *
     * @param status optional status filter (name of {@link VisitStatus})
     * @param cursor optional id cursor (exclusive upper bound — return rows with id &lt; cursor)
     * @param limit  optional page size (defaults to {@value DEFAULT_PAGE_SIZE}, capped at 100)
     * @throws InvalidVisitRequestException if {@code status} is not a valid {@link VisitStatus}
     */
    @Transactional(readOnly = true)
    public List<AdminVisitListItem> listVisits(String status, Long cursor, Integer limit) {
        int pageSize = resolveLimit(limit);
        PageRequest pageable = PageRequest.of(0, pageSize);

        List<Visit> visits;

        if (status != null && !status.isBlank()) {
            VisitStatus visitStatus;
            try {
                visitStatus = VisitStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidVisitRequestException("Invalid status value: " + status);
            }

            visits = (cursor != null)
                    ? visitRepository.findByStatusAndIdLessThanOrderByIdDesc(visitStatus, cursor, pageable)
                    : visitRepository.findByStatusOrderByIdDesc(visitStatus, pageable);
        } else {
            visits = (cursor != null)
                    ? visitRepository.findByIdLessThanOrderByIdDesc(cursor, pageable)
                    : visitRepository.findAllByOrderByIdDesc(pageable);
        }

        return visits.stream()
                .map(AdminVisitListItem::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns the count of SCHEDULED visits with {@code scheduledFor} at or after now.
     * Used by the admin dashboard aggregate ("upcoming visits").
     */
    @Transactional(readOnly = true)
    public long countUpcomingVisits() {
        return visitRepository.countByStatusAndScheduledForGreaterThanEqual(VisitStatus.SCHEDULED, Instant.now());
    }

    /**
     * Patches a visit: reschedule, cancel, or assign technician.
     *
     * <p>Reschedule ({@code scheduledFor} present): marks old visit RESCHEDULED (via state
     * machine), creates a fresh SCHEDULED visit at the new time with the same subscriber,
     * property, template, type, and service rows (source preserved).
     *
     * <p>Cancel ({@code status = "CANCELLED"}): transitions via state machine.
     *
     * <p>Assign technician ({@code technicianUserId} present, no other op): updates the
     * technician without a state transition.
     *
     * @param visitId the visit id to patch
     * @param request the patch request
     * @return the updated (or newly created) visit representation
     */
    @Transactional
    public AdminVisitResponse patchVisit(Long visitId, AdminPatchVisitRequest request) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new VisitNotFoundException(visitId));

        boolean isReschedule = request.scheduledFor() != null;
        boolean isCancel = "CANCELLED".equalsIgnoreCase(request.status());
        boolean isTechAssign = request.technicianUserId() != null;

        if (isReschedule && isCancel) {
            throw new InvalidVisitRequestException(
                    "Ambiguous request: cannot supply both scheduledFor and status=CANCELLED");
        }

        if (isReschedule) {
            return reschedule(visit, request.scheduledFor(), request.technicianUserId());
        }

        if (isCancel) {
            return cancel(visit);
        }

        if (isTechAssign) {
            visit.setTechnicianId(request.technicianUserId());
            Visit saved = visitRepository.save(visit);
            log.info("admin_visit_technician_assigned visitId={} technicianId={}",
                    saved.getId(), saved.getTechnicianId());
            return toResponse(saved, loadServiceItems(saved.getId()));
        }

        // No-op patch — return current state.
        return toResponse(visit, loadServiceItems(visit.getId()));
    }

    // ── Private operations ────────────────────────────────────────────────────

    private AdminVisitResponse reschedule(Visit oldVisit, Instant newScheduledFor, Long technicianUserId) {
        Visit savedNew = rescheduleInternal(oldVisit, newScheduledFor, technicianUserId);
        return toResponse(savedNew, loadServiceItems(savedNew.getId()));
    }

    /**
     * Reschedules a visit by id and returns the new SCHEDULED visit. Used by the customer
     * reschedule-request confirm flow ({@code RescheduleService}) so it can record the
     * replacement visit id. Same-domain service call (visit → visit) — allowed.
     *
     * @param visitId         the visit to reschedule
     * @param newScheduledFor the new start time
     * @return the newly created SCHEDULED visit
     * @throws VisitNotFoundException        if the visit does not exist
     * @throws IllegalVisitTransitionException if the visit is not in a reschedulable state
     */
    @Transactional
    public Visit rescheduleVisit(Long visitId, Instant newScheduledFor) {
        Visit oldVisit = visitRepository.findById(visitId)
                .orElseThrow(() -> new VisitNotFoundException(visitId));
        return rescheduleInternal(oldVisit, newScheduledFor, null);
    }

    /**
     * Core reschedule: marks the old visit RESCHEDULED (via the state machine) and creates a
     * fresh SCHEDULED visit copying the subscriber, property, template, type, and service
     * rows (source preserved). Returns the new visit. Per arch doc §4.2 this preserves history.
     */
    private Visit rescheduleInternal(Visit oldVisit, Instant newScheduledFor, Long technicianUserId) {
        // State machine: SCHEDULED → RESCHEDULED
        if (!stateMachine.canTransition(oldVisit.getStatus(), VisitStatus.RESCHEDULED)) {
            throw new IllegalVisitTransitionException(oldVisit.getStatus(), VisitStatus.RESCHEDULED);
        }
        oldVisit.setStatus(VisitStatus.RESCHEDULED);
        visitRepository.save(oldVisit);
        log.info("admin_visit_rescheduled oldVisitId={}", oldVisit.getId());

        // Create the new SCHEDULED visit.
        Visit newVisit = new Visit(
                oldVisit.getSubscriberId(),
                oldVisit.getPropertyId(),
                oldVisit.getVisitTemplateId(),
                newScheduledFor,
                oldVisit.getDurationMinutes(),
                oldVisit.getType()
        );
        if (technicianUserId != null) {
            newVisit.setTechnicianId(technicianUserId);
        } else if (oldVisit.getTechnicianId() != null) {
            newVisit.setTechnicianId(oldVisit.getTechnicianId());
        }

        Visit savedNew = visitRepository.save(newVisit);

        // Copy the old visit's service rows to the new visit (source preserved).
        List<VisitService> oldServices = visitServiceRepository.findByVisitIdOrderByIdAsc(oldVisit.getId());
        int copied = 0;
        for (VisitService vs : oldServices) {
            visitServiceRepository.save(new VisitService(savedNew.getId(), vs.getServiceId(), vs.getSource()));
            copied++;
        }

        log.info("admin_visit_created_rescheduled newVisitId={} subscriberId={} services={}",
                savedNew.getId(), savedNew.getSubscriberId(), copied);

        return savedNew;
    }

    private AdminVisitResponse cancel(Visit visit) {
        if (!stateMachine.canTransition(visit.getStatus(), VisitStatus.CANCELLED)) {
            throw new IllegalVisitTransitionException(visit.getStatus(), VisitStatus.CANCELLED);
        }
        visit.setStatus(VisitStatus.CANCELLED);
        Visit saved = visitRepository.save(visit);
        log.info("admin_visit_cancelled visitId={} subscriberId={}", saved.getId(), saved.getSubscriberId());
        return toResponse(saved, loadServiceItems(saved.getId()));
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 100);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AdminVisitResponse toResponse(Visit v, List<VisitServiceItem> services) {
        return new AdminVisitResponse(
                v.getId(),
                v.getSubscriberId(),
                v.getPropertyId(),
                v.getTechnicianId(),
                v.getVisitTemplateId(),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getActualDurationMinutes(),
                v.getMaterialsCostCents(),
                v.getStatus().name(),
                v.getType().name(),
                v.getCompletionNotes(),
                v.getCompletedAt(),
                v.getCreatedAt(),
                services
        );
    }

    private List<VisitServiceItem> loadServiceItems(Long visitId) {
        List<VisitService> rows = visitServiceRepository.findByVisitIdOrderByIdAsc(visitId);
        return toServiceItemsFromEntities(rows);
    }

    private List<VisitServiceItem> toServiceItemsFromEntities(List<VisitService> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(VisitService::getServiceId).distinct().collect(Collectors.toList());
        Map<Long, String> nameById = catalogService.getServiceNamesByIds(ids);
        return rows.stream()
                .map(vs -> new VisitServiceItem(
                        vs.getId(),
                        vs.getServiceId(),
                        nameById.getOrDefault(vs.getServiceId(), "Unknown service"),
                        vs.getSource().name(),
                        vs.isCompleted(),
                        vs.getTechnicianNotes()))
                .collect(Collectors.toList());
    }
}
