package com.homekept.visit;

import com.homekept.catalog.CatalogService;
import com.homekept.common.Pagination;
import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserQueryService.AdminContactDetail;
import com.homekept.identity.UserQueryService.UserSummary;
import com.homekept.property.Property;
import com.homekept.property.PropertyService;
import com.homekept.storage.StorageService;
import com.homekept.storage.StorageUnavailableException;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberNotFoundException;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.dto.AdminCreateVisitRequest;
import com.homekept.visit.dto.AdminPatchVisitRequest;
import com.homekept.visit.dto.AdminVisitDayLoadItem;
import com.homekept.visit.dto.AdminVisitDetail;
import com.homekept.visit.dto.AdminVisitListItem;
import com.homekept.visit.dto.AdminVisitPropertySummary;
import com.homekept.visit.dto.AdminVisitResponse;
import com.homekept.visit.dto.AppVisitPhoto;
import com.homekept.visit.dto.VisitEventItem;
import com.homekept.visit.dto.VisitServiceItem;
import com.homekept.visit.exception.IllegalVisitTransitionException;
import com.homekept.visit.exception.InvalidVisitRequestException;
import com.homekept.visit.exception.VisitNotFoundException;
import com.homekept.visit.exception.VisitNotReschedulableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin visit management: create, patch (reschedule / cancel / assign technician), the
 * single-visit detail view, and the visit's activity log.
 *
 * <p>Rules:
 * <ul>
 *   <li>Every status write goes through {@link VisitStateMachine#canTransition}; whether a
 *       visit may be rescheduled at all goes through {@link VisitStateMachine#canReschedule}
 *       (see that method's javadoc for why reschedule no longer uses {@code canTransition}
 *       with {@code RESCHEDULED} as the target).</li>
 *   <li>Reschedule updates the existing visit's {@code scheduledFor} (and technician, when
 *       supplied) IN PLACE and records a {@code visit_event} row instead of creating a
 *       replacement visit — see {@link #rescheduleInternal}'s javadoc. This supersedes the
 *       old create-a-replacement-visit model formerly documented at arch doc §4.2.</li>
 *   <li>Money is integer cents — {@code materialsCostCents} uses INTEGER, never float.</li>
 *   <li>No PII in logs — IDs and enums only.</li>
 * </ul>
 *
 * <h2>Domain boundaries</h2>
 * <p>Validates the subscriber exists via {@link SubscriberQueryService} (subscription
 * domain's service) — never by calling the subscription repository directly. Service name
 * lookups go through {@link CatalogService}; the catalog repository is never called
 * directly from this package. The visit detail view resolves the customer's identity via
 * {@link UserQueryService} (identity domain), the property's address via
 * {@link PropertyService} (property domain), and photo download URLs via
 * {@link StorageService} (storage domain) — never their repositories or entities (except
 * {@link Property}, whose {@code findById} already returns the entity to this same admin-
 * console pattern elsewhere, e.g. {@code SubscriptionAdminService#toDetail}).
 */
@Service
public class VisitAdminService {

    private static final Logger log = LoggerFactory.getLogger(VisitAdminService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Max inclusive span, in days, accepted by {@link #listDayLoad}. */
    private static final long MAX_DAY_LOAD_SPAN_DAYS = 62;

    /** Cap for {@link #listEvents} — mirrors {@code SubscriptionAdminService.EVENTS_LIMIT}. */
    private static final int EVENTS_LIMIT = 100;

    /** Defense-in-depth cap on photo count for the detail view — mirrors
     * {@code VisitAppService.MAX_PHOTOS_PER_VISIT}. */
    private static final int MAX_PHOTOS_PER_VISIT = 50;

    private final VisitRepository visitRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final VisitPhotoRepository visitPhotoRepository;
    private final VisitTemplateRepository visitTemplateRepository;
    private final VisitEventRepository visitEventRepository;
    private final VisitStateMachine stateMachine;
    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final PropertyService propertyService;
    private final UserQueryService userQueryService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final ZoneId renderZoneId;

    public VisitAdminService(VisitRepository visitRepository,
                             VisitServiceRepository visitServiceRepository,
                             VisitPhotoRepository visitPhotoRepository,
                             VisitTemplateRepository visitTemplateRepository,
                             VisitEventRepository visitEventRepository,
                             VisitStateMachine stateMachine,
                             SubscriberQueryService subscriberQueryService,
                             CatalogService catalogService,
                             PropertyService propertyService,
                             UserQueryService userQueryService,
                             StorageService storageService,
                             ObjectMapper objectMapper,
                             ZoneId renderZoneId) {
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.visitPhotoRepository = visitPhotoRepository;
        this.visitTemplateRepository = visitTemplateRepository;
        this.visitEventRepository = visitEventRepository;
        this.stateMachine = stateMachine;
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.propertyService = propertyService;
        this.userQueryService = userQueryService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.renderZoneId = renderZoneId;
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
                request.durationMinutes(),
                VisitType.ROUTINE
        );
        // templateOccurrenceYear stays null: it identifies which yearly occurrence of a
        // TEMPLATE this visit is, and this visit has no template. The scheduling guard
        // (VisitRepository#existsBySubscriberIdAndVisitTemplateIdAndTemplateOccurrenceYear)
        // is keyed on visitTemplateId too, so a null-template row like this one is never a
        // candidate match regardless — there is nothing to set it TO even if we wanted to.

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
     * by that status; otherwise returns all statuses. Also backs the admin Routes day view,
     * which calls this filtered to {@code status=SCHEDULED} and groups the rows by
     * technician client-side.
     *
     * <p>Each row's customer identity and property address are resolved via two batched
     * queries for the whole page — {@link SubscriberQueryService#findByIds} then
     * {@link UserQueryService#findAdminContactsByIds} for identity, and
     * {@link PropertyService#findByIds} for the address — never one query per row. Mirrors
     * {@code SubscriptionAdminService#listSubscribers}'s batching pattern exactly.
     *
     * @param status optional status filter (name of {@link VisitStatus})
     * @param cursor optional id cursor (exclusive upper bound — return rows with id &lt; cursor)
     * @param limit  optional page size (defaults to {@value DEFAULT_PAGE_SIZE}, capped at 100)
     * @throws InvalidVisitRequestException if {@code status} is not a valid {@link VisitStatus}
     */
    @Transactional(readOnly = true)
    public List<AdminVisitListItem> listVisits(String status, Long cursor, Integer limit) {
        int pageSize = Pagination.resolveLimit(limit, DEFAULT_PAGE_SIZE, 100);
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

        if (visits.isEmpty()) {
            return List.of();
        }

        // Batch 1: subscriberId → Subscriber (for its userId), one query for the whole page.
        List<Long> subscriberIds = visits.stream().map(Visit::getSubscriberId).distinct().toList();
        Map<Long, Subscriber> subscribersById = subscriberQueryService.findByIds(subscriberIds);

        // Batch 2: userId → contact detail, one query for the whole page.
        List<Long> userIds = subscribersById.values().stream().map(Subscriber::getUserId).distinct().toList();
        Map<Long, AdminContactDetail> contactsByUserId = userQueryService.findAdminContactsByIds(userIds);

        // Batch 3: propertyId → Property (for its address), one query for the whole page.
        List<Long> propertyIds = visits.stream().map(Visit::getPropertyId).distinct().toList();
        Map<Long, Property> propertiesById = propertyService.findByIds(propertyIds);

        return visits.stream()
                .map(v -> toListItem(v, subscribersById, contactsByUserId, propertiesById))
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
     * Returns the SCHEDULED-visit load per local calendar day within {@code [from, to]}
     * (inclusive), for the admin Routes month-sidebar calendar. One entry per day that has
     * at least one SCHEDULED visit — empty days are omitted, not sent as zero — ascending.
     *
     * <p>Honest counts only: {@code total} and {@code unassigned} are real visit counts.
     * Never adds a capacity/percentage/"slots free" figure — there is no backing model of
     * technician working hours, so a fabricated availability signal would be worse than none.
     *
     * <p>Backed by a single aggregate query ({@link VisitRepository#findScheduledDayLoad})
     * grouped in the database on the visit's local date — never by loading every row in the
     * range and grouping in Java. {@code from}/{@code to} are local dates in
     * {@link #renderZoneId} (the same zone bean {@link VisitSchedulingService} uses — never
     * hardcoded), converted here to the UTC instant bounds the query binds against.
     *
     * @param from inclusive local start date
     * @param to   inclusive local end date
     * @throws InvalidVisitRequestException if the range is empty/negative or spans more than
     *                                       {@value MAX_DAY_LOAD_SPAN_DAYS} days
     */
    @Transactional(readOnly = true)
    public List<AdminVisitDayLoadItem> listDayLoad(LocalDate from, LocalDate to) {
        long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (inclusiveDays < 1 || inclusiveDays > MAX_DAY_LOAD_SPAN_DAYS) {
            throw new InvalidVisitRequestException(
                    "Date range must be between 1 and " + MAX_DAY_LOAD_SPAN_DAYS + " days");
        }

        Instant fromInstant = from.atStartOfDay(renderZoneId).toInstant();
        Instant toInstantExclusive = to.plusDays(1).atStartOfDay(renderZoneId).toInstant();

        return visitRepository
                .findScheduledDayLoad(renderZoneId.getId(), fromInstant, toInstantExclusive)
                .stream()
                .map(row -> new AdminVisitDayLoadItem(row.getDay().toString(), row.getTotal(), row.getUnassigned()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the full admin detail for a single visit: the visit itself, the customer's
     * identity, the property's address, the assigned technician's name, the checklist, and
     * any notes/photos captured so far. Backs {@code GET /api/admin/visits/{id}} — the link
     * the founder wanted a visit row to open into, showing its history rather than adding
     * another row to the list on reschedule (see {@link #listEvents} for the log itself).
     *
     * @param visitId the visit id
     * @return the full detail
     * @throws VisitNotFoundException if no visit exists with this id (404)
     */
    @Transactional(readOnly = true)
    public AdminVisitDetail getVisitDetail(Long visitId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new VisitNotFoundException(visitId));

        List<VisitServiceItem> services = loadServiceItems(visit.getId());
        List<AppVisitPhoto> photos = loadPhotos(visit.getId());
        String displayName = resolveDisplayName(visit);

        AdminContactDetail customer = subscriberQueryService.findById(visit.getSubscriberId())
                .flatMap(s -> userQueryService.findAdminContactById(s.getUserId()))
                .orElse(null);

        UserSummary technician = visit.getTechnicianId() != null
                ? userQueryService.findSummariesByIds(List.of(visit.getTechnicianId())).get(visit.getTechnicianId())
                : null;

        AdminVisitPropertySummary propertySummary = toPropertySummary(propertyService.findById(visit.getPropertyId()));

        return new AdminVisitDetail(
                visit.getId(),
                visit.getSubscriberId(),
                visit.getTechnicianId(),
                technician != null ? technician.firstName() : null,
                technician != null ? technician.lastName() : null,
                visit.getVisitTemplateId(),
                displayName,
                visit.getScheduledFor(),
                visit.getDurationMinutes(),
                visit.getActualDurationMinutes(),
                visit.getMaterialsCostCents(),
                visit.getStatus().name(),
                visit.getType().name(),
                visit.getCompletionNotes(),
                visit.getMaterialsNotes(),
                visit.getCompletedAt(),
                visit.getCreatedAt(),
                services,
                photos,
                propertySummary,
                customer != null ? customer.firstName() : null,
                customer != null ? customer.lastName() : null,
                customer != null ? customer.email() : null,
                customer != null ? customer.phone() : null
        );
    }

    /**
     * Returns a visit's activity log, newest first, capped at {@value #EVENTS_LIMIT} rows —
     * mirrors {@code SubscriptionAdminService.listSubscriberEvents}'s shape and cap exactly.
     * Backs {@code GET /api/admin/visits/{id}/events}.
     *
     * @param visitId the visit id
     * @return the visit's events, newest first
     * @throws VisitNotFoundException if no visit exists with this id (404)
     */
    @Transactional(readOnly = true)
    public List<VisitEventItem> listEvents(Long visitId) {
        if (!visitRepository.existsById(visitId)) {
            throw new VisitNotFoundException(visitId);
        }
        return visitEventRepository
                .findByVisitIdOrderByCreatedAtDesc(visitId, PageRequest.of(0, EVENTS_LIMIT))
                .stream()
                .map(this::toEventItem)
                .collect(Collectors.toList());
    }

    /**
     * Patches a visit: reschedule, cancel, or assign technician.
     *
     * <p>Reschedule ({@code scheduledFor} present): updates the visit's {@code scheduledFor}
     * (and technician, when supplied) in place, via the state machine's
     * {@link VisitStateMachine#canReschedule} guard, and records a {@code RESCHEDULED}
     * {@code visit_event}.
     *
     * <p>Cancel ({@code status = "CANCELLED"}): transitions via the state machine and
     * records a {@code CANCELLED} {@code visit_event}.
     *
     * <p>Assign technician ({@code technicianUserId} present, no other op): updates the
     * technician without a state transition, and records a {@code TECHNICIAN_ASSIGNED}
     * {@code visit_event} if the technician actually changed.
     *
     * @param visitId     the visit id to patch
     * @param request     the patch request
     * @param adminUserId the authenticated admin's user id (JWT principal), recorded as the
     *                    acting user on any {@code visit_event} this patch produces
     * @return the updated visit representation
     */
    @Transactional
    public AdminVisitResponse patchVisit(Long visitId, AdminPatchVisitRequest request, Long adminUserId) {
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
            return reschedule(visit, request.scheduledFor(), request.technicianUserId(), adminUserId);
        }

        if (isCancel) {
            return cancel(visit, adminUserId);
        }

        if (isTechAssign) {
            Long oldTechnicianId = visit.getTechnicianId();
            visit.setTechnicianId(request.technicianUserId());
            Visit saved = visitRepository.save(visit);
            if (!request.technicianUserId().equals(oldTechnicianId)) {
                recordEvent(saved.getId(), VisitEventType.TECHNICIAN_ASSIGNED,
                        technicianChangePayload(oldTechnicianId, request.technicianUserId()),
                        adminUserId, VisitEventSource.ADMIN);
            }
            log.info("admin_visit_technician_assigned visitId={} technicianId={}",
                    saved.getId(), saved.getTechnicianId());
            return toResponse(saved, loadServiceItems(saved.getId()));
        }

        // No-op patch — return current state.
        return toResponse(visit, loadServiceItems(visit.getId()));
    }

    // ── Private operations ────────────────────────────────────────────────────

    private AdminVisitResponse reschedule(Visit visit, Instant newScheduledFor, Long technicianUserId,
                                          Long adminUserId) {
        Visit saved = rescheduleInternal(visit, newScheduledFor, technicianUserId, adminUserId, VisitEventSource.ADMIN);
        return toResponse(saved, loadServiceItems(saved.getId()));
    }

    /**
     * Reschedules a visit by id in place and returns the updated visit. Used by the customer
     * reschedule-request confirm flow ({@code RescheduleService}) so it can record
     * {@code reschedule_request.confirmed_visit_id} — which, now that reschedule is in
     * place, is simply the same visit id the request was made against. Same-domain service
     * call (visit → visit) — allowed.
     *
     * @param visitId         the visit to reschedule
     * @param newScheduledFor the new start time
     * @param actingUserId    the acting user recorded on the {@code visit_event} (the
     *                        subscriber whose request this fulfills, per
     *                        {@code RescheduleService#confirm})
     * @param source          the {@code visit_event} source ({@code CUSTOMER} for the
     *                        confirm flow)
     * @return the updated visit
     * @throws VisitNotFoundException         if the visit does not exist
     * @throws VisitNotReschedulableException if the visit is not in a reschedulable state
     */
    @Transactional
    public Visit rescheduleVisit(Long visitId, Instant newScheduledFor, Long actingUserId, VisitEventSource source) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new VisitNotFoundException(visitId));
        return rescheduleInternal(visit, newScheduledFor, null, actingUserId, source);
    }

    /**
     * Core reschedule: updates the visit's {@code scheduledFor} (and technician, when
     * supplied) IN PLACE — no replacement visit is created — and records a
     * {@code RESCHEDULED} {@code visit_event} carrying the before/after times. If a
     * technician is supplied and differs from the visit's current technician, also records a
     * {@code TECHNICIAN_ASSIGNED} event for that change.
     *
     * <p>This supersedes the old model (mark old visit RESCHEDULED, create a new SCHEDULED
     * visit copying subscriber/property/template/type/services) that used to live here —
     * that model put an extra row in the admin visit list on every reschedule and broke the
     * "Visit #N" identity an operator uses to refer to a visit (founder's explicit ask). The
     * before/after history that model preserved via the extra row now lives in
     * {@code visit_event} instead, surfaced through the visit's own detail view
     * ({@link #listEvents}).
     *
     * <p>Whether the visit may be rescheduled at all is governed by
     * {@link VisitStateMachine#canReschedule} rather than {@code canTransition(from,
     * RESCHEDULED)}: the visit's status never becomes {@code RESCHEDULED} anymore (it stays
     * {@code SCHEDULED}), so a transition check against that target would be checking
     * something that can no longer happen. Only a {@code SCHEDULED} visit may be
     * rescheduled — an {@code IN_PROGRESS}, {@code COMPLETED}, {@code INCOMPLETE}, or
     * {@code CANCELLED} visit is rejected with a 409 ({@link VisitNotReschedulableException},
     * not {@link IllegalVisitTransitionException} — see that exception's javadoc for why:
     * reporting this as an attempted transition to {@code RESCHEDULED} would tell the
     * operator they tried something no code path can actually do), same as before.
     *
     * <p><strong>This method (and every caller of it) must NEVER write
     * {@code templateOccurrenceYear}.</strong> That field identifies which occurrence of a
     * template a visit IS, not where it currently sits on the calendar — the whole point of
     * the V17 migration is that it survives exactly this method moving {@code scheduledFor}
     * around. Touching it here would silently reintroduce the double-booking bug it fixes
     * (see {@link VisitSchedulingService} class Javadoc "Idempotency").
     */
    private Visit rescheduleInternal(Visit visit, Instant newScheduledFor, Long technicianUserId,
                                     Long actingUserId, VisitEventSource source) {
        if (!stateMachine.canReschedule(visit.getStatus())) {
            throw new VisitNotReschedulableException(visit.getStatus());
        }

        Instant oldScheduledFor = visit.getScheduledFor();
        // Only scheduledFor (and, below, technicianId) move. templateOccurrenceYear is
        // deliberately never read or written here — see this method's javadoc.
        visit.setScheduledFor(newScheduledFor);

        Long oldTechnicianId = visit.getTechnicianId();
        boolean technicianChanged = technicianUserId != null && !technicianUserId.equals(oldTechnicianId);
        if (technicianUserId != null) {
            visit.setTechnicianId(technicianUserId);
        }

        Visit saved = visitRepository.save(visit);

        Map<String, Object> reschedulePayload = new LinkedHashMap<>();
        reschedulePayload.put("from", oldScheduledFor.toString());
        reschedulePayload.put("to", newScheduledFor.toString());
        recordEvent(saved.getId(), VisitEventType.RESCHEDULED, reschedulePayload, actingUserId, source);

        if (technicianChanged) {
            recordEvent(saved.getId(), VisitEventType.TECHNICIAN_ASSIGNED,
                    technicianChangePayload(oldTechnicianId, technicianUserId), actingUserId, source);
        }

        log.info("admin_visit_rescheduled visitId={} source={}", saved.getId(), source);

        return saved;
    }

    private AdminVisitResponse cancel(Visit visit, Long adminUserId) {
        if (!stateMachine.canTransition(visit.getStatus(), VisitStatus.CANCELLED)) {
            throw new IllegalVisitTransitionException(visit.getStatus(), VisitStatus.CANCELLED);
        }
        visit.setStatus(VisitStatus.CANCELLED);
        Visit saved = visitRepository.save(visit);
        recordEvent(saved.getId(), VisitEventType.CANCELLED, null, adminUserId, VisitEventSource.ADMIN);
        log.info("admin_visit_cancelled visitId={} subscriberId={}", saved.getId(), saved.getSubscriberId());
        return toResponse(saved, loadServiceItems(saved.getId()));
    }

    /**
     * Persists a {@code visit_event} row. {@code payloadFields}, when non-empty, is
     * serialized to JSON via a {@link LinkedHashMap} purely for readable key order when the
     * payload is inspected directly — JSON object key order carries no semantic meaning here
     * (mirrors {@code SubscriptionAdminService#serializeAdminCancelPayload}).
     *
     * @param payloadFields the event's specifics, or {@code null}/empty for none
     */
    private void recordEvent(Long visitId, String eventType, Map<String, Object> payloadFields,
                             Long byUserId, VisitEventSource source) {
        String payload = (payloadFields != null && !payloadFields.isEmpty())
                ? objectMapper.writeValueAsString(payloadFields)
                : null;
        visitEventRepository.save(new VisitEvent(visitId, eventType, payload, byUserId, source));
    }

    private Map<String, Object> technicianChangePayload(Long oldTechnicianId, Long newTechnicianId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", oldTechnicianId);
        payload.put("to", newTechnicianId);
        return payload;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Maps a {@link Visit} to its admin-list-row DTO, resolving customer identity and
     * property address from the pre-loaded, page-batched maps built by {@link #listVisits}
     * (never a per-row query — see that method's javadoc).
     */
    private AdminVisitListItem toListItem(Visit v, Map<Long, Subscriber> subscribersById,
                                          Map<Long, AdminContactDetail> contactsByUserId,
                                          Map<Long, Property> propertiesById) {
        Subscriber subscriber = subscribersById.get(v.getSubscriberId());
        AdminContactDetail contact = subscriber != null ? contactsByUserId.get(subscriber.getUserId()) : null;
        Property property = propertiesById.get(v.getPropertyId());

        return new AdminVisitListItem(
                v.getId(),
                v.getSubscriberId(),
                v.getPropertyId(),
                v.getTechnicianId(),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getActualDurationMinutes(),
                v.getMaterialsCostCents(),
                v.getStatus().name(),
                v.getType().name(),
                v.getCompletedAt(),
                v.getCreatedAt(),
                contact != null ? contact.firstName() : null,
                contact != null ? contact.lastName() : null,
                contact != null ? contact.phone() : null,
                property != null ? property.getStreetAddress() : null,
                property != null ? property.getCity() : null
        );
    }

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

    private VisitEventItem toEventItem(VisitEvent event) {
        JsonNode payload = null;
        if (event.getPayload() != null) {
            try {
                payload = objectMapper.readTree(event.getPayload());
            } catch (JacksonException e) {
                log.warn("visit_event_payload_unparseable visitId={} eventId={}",
                        event.getVisitId(), event.getId());
            }
        }
        return new VisitEventItem(
                event.getId(),
                event.getEventType(),
                event.getSource().name(),
                event.getCreatedAt(),
                event.getByUserId(),
                payload);
    }

    private AdminVisitPropertySummary toPropertySummary(Property property) {
        if (property == null) {
            return null;
        }
        return new AdminVisitPropertySummary(
                property.getId(),
                property.getStreetAddress(),
                property.getUnit(),
                property.getCity(),
                property.getPostalCode());
    }

    private String resolveDisplayName(Visit visit) {
        Map<Long, String> templateNames = Map.of();
        if (visit.getVisitTemplateId() != null) {
            String name = visitTemplateRepository.findById(visit.getVisitTemplateId())
                    .map(VisitTemplate::getName)
                    .orElse(null);
            if (name != null) {
                templateNames = Map.of(visit.getVisitTemplateId(), name);
            }
        }
        return visit.resolveDisplayName(templateNames);
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
                .map(vs -> VisitServiceItem.from(vs, nameById))
                .collect(Collectors.toList());
    }

    /**
     * Loads the photos for a visit, signing each download URL via
     * {@link StorageService#presignDownload}. Mirrors {@code VisitAppService#loadPhotos}'s
     * graceful-degradation semantics exactly (R2 unconfigured or a bad key skips that photo
     * rather than 500ing the whole detail response) — see that method's javadoc for detail.
     */
    private List<AppVisitPhoto> loadPhotos(Long visitId) {
        List<VisitPhoto> rows = visitPhotoRepository.findByVisitIdOrderByIdAsc(visitId);
        if (rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() > MAX_PHOTOS_PER_VISIT) {
            rows = rows.subList(0, MAX_PHOTOS_PER_VISIT);
        }
        List<AppVisitPhoto> photos = new ArrayList<>(rows.size());
        for (VisitPhoto row : rows) {
            try {
                String url = storageService.presignDownload(row.getStorageKey());
                if (url == null || url.isBlank()) {
                    continue;
                }
                photos.add(new AppVisitPhoto(url, row.getCaption(), row.getTakenAt()));
            } catch (StorageUnavailableException e) {
                log.debug("admin_visit_photo_presign_unavailable visitId={} photoId={}", visitId, row.getId());
            } catch (Exception e) {
                log.warn("admin_visit_photo_presign_failed visitId={} photoId={}", visitId, row.getId());
            }
        }
        return photos;
    }
}
