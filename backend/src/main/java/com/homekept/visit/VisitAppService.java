package com.homekept.visit;

import com.homekept.catalog.CatalogService;
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

    public VisitAppService(VisitRepository visitRepository,
                           VisitServiceRepository visitServiceRepository,
                           VisitPhotoRepository visitPhotoRepository,
                           SubscriberQueryService subscriberQueryService,
                           CatalogService catalogService,
                           VisitTemplateRepository visitTemplateRepository,
                           StorageService storageService) {
        this.visitRepository = visitRepository;
        this.visitServiceRepository = visitServiceRepository;
        this.visitPhotoRepository = visitPhotoRepository;
        this.subscriberQueryService = subscriberQueryService;
        this.catalogService = catalogService;
        this.visitTemplateRepository = visitTemplateRepository;
        this.storageService = storageService;
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
        int pageSize = resolveLimit(limit);
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

        Map<Long, String> templateNames = loadTemplateNames(visits);
        return visits.stream()
                .map(v -> toListItem(v, loadServiceItems(v.getId()), templateNames))
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
        return toDetail(visit, services, photos, templateNames);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AppVisitListItem toListItem(Visit v, List<VisitServiceItem> services,
                                        Map<Long, String> templateNames) {
        return new AppVisitListItem(
                v.getId(),
                resolveVisitName(v, templateNames),
                v.getScheduledFor(),
                v.getDurationMinutes(),
                v.getStatus().name(),
                v.getType().name(),
                null,          // technicianFirstName — technician slice not yet built
                services
        );
    }

    private AppVisitDetail toDetail(Visit v, List<VisitServiceItem> services,
                                    List<AppVisitPhoto> photos,
                                    Map<Long, String> templateNames) {
        return new AppVisitDetail(
                v.getId(),
                resolveVisitName(v, templateNames),
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
                photos
        );
    }

    /**
     * Resolves the human-readable visit name.
     *
     * <ul>
     *   <li>Template-driven visits (ROUTINE with {@code visitTemplateId} set): use the
     *       template's {@code name} field (e.g. "Fall winterization").</li>
     *   <li>EXTRA visits (à-la-carte add-ons): "Extra visit".</li>
     *   <li>WALKTHROUGH visits: "Walk-through".</li>
     *   <li>WARRANTY visits: "Warranty visit".</li>
     *   <li>Any other ROUTINE with no template: "Routine visit" (admin-created).</li>
     * </ul>
     */
    private String resolveVisitName(Visit v, Map<Long, String> templateNames) {
        if (v.getVisitTemplateId() != null) {
            String name = templateNames.get(v.getVisitTemplateId());
            if (name != null) {
                return name;
            }
        }
        return switch (v.getType()) {
            case EXTRA -> "Extra visit";
            case WALKTHROUGH -> "Walk-through";
            case WARRANTY -> "Warranty visit";
            case ROUTINE -> "Routine visit";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Batch-loads template names for the given list of visits.
     * Returns a map of templateId → template name. Visits with no template id are skipped.
     * Uses a single {@code findAllById} call to avoid N+1 queries.
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
     * Loads the checklist items for a visit, resolving service names via CatalogService.
     */
    private List<VisitServiceItem> loadServiceItems(Long visitId) {
        List<VisitService> rows = visitServiceRepository.findByVisitIdOrderByIdAsc(visitId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> serviceIds = rows.stream().map(VisitService::getServiceId).distinct().collect(Collectors.toList());
        Map<Long, String> nameById = catalogService.getServiceNamesByIds(serviceIds);

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

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 100);
    }
}
