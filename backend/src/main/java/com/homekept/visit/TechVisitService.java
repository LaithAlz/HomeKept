package com.homekept.visit;

import com.homekept.analytics.AnalyticsEvent;
import com.homekept.analytics.AnalyticsService;
import com.homekept.catalog.CatalogService;
import com.homekept.property.PropertyService;
import com.homekept.storage.StorageService;
import com.homekept.subscription.Subscriber;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.subscription.SubscriberStatus;
import com.homekept.visit.dto.FlagResponse;
import com.homekept.visit.dto.TechCompleteVisitRequest;
import com.homekept.visit.dto.TechCompleteVisitResponse;
import com.homekept.visit.dto.TechConfirmPhotoRequest;
import com.homekept.visit.dto.TechCreateFlagRequest;
import com.homekept.visit.dto.TechIncompleteVisitRequest;
import com.homekept.visit.dto.TechIncompleteVisitResponse;
import com.homekept.visit.dto.TechPatchServiceRequest;
import com.homekept.visit.dto.TechPatchTodoRequest;
import com.homekept.visit.dto.TechPhotoResponse;
import com.homekept.visit.dto.TechPhotoUploadUrlResponse;
import com.homekept.visit.dto.TechStartVisitResponse;
import com.homekept.visit.dto.TechVisitListItem;
import com.homekept.visit.dto.TodoResponse;
import com.homekept.visit.dto.VisitServiceItem;
import com.homekept.visit.exception.IllegalVisitTransitionException;
import com.homekept.visit.exception.InvalidVisitRequestException;
import com.homekept.visit.exception.SubscriberNotActiveException;
import com.homekept.visit.exception.VisitNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the technician app (all {@code /api/tech/*} endpoints).
 *
 * <h2>Security contract — every method enforces "assigned to THIS tech"</h2>
 * <p>Every operation takes the authenticated technician's {@code userId} (from the JWT
 * principal) and verifies that the visit's {@code technicianId} matches before doing
 * anything. Mismatches return 404 per the ownership-failure rule (don't leak existence).
 *
 * <h2>Access notes</h2>
 * <p>{@link #getTodaysVisits} is the <strong>only</strong> call-site where access notes
 * are decrypted. The plaintext is mapped directly into the response DTO and NEVER stored
 * in a local variable with a name that would tempt a log call, and NEVER logged here or
 * anywhere else.
 *
 * <h2>State machine</h2>
 * <p>Every visit status write goes through {@link VisitStateMachine#canTransition}.
 * No direct status writes anywhere.
 *
 * <h2>Domain boundaries</h2>
 * <p>Reads property data via {@link PropertyService} (property domain's service).
 * Reads service names via {@link CatalogService} (catalog domain's service).
 * Never calls those domains' repositories directly.
 */
@Service
public class TechVisitService {

    private static final Logger log = LoggerFactory.getLogger(TechVisitService.class);

    /**
     * Allowed MIME type prefixes for photo uploads.
     * Validated to prevent arbitrary content being PUT to R2 under the visits/ prefix.
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif", "image/webp"
    );

    /** Follow-up visit scheduled N days from now when a visit is marked INCOMPLETE. */
    private static final int FOLLOW_UP_DAYS_AHEAD = 7;

    private final VisitRepository visitRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final VisitPhotoRepository visitPhotoRepository;
    private final FlagRepository flagRepository;
    private final TodoItemRepository todoItemRepository;
    private final VisitStateMachine stateMachine;
    private final PropertyService propertyService;
    private final CatalogService catalogService;
    private final StorageService storageService;
    private final VisitReportNotifier visitReportNotifier;
    private final HealthScoreService healthScoreService;
    private final SubscriberQueryService subscriberQueryService;
    private final AnalyticsService analytics;
    private final ZoneId renderZoneId;

    public TechVisitService(VisitRepository visitRepository,
                            VisitServiceRepository visitServiceRepository,
                            VisitPhotoRepository visitPhotoRepository,
                            FlagRepository flagRepository,
                            TodoItemRepository todoItemRepository,
                            VisitStateMachine stateMachine,
                            PropertyService propertyService,
                            CatalogService catalogService,
                            StorageService storageService,
                            VisitReportNotifier visitReportNotifier,
                            HealthScoreService healthScoreService,
                            SubscriberQueryService subscriberQueryService,
                            AnalyticsService analytics,
                            ZoneId renderZoneId) {
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.visitPhotoRepository = visitPhotoRepository;
        this.flagRepository = flagRepository;
        this.todoItemRepository = todoItemRepository;
        this.stateMachine = stateMachine;
        this.propertyService = propertyService;
        this.catalogService = catalogService;
        this.storageService = storageService;
        this.visitReportNotifier = visitReportNotifier;
        this.healthScoreService = healthScoreService;
        this.subscriberQueryService = subscriberQueryService;
        this.analytics = analytics;
        this.renderZoneId = renderZoneId;
    }

    // ── GET /api/tech/visits/today ────────────────────────────────────────────

    /**
     * Returns the authenticated technician's visits scheduled for today in
     * America/Toronto, each with property address, decrypted access notes, and
     * the full checklist.
     *
     * <p><strong>ACCESS NOTES:</strong> decrypted here and mapped directly to the DTO.
     * The plaintext is never stored in a named local variable intended for logging.
     * Never log the return value of this method or any element of the returned list.
     *
     * @param techUserId the authenticated technician's user id (from JWT principal)
     * @return the list of today's visits for this technician
     */
    @Transactional(readOnly = true)
    public List<TechVisitListItem> getTodaysVisits(Long techUserId) {
        // Compute today's start and end in America/Toronto, stored as UTC Instants.
        ZonedDateTime todayStart = LocalDate.now(renderZoneId).atStartOfDay(renderZoneId);
        ZonedDateTime todayEnd = todayStart.plusDays(1);

        List<Visit> visits = visitRepository.findByTechnicianIdAndScheduledForBetween(
                techUserId,
                todayStart.toInstant(),
                todayEnd.toInstant()
        );

        if (visits.isEmpty()) {
            return List.of();
        }

        // Batch-load service names to avoid N+1.
        List<Long> visitIds = visits.stream().map(Visit::getId).collect(Collectors.toList());
        Map<Long, List<VisitService>> servicesByVisitId = loadServicesByVisitIds(visitIds);
        List<Long> allServiceIds = servicesByVisitId.values().stream()
                .flatMap(List::stream)
                .map(VisitService::getServiceId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> serviceNames = allServiceIds.isEmpty()
                ? Map.of()
                : catalogService.getServiceNamesByIds(allServiceIds);

        List<TechVisitListItem> result = new ArrayList<>();
        for (Visit visit : visits) {
            var property = propertyService.findById(visit.getPropertyId());
            if (property == null) {
                log.warn("tech_day_sheet_property_missing visitId={} propertyId={}",
                        visit.getId(), visit.getPropertyId());
                continue;
            }

            // Decrypt access notes — the ONLY decryption call-site in the codebase.
            // NEVER log the returned string. NEVER store it in a variable named for logging.
            String decryptedNotes = propertyService.decryptAccessNotes(visit.getPropertyId());

            List<VisitServiceItem> services = toServiceItems(
                    servicesByVisitId.getOrDefault(visit.getId(), List.of()), serviceNames);

            // Todos folded into THIS visit (TodoItem.visitId == visit.id) — includes items
            // already marked DONE/DECLINED so the tech can see what was already handled.
            List<TodoResponse> todos = todoItemRepository.findByVisitId(visit.getId()).stream()
                    .map(this::toTodoResponse)
                    .collect(Collectors.toList());

            // OPEN flags on this visit's subscriber — prior observations shown for context
            // (flags are subscriber-scoped; see TechVisitListItem javadoc).
            List<FlagResponse> flags = flagRepository
                    .findBySubscriberIdAndStatusOrderByCreatedAtDesc(visit.getSubscriberId(), FlagStatus.OPEN)
                    .stream()
                    .map(this::toFlagResponse)
                    .collect(Collectors.toList());

            result.add(new TechVisitListItem(
                    visit.getId(),
                    resolveVisitName(visit),
                    visit.getScheduledFor(),
                    visit.getDurationMinutes(),
                    visit.getStatus().name(),
                    visit.getType().name(),
                    property.getStreetAddress(),
                    property.getUnit(),
                    property.getCity(),
                    property.getPostalCode(),
                    decryptedNotes,  // plaintext access notes — NEVER log this value
                    services,
                    todos,
                    flags
            ));
        }
        return result;
    }

    // ── POST /api/tech/visits/{id}/start ─────────────────────────────────────

    /**
     * Transitions a visit from SCHEDULED to IN_PROGRESS.
     * Verifies the visit is assigned to this technician (→ 404 if not).
     *
     * @param visitId    the visit id
     * @param techUserId the authenticated technician's user id
     * @return the updated visit status
     */
    @Transactional
    public TechStartVisitResponse startVisit(Long visitId, Long techUserId) {
        Visit visit = requireOwnedVisit(visitId, techUserId);

        if (!stateMachine.canTransition(visit.getStatus(), VisitStatus.IN_PROGRESS)) {
            throw new IllegalVisitTransitionException(visit.getStatus(), VisitStatus.IN_PROGRESS);
        }

        // Don't perform (free) service for a subscriber who is no longer paying. Guard on
        // START only — a visit already IN_PROGRESS is allowed to finish even if the customer
        // pauses mid-visit. Cross-domain status read goes through SubscriberQueryService (a
        // subscription service), never its repository. A missing subscriber is treated as
        // not-serviceable (fail closed). On resume → ACTIVE the visit becomes startable again.
        //
        // Serviceable = ACTIVE or PAYMENT_ISSUE. PAYMENT_ISSUE is dunning grace: Stripe is
        // retrying a card on an otherwise-active subscription, so we still perform the visit
        // rather than penalise a transient decline (founder decision — flip by editing
        // SERVICEABLE_STATUSES). PAUSED / CANCELLED / PENDING_ACTIVATION are blocked.
        //
        // NOTE: this keys on SUBSCRIPTION status, not per-visit payment. When prepaid EXTRA /
        // WARRANTY visits land (#60; VisitType.EXTRA/WARRANTY exist but nothing creates them
        // yet), this guard must also allow an independently-paid visit for a paused subscriber.
        SubscriberStatus subStatus = subscriberQueryService.findById(visit.getSubscriberId())
                .map(Subscriber::getStatus)
                .orElse(null);
        if (subStatus == null || !subStatus.isServiceable()) {
            log.info("tech_visit_start_blocked visitId={} subscriberId={} subscriberStatus={}",
                    visit.getId(), visit.getSubscriberId(), subStatus);
            throw new SubscriberNotActiveException(String.valueOf(subStatus));
        }

        visit.setStatus(VisitStatus.IN_PROGRESS);
        Visit saved = visitRepository.save(visit);

        log.info("tech_visit_started visitId={} techUserId={}", saved.getId(), techUserId);
        return new TechStartVisitResponse(saved.getId(), saved.getStatus().name());
    }

    // ── PATCH /api/tech/visits/{id}/services/{visitServiceId} ────────────────

    /**
     * Ticks or unticks a checklist item. The visit service row must belong to this visit,
     * and the visit must be assigned to this technician.
     *
     * @param visitId        the visit id
     * @param visitServiceId the checklist item id
     * @param request        completed flag and optional notes
     * @param techUserId     the authenticated technician's user id
     * @return the updated checklist item
     */
    @Transactional
    public VisitServiceItem patchService(Long visitId, Long visitServiceId,
                                         TechPatchServiceRequest request, Long techUserId) {
        requireOwnedVisit(visitId, techUserId);

        VisitService vs = visitServiceRepository.findById(visitServiceId)
                .orElseThrow(() -> new VisitNotFoundException(visitServiceId));

        // Ensure the checklist item belongs to this visit (prevents cross-visit IDOR).
        if (!vs.getVisitId().equals(visitId)) {
            throw new VisitNotFoundException(visitServiceId);
        }

        vs.setCompleted(request.completed());
        vs.setTechnicianNotes(request.technicianNotes());
        if (request.completed()) {
            vs.setCompletedAt(Instant.now());
        } else {
            vs.setCompletedAt(null);
        }
        VisitService saved = visitServiceRepository.save(vs);

        log.info("tech_service_patched visitId={} visitServiceId={} completed={}",
                visitId, visitServiceId, request.completed());

        // Resolve service name for the response.
        Map<Long, String> names = catalogService.getServiceNamesByIds(List.of(saved.getServiceId()));
        return new VisitServiceItem(
                saved.getId(),
                saved.getServiceId(),
                names.getOrDefault(saved.getServiceId(), "Unknown service"),
                saved.getSource().name(),
                saved.isCompleted(),
                saved.getTechnicianNotes()
        );
    }

    // ── POST /api/tech/visits/{id}/photos/upload-url ─────────────────────────

    /**
     * Generates a 15-minute signed R2 PUT URL for the technician to upload a photo
     * directly to R2.
     *
     * <p>The storage key is server-generated as {@code visits/{visitId}/{uuid}} —
     * the client NEVER supplies arbitrary keys (prevents path traversal / overwrite).
     *
     * <p>The size cap is enforced twice: the {@code contentLength} is already bounded by
     * {@code @Max} on the request DTO (rejects an oversized presign request with 400), and
     * it is signed into the PUT URL so R2 rejects a body that does not match — a client
     * cannot upload more bytes than it declared.
     *
     * @param visitId       the visit id
     * @param contentType   the MIME type of the image to be uploaded
     * @param contentLength the exact byte size of the image (validated + signed)
     * @param techUserId    the authenticated technician's user id
     * @return the signed upload URL and the storage key
     */
    @Transactional(readOnly = true)
    public TechPhotoUploadUrlResponse presignUpload(Long visitId, String contentType,
                                                    long contentLength, Long techUserId) {
        requireOwnedVisit(visitId, techUserId);

        // Validate content type before touching R2.
        String normalised = contentType == null ? "" : contentType.toLowerCase().trim();
        if (!ALLOWED_IMAGE_TYPES.contains(normalised)) {
            throw new InvalidVisitRequestException(
                    "contentType must be an image type (jpeg, png, heic, heif, or webp). Got: " + contentType);
        }

        // Server-generates the key — client cannot choose arbitrary paths.
        String storageKey = "visits/" + visitId + "/" + UUID.randomUUID();

        StorageService.PresignedUpload upload =
                storageService.presignUpload(storageKey, normalised, contentLength);

        log.debug("tech_photo_upload_url_generated visitId={} storageKey={} contentLength={}",
                visitId, storageKey, contentLength);
        return new TechPhotoUploadUrlResponse(upload.uploadUrl(), upload.storageKey());
    }

    // ── POST /api/tech/visits/{id}/photos ────────────────────────────────────

    /**
     * Confirms a photo upload and persists the {@link VisitPhoto} row.
     *
     * <p>The storage key is validated to start with {@code visits/{visitId}/} to prevent
     * a tech from attaching another visit's (or another resource's) key to this visit.
     *
     * @param visitId    the visit id
     * @param request    storage key and optional caption
     * @param techUserId the authenticated technician's user id
     * @return the persisted photo record
     */
    @Transactional
    public TechPhotoResponse confirmPhoto(Long visitId, TechConfirmPhotoRequest request,
                                          Long techUserId) {
        requireOwnedVisit(visitId, techUserId);

        // Validate the storage key belongs to this visit — prevents cross-visit key injection.
        String expectedPrefix = "visits/" + visitId + "/";
        if (!request.storageKey().startsWith(expectedPrefix)) {
            throw new InvalidVisitRequestException(
                    "storageKey must start with '" + expectedPrefix + "'");
        }

        VisitPhoto photo = new VisitPhoto(
                visitId,
                request.storageKey(),
                request.caption(),
                null  // takenAt — not provided in the confirm step
        );
        VisitPhoto saved = visitPhotoRepository.save(photo);

        log.info("tech_photo_confirmed visitId={} photoId={}", visitId, saved.getId());
        return toPhotoResponse(saved);
    }

    // ── POST /api/tech/visits/{id}/flags ─────────────────────────────────────

    /**
     * Creates an OPEN {@link Flag} on the visit's subscriber (the observe → flag loop).
     *
     * @param visitId    the visit id (and origin_visit_id on the flag)
     * @param request    flag body, severity, and optional photo key
     * @param techUserId the authenticated technician's user id
     * @return the created flag
     */
    @Transactional
    public FlagResponse createFlag(Long visitId, TechCreateFlagRequest request, Long techUserId) {
        Visit visit = requireOwnedVisit(visitId, techUserId);

        Flag flag = new Flag(
                visit.getSubscriberId(),
                visitId,
                request.body(),
                FlagSeverity.valueOf(request.severity())
        );
        if (request.photoStorageKey() != null && !request.photoStorageKey().isBlank()) {
            // Same prefix guard as confirmPhoto: a flag's photo must belong to THIS visit's
            // object prefix, so a future photo-download path can't be tricked into reading
            // another visit's / an arbitrary R2 object via a forged flag key.
            String expectedPrefix = "visits/" + visitId + "/";
            if (!request.photoStorageKey().startsWith(expectedPrefix)) {
                throw new InvalidVisitRequestException(
                        "photoStorageKey must start with '" + expectedPrefix + "'");
            }
            flag.setPhotoStorageKey(request.photoStorageKey());
        }
        Flag saved = flagRepository.save(flag);

        log.info("tech_flag_created flagId={} visitId={} severity={} subscriberId={}",
                saved.getId(), visitId, saved.getSeverity(), saved.getSubscriberId());

        // Analytics (arch doc §5.7) — attributed to the acting technician. Severity is an
        // enum; no PII (the flag body is never sent). Fires after commit.
        analytics.capture(techUserId, AnalyticsEvent.FLAG_CREATED,
                Map.of("severity", saved.getSeverity().name()));

        return toFlagResponse(saved);
    }

    // ── PATCH /api/tech/todos/{id} ────────────────────────────────────────────

    /**
     * Updates a todo item's status to DONE or DECLINED in the field.
     *
     * <p><strong>Authz:</strong> the todo's subscriber must have an active visit
     * (SCHEDULED or IN_PROGRESS) assigned to this technician. This is the MVP
     * simplification — at Stage 3, this becomes "subscriber has a visit assigned to this
     * tech today specifically". Documented here for the safety reviewer.
     *
     * @param todoId     the todo item id
     * @param request    the new status (DONE or DECLINED) and optional note
     * @param techUserId the authenticated technician's user id
     * @return the updated todo item
     */
    @Transactional
    public TodoResponse patchTodo(Long todoId, TechPatchTodoRequest request, Long techUserId) {
        TodoItem todo = todoItemRepository.findById(todoId)
                .orElseThrow(() -> new VisitNotFoundException(todoId)); // 404 per ownership rule

        // Authz: verify this tech has an active visit for this subscriber.
        List<Visit> activeVisits = visitRepository.findActiveVisitsBySubscriberAndTechnician(
                todo.getSubscriberId(), techUserId,
                List.of(VisitStatus.SCHEDULED, VisitStatus.IN_PROGRESS));
        if (activeVisits.isEmpty()) {
            // No active visit → treat as not-found (ownership-failure rule → 404 not 403).
            throw new VisitNotFoundException(todoId);
        }

        TodoItemStatus newStatus = TodoItemStatus.valueOf(request.status());

        if (newStatus == TodoItemStatus.DECLINED) {
            if (request.note() == null || request.note().isBlank()) {
                throw new InvalidVisitRequestException(
                        "A note is required when declining a todo item");
            }
            todo.setDeclineNote(request.note());
        }

        todo.setStatus(newStatus);
        TodoItem saved = todoItemRepository.save(todo);

        log.info("tech_todo_patched todoId={} status={} techUserId={}",
                saved.getId(), newStatus, techUserId);
        return toTodoResponse(saved);
    }

    // ── POST /api/tech/visits/{id}/complete ──────────────────────────────────

    /**
     * Transitions a visit from IN_PROGRESS to COMPLETED.
     * Captures unit-economics data (duration + materials cost).
     * Fires the visit-report notification via the stub notifier.
     *
     * <p>DEFER: health_score_snapshot computation (#53) — flagged here.
     *
     * @param visitId    the visit id
     * @param request    completion data including actual duration and materials cost
     * @param techUserId the authenticated technician's user id
     * @return the updated visit
     */
    @Transactional
    public TechCompleteVisitResponse completeVisit(Long visitId,
                                                    TechCompleteVisitRequest request,
                                                    Long techUserId) {
        Visit visit = requireOwnedVisit(visitId, techUserId);

        if (!stateMachine.canTransition(visit.getStatus(), VisitStatus.COMPLETED)) {
            throw new IllegalVisitTransitionException(visit.getStatus(), VisitStatus.COMPLETED);
        }

        visit.setStatus(VisitStatus.COMPLETED);
        visit.setActualDurationMinutes(request.actualDurationMinutes());
        visit.setMaterialsCostCents(request.materialsCostCents());
        visit.setCompletionNotes(request.completionNotes());
        visit.setMaterialsNotes(request.materialsNotes());
        visit.setCompletedAt(Instant.now());

        Visit saved = visitRepository.save(visit);

        // Snapshot the Home Health Score now the visit is COMPLETED (#53) — gives the
        // dashboard delta a prior value to compare future reads against.
        healthScoreService.snapshotOnCompletion(saved.getSubscriberId());

        // Fire visit-report notification (real SendGrid email — the notification slice).
        visitReportNotifier.sendVisitReport(saved);

        log.info("tech_visit_completed visitId={} subscriberId={} durationMin={} materialsCents={}",
                saved.getId(), saved.getSubscriberId(),
                saved.getActualDurationMinutes(), saved.getMaterialsCostCents());

        // Analytics (arch doc §5.7) — attributed to the acting technician. IDs/counts only,
        // no PII. Fires after this transaction commits (or no-ops without a PostHog key).
        Map<String, Object> visitProps = new LinkedHashMap<>();
        visitProps.put("visit_template", saved.getVisitTemplateId());
        visitProps.put("duration_actual", saved.getActualDurationMinutes());
        visitProps.put("services_count",
                visitServiceRepository.findByVisitIdOrderByIdAsc(saved.getId()).size());
        visitProps.put("photos_count",
                visitPhotoRepository.findByVisitIdOrderByIdAsc(saved.getId()).size());
        analytics.capture(techUserId, AnalyticsEvent.VISIT_COMPLETED, visitProps);

        return new TechCompleteVisitResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getCompletedAt(),
                saved.getActualDurationMinutes(),
                saved.getMaterialsCostCents()
        );
    }

    // ── POST /api/tech/visits/{id}/incomplete ────────────────────────────────

    /**
     * Transitions a visit from IN_PROGRESS to INCOMPLETE and auto-creates a follow-up
     * SCHEDULED visit 7 days from now (same subscriber, property, template, type,
     * and services — the reschedule pattern from {@link VisitAdminService}).
     *
     * @param visitId    the visit id
     * @param request    the reason the visit could not be completed
     * @param techUserId the authenticated technician's user id
     * @return the updated visit and the id/date of the follow-up
     */
    @Transactional
    public TechIncompleteVisitResponse incompleteVisit(Long visitId,
                                                        TechIncompleteVisitRequest request,
                                                        Long techUserId) {
        Visit visit = requireOwnedVisit(visitId, techUserId);

        if (!stateMachine.canTransition(visit.getStatus(), VisitStatus.INCOMPLETE)) {
            throw new IllegalVisitTransitionException(visit.getStatus(), VisitStatus.INCOMPLETE);
        }

        // Store the reason as completion_notes (repurposed — these are the "why not done" notes).
        visit.setCompletionNotes(request.reason());
        visit.setStatus(VisitStatus.INCOMPLETE);
        visitRepository.save(visit);

        log.info("tech_visit_incomplete visitId={} subscriberId={}", visitId, visit.getSubscriberId());

        // Auto-create follow-up visit: 7 days from now, noon Toronto time.
        Instant followUpScheduledFor = LocalDate.now(renderZoneId)
                .plusDays(FOLLOW_UP_DAYS_AHEAD)
                .atTime(12, 0)
                .atZone(renderZoneId)
                .toInstant();

        Visit followUp = new Visit(
                visit.getSubscriberId(),
                visit.getPropertyId(),
                visit.getVisitTemplateId(),
                followUpScheduledFor,
                visit.getDurationMinutes(),
                visit.getType()
        );
        // Carry the technician assignment forward.
        followUp.setTechnicianId(visit.getTechnicianId());

        Visit savedFollowUp = visitRepository.save(followUp);

        // Copy the original visit's checklist (source preserved — same pattern as reschedule).
        List<VisitService> originalServices = visitServiceRepository.findByVisitIdOrderByIdAsc(visitId);
        for (VisitService vs : originalServices) {
            visitServiceRepository.save(new VisitService(
                    savedFollowUp.getId(), vs.getServiceId(), vs.getSource()
            ));
        }

        log.info("tech_visit_followup_created followUpVisitId={} subscriberId={} scheduledFor={}",
                savedFollowUp.getId(), savedFollowUp.getSubscriberId(), followUpScheduledFor);

        return new TechIncompleteVisitResponse(
                visit.getId(),
                VisitStatus.INCOMPLETE.name(),
                savedFollowUp.getId(),
                followUpScheduledFor
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads the visit and verifies it is assigned to {@code techUserId}.
     * Returns 404 if the visit does not exist or is assigned to a different technician
     * (ownership-failure rule — don't leak existence of another tech's visit).
     */
    private Visit requireOwnedVisit(Long visitId, Long techUserId) {
        return visitRepository.findByIdAndTechnicianId(visitId, techUserId)
                .orElseThrow(() -> {
                    log.debug("tech_visit_not_found_or_not_assigned visitId={} techUserId={}",
                            visitId, techUserId);
                    return new VisitNotFoundException(visitId);
                });
    }

    /**
     * Bulk-loads all VisitService rows for the given visit ids, grouped by visit id.
     * Single query — avoids N+1 on the day-sheet.
     */
    private Map<Long, List<VisitService>> loadServicesByVisitIds(List<Long> visitIds) {
        // Use per-visit queries at MVP scale; at 500+ visits/year, consider a JPQL IN query.
        Map<Long, List<VisitService>> result = new HashMap<>();
        for (Long id : visitIds) {
            result.put(id, visitServiceRepository.findByVisitIdOrderByIdAsc(id));
        }
        return result;
    }

    private List<VisitServiceItem> toServiceItems(List<VisitService> rows,
                                                   Map<Long, String> nameById) {
        return rows.stream()
                .map(vs -> new VisitServiceItem(
                        vs.getId(),
                        vs.getServiceId(),
                        nameById.getOrDefault(vs.getServiceId(), "Unknown service"),
                        vs.getSource().name(),
                        vs.isCompleted(),
                        vs.getTechnicianNotes()
                ))
                .collect(Collectors.toList());
    }

    private String resolveVisitName(Visit v) {
        return switch (v.getType()) {
            case EXTRA -> "Extra visit";
            case WALKTHROUGH -> "Walk-through";
            case WARRANTY -> "Warranty visit";
            case ROUTINE -> "Routine visit";
        };
    }

    private TechPhotoResponse toPhotoResponse(VisitPhoto p) {
        return new TechPhotoResponse(
                p.getId(),
                p.getVisitId(),
                p.getStorageKey(),
                p.getCaption(),
                p.getTakenAt(),
                p.getCreatedAt()
        );
    }

    private FlagResponse toFlagResponse(Flag f) {
        return new FlagResponse(
                f.getId(),
                f.getSubscriberId(),
                f.getOriginVisitId(),
                f.getBody(),
                f.getSeverity().name(),
                f.getStatus().name(),
                f.getPhotoStorageKey(),
                f.getCreatedAt()
        );
    }

    private TodoResponse toTodoResponse(TodoItem t) {
        return new TodoResponse(
                t.getId(),
                t.getSubscriberId(),
                t.getBody(),
                t.getStatus().name(),
                t.getVisitId(),
                t.getDeclineNote(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
