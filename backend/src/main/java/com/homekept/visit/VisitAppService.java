package com.homekept.visit;

import com.homekept.catalog.CatalogService;
import com.homekept.common.Pagination;
import com.homekept.storage.StorageService;
import com.homekept.storage.StorageUnavailableException;
import com.homekept.subscription.SubscriberQueryService;
import com.homekept.visit.dto.AppVisitDetail;
import com.homekept.visit.dto.AppVisitListItem;
import com.homekept.visit.dto.AppVisitPhoto;
import com.homekept.visit.dto.VisitServiceItem;
import com.homekept.visit.exception.InvalidVisitRequestException;
import com.homekept.visit.exception.VisitNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customer-facing visit queries (the app-side of the visit domain).
 *
 * <p>Rules:
 * <ul>
 *   <li>Every subscriber is resolved from the authenticated user id — never from a
 *       subscriber id passed by the caller (prevents IDOR).</li>
 *   <li>Ownership failures → 404, not 403 (don't leak existence).</li>
 *   <li>No PII in logs — subscriber id and visit id only.</li>
 * </ul>
 *
 * <h2>Domain boundaries</h2>
 * <p>Resolves the subscriber via {@link SubscriberQueryService} (subscription domain's
 * service) — never by calling the subscription repository directly.
 *
 * <p>Service names for display are resolved via {@link CatalogService#getServiceNamesByIds}
 * — never by calling the catalog repository directly.
 *
 * <p>Photo download URLs are signed via {@link StorageService#presignDownload} (the storage
 * domain's service) — never by reading R2 credentials or building URLs here.
 *
 * <p>Whether a visit has a pending reschedule request is resolved via
 * {@link RescheduleService#hasPendingRequest} (detail) / {@link RescheduleService#pendingRequestVisitIds}
 * (list, batch — one query for the whole page) — {@code RescheduleService} lives in this same
 * domain (visit), so this is a same-domain service call, not a cross-domain one.
 */
@Service
public class VisitAppService {

    private static final Logger log = LoggerFactory.getLogger(VisitAppService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Defense-in-depth cap — photo count is technician-controlled, not customer-controlled,
     * but this bounds the number of presign calls a single request can trigger. */
    private static final int MAX_PHOTOS_PER_VISIT = 50;

    private final VisitRepository visitRepository;
    private final VisitServiceRepository visitServiceRepository;
    private final VisitPhotoRepository visitPhotoRepository;
    private final SubscriberQueryService subscriberQueryService;
    private final CatalogService catalogService;
    private final VisitTemplateRepository visitTemplateRepository;
    private final StorageService storageService;
    private final RescheduleService rescheduleService;

    public VisitAppService(VisitRepository visitRepository,
                           VisitServiceRepository visitServiceRepository,
                           VisitPhotoRepository visitPhotoRepository,
                           SubscriberQueryService subscriberQueryService,
                           CatalogService catalogService,
                           VisitTemplateRepository visitTemplateRepository,
                           StorageService storageService,
                           RescheduleService rescheduleService) {
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.visitPhotoRepository = visitPhotoRepository;
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.visitTemplateRepository = visitTemplateRepository;
        this.storageService = storageService;
        this.rescheduleService = rescheduleService;
    }

    /**
     * Returns a cursor-paginated list of visits for the authenticated subscriber.
     * Ordered by scheduledFor descending (newest/soonest first).
     *
     * @param userId authenticated user id (from JWT principal)
     * @param status optional status filter
     * @param cursor optional id cursor (exclusive upper bound)
     * @param limit  optional page size (default 20, capped at 100)
     * @return paginated visit list
     */
    @Transactional(readOnly = true)
    public List<AppVisitListItem> listVisits(Long userId, String status, Long cursor, Integer limit) {
        Long subscriberId = resolveSubscriberId(userId);
        int pageSize = Pagination.resolveLimit(limit, DEFAULT_PAGE_SIZE, 100);
        PageRequest pageable = PageRequest.of(0, pageSize);

        List<Visit> visits;
        if (status != null && !status.isBlank()) {
            VisitStatus visitStatus = parseStatus(status);
            visits = (cursor != null)
                    ? visitRepository.findBySubscriberIdAndStatusAndIdLessThanOrderByScheduledForDescIdDesc(
                            subscriberId, visitStatus, cursor, pageable)
                    : visitRepository.findBySubscriberIdAndStatusOrderByScheduledForDescIdDesc(
                            subscriberId, visitStatus, pageable);
        } else {
            visits = (cursor != null)
                    ? visitRepository.findBySubscriberIdAndIdLessThanOrderByScheduledForDescIdDesc(
                            subscriberId, cursor, pageable)
                    : visitRepository.findBySubscriberIdOrderByScheduledForDescIdDesc(
                            subscriberId, pageable);
        }

        List<Long> visitIds = visits.stream().map(Visit::getId).collect(Collectors.toList());
        Map<Long, String> templateNames = loadTemplateNames(visits);
        Set<Long> pendingVisitIds = rescheduleService.pendingRequestVisitIds(visitIds);
        Map<Long, List<VisitServiceItem>> servicesByVisitId = loadServiceItemsByVisitIds(visitIds);
        return visits.stream()
                .map(v -> toListItem(v, servicesByVisitId.getOrDefault(v.getId(), List.of()),
                        templateNames, pendingVisitIds))
                .collect(Collectors.toList());
    }

    /**
     * Returns the full detail of a visit, including its checklist.
     * Returns 404 if the visit does not belong to the authenticated subscriber.
     *
     * @param userId    authenticated user id
     * @param visitId   the visit id
     * @return full visit detail
     * @throws VisitNotFoundException if not found or not owned by this subscriber
     */
    @Transactional(readOnly = true)
    public AppVisitDetail getVisit(Long userId, Long visitId) {
        Long subscriberId = resolveSubscriberId(userId);
        Visit visit = visitRepository.findByIdAndSubscriberId(visitId, subscriberId)
                .orElseThrow(() -> {
                    log.debug("visit_not_found_or_not_owned visitId={} subscriberId={}", visitId, subscriberId);
                    return new VisitNotFoundException(visitId);
                });
        List<VisitServiceItem> services = loadServiceItems(visit.getId());
        List<AppVisitPhoto> photos = loadPhotos(visit.getId());
        Map<Long, String> templateNames = loadTemplateNames(List.of(visit));
        boolean hasPendingRescheduleRequest = rescheduleService.hasPendingRequest(visit.getId());
        return toDetail(visit, services, photos, templateNames, hasPendingRescheduleRequest);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AppVisitListItem toListItem(Visit v, List<VisitServiceItem> services,
                                        Map<Long, String> templateNames, Set<Long> pendingVisitIds) {
        return new AppVisitListItem(
                v.getId(),
                v.resolveDisplayName(templateNames),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getStatus().name(),
                v.getType().name(),
                null,          // technicianFirstName — technician slice not yet built
                services,
                pendingVisitIds.contains(v.getId())
        );
    }

    private AppVisitDetail toDetail(Visit v, List<VisitServiceItem> services,
                                    List<AppVisitPhoto> photos,
                                    Map<Long, String> templateNames,
                                    boolean hasPendingRescheduleRequest) {
        return new AppVisitDetail(
                v.getId(),
                v.resolveDisplayName(templateNames),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getActualDurationMinutes(),
                v.getMaterialsCostCents(),
                v.getStatus().name(),
                v.getType().name(),
                v.getCompletionNotes(),
                v.getCompletedAt(),
                null,          // technicianFirstName — technician slice not yet built
                services,
                photos,
                hasPendingRescheduleRequest
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Batch-loads template names for the given list of visits, for
     * {@link Visit#resolveDisplayName}. Returns a map of templateId → template name.
     * Visits with no template id are skipped. Uses a single {@code findAllById} call to
     * avoid N+1 queries.
     */
    private Map<Long, String> loadTemplateNames(List<Visit> visits) {
        List<Long> templateIds = visits.stream()
                .map(Visit::getVisitTemplateId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        visitTemplateRepository.findAllById(templateIds)
                .forEach(t -> result.put(t.getId(), t.getName()));
        return result;
    }

    /**
     * Resolves the subscriber id from the authenticated user id.
     * Returns 404 if no subscriber row exists for this user.
     */
    private Long resolveSubscriberId(Long userId) {
        return subscriberQueryService.findByUserId(userId)
                .map(s -> s.getId())
                .orElseThrow(() -> new VisitNotFoundException(-1L));
    }

    /**
     * Loads the checklist items for a single visit, resolving service names via CatalogService.
     */
    private List<VisitServiceItem> loadServiceItems(Long visitId) {
        List<VisitService> rows = visitServiceRepository.findByVisitIdOrderByIdAsc(visitId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> serviceIds = rows.stream().map(VisitService::getServiceId).distinct().collect(Collectors.toList());
        Map<Long, String> nameById = catalogService.getServiceNamesByIds(serviceIds);

        return rows.stream()
                .map(vs -> VisitServiceItem.from(vs, nameById))
                .collect(Collectors.toList());
    }

    /**
     * Batch equivalent of {@link #loadServiceItems} for a page of visits: one query for all
     * checklist rows plus one query for all service names, instead of two queries per visit
     * (avoids the N+1 that {@link #listVisits} previously had).
     */
    private Map<Long, List<VisitServiceItem>> loadServiceItemsByVisitIds(List<Long> visitIds) {
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        List<VisitService> rows = visitServiceRepository.findByVisitIdInOrderByIdAsc(visitIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> serviceIds = rows.stream().map(VisitService::getServiceId).distinct().collect(Collectors.toList());
        Map<Long, String> nameById = catalogService.getServiceNamesByIds(serviceIds);

        return rows.stream()
                .collect(Collectors.groupingBy(VisitService::getVisitId,
                        Collectors.mapping(vs -> VisitServiceItem.from(vs, nameById), Collectors.toList())));
    }

    /**
     * Loads the photos for a visit (scoped strictly to this visit id — the query is a
     * {@code visit_id} equality lookup, so it cannot return another visit's rows), signing
     * each download URL via {@link StorageService#presignDownload}.
     *
     * <p>Graceful degradation: if R2 is not configured, {@code presignDownload} throws
     * {@link StorageUnavailableException} — logged at DEBUG (expected in dev/test) and the
     * photo is skipped. Any OTHER exception from signing (SDK error, malformed/legacy key,
     * etc.) is also caught per-photo, logged at WARN (id only — never the storage key), and
     * skipped, so one bad photo degrades to a missing thumbnail rather than a 500 for the
     * whole visit-detail response. The honest result when everything fails is an empty
     * {@code photos[]}, still 200.
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
                log.debug("visit_photo_presign_unavailable visitId={} photoId={}", visitId, row.getId());
                // R2 not configured — skip this (and, in practice, every) photo rather than
                // return a dead link. The honest result is an empty photos[].
            } catch (Exception e) {
                log.warn("visit_photo_presign_failed visitId={} photoId={}", visitId, row.getId());
                // Any other signing failure (SDK error, malformed key, etc.) — skip just this
                // photo rather than 500 the whole visit-detail response.
            }
        }
        return photos;
    }

    private VisitStatus parseStatus(String status) {
        try {
            return VisitStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidVisitRequestException("Invalid status value: " + status);
        }
    }
}
