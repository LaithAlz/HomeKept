package com.homekept.visit;

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
import com.homekept.visit.dto.TechPhotoUploadUrlRequest;
import com.homekept.visit.dto.TechPhotoUploadUrlResponse;
import com.homekept.visit.dto.TechStartVisitResponse;
import com.homekept.visit.dto.TechVisitListItem;
import com.homekept.visit.dto.TodoResponse;
import com.homekept.visit.dto.VisitServiceItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Technician app endpoints (role: TECHNICIAN).
 *
 * <h2>Auth model</h2>
 * <p>All endpoints require {@code hasRole('TECHNICIAN')} (method security). The JWT
 * principal is a {@code Long} user id (set by {@link com.homekept.identity.JwtAuthFilter}).
 * The service layer verifies the visit is assigned to THIS technician before doing
 * anything — mismatches return 404 per the ownership-failure rule.
 *
 * <h2>Access notes</h2>
 * <p>Decrypted access notes appear <strong>only</strong> in the day-sheet response
 * ({@code GET /api/tech/visits/today}). They are never logged here or in the service.
 *
 * <h2>Security note</h2>
 * <p>{@code /api/tech/**} falls under {@code anyRequest().authenticated()} in
 * {@link com.homekept.config.SecurityConfig} — no change to SecurityConfig is needed.
 * The {@code @PreAuthorize} annotation is the role gate (second layer).
 */
@RestController
@RequestMapping("/api/tech")
@PreAuthorize("hasRole('TECHNICIAN')")
public class TechVisitController {

    private final TechVisitService techVisitService;

    public TechVisitController(TechVisitService techVisitService) {
        this.techVisitService = techVisitService;
    }

    /**
     * GET /api/tech/visits/today
     *
     * <p>Returns the authenticated technician's visits scheduled for today in
     * America/Toronto, each with property address, decrypted access notes, and the
     * full checklist (template + picks + todos + flagged items).
     *
     * @param auth JWT principal — Long user id
     * @return list of today's visits (may be empty)
     */
    @GetMapping("/visits/today")
    public ResponseEntity<List<TechVisitListItem>> getTodaysVisits(Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.getTodaysVisits(techUserId));
    }

    /**
     * POST /api/tech/visits/{id}/start
     *
     * <p>Transitions the visit from SCHEDULED to IN_PROGRESS.
     * Returns 404 if the visit does not exist or is not assigned to this technician.
     * Returns 409 if the state transition is illegal.
     *
     * @param id   the visit id
     * @param auth JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/start")
    public ResponseEntity<TechStartVisitResponse> startVisit(
            @PathVariable Long id,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.startVisit(id, techUserId));
    }

    /**
     * PATCH /api/tech/visits/{id}/services/{visitServiceId}
     *
     * <p>Ticks or unticks a checklist item, optionally adding technician notes.
     * Returns 404 if the visit or service item is not found / not owned.
     *
     * @param id             the visit id
     * @param visitServiceId the checklist item id
     * @param request        {@code {completed, technicianNotes}}
     * @param auth           JWT principal — Long user id
     */
    @PatchMapping("/visits/{id}/services/{visitServiceId}")
    public ResponseEntity<VisitServiceItem> patchService(
            @PathVariable Long id,
            @PathVariable Long visitServiceId,
            @RequestBody TechPatchServiceRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.patchService(id, visitServiceId, request, techUserId));
    }

    /**
     * POST /api/tech/visits/{id}/photos/upload-url
     *
     * <p>Generates a 15-minute signed R2 PUT URL. The storage key is server-generated
     * ({@code visits/{id}/{uuid}}). Returns 400 if the content type is not an image.
     * Returns 503 if R2 is not configured.
     *
     * @param id      the visit id
     * @param request {@code {contentType}}
     * @param auth    JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/photos/upload-url")
    public ResponseEntity<TechPhotoUploadUrlResponse> getPhotoUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody TechPhotoUploadUrlRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.presignUpload(id, request.contentType(), techUserId));
    }

    /**
     * POST /api/tech/visits/{id}/photos
     *
     * <p>Confirms a photo upload and persists the {@code visit_photo} row.
     * Returns 400 if the storage key prefix doesn't match {@code visits/{id}/}.
     *
     * @param id      the visit id
     * @param request {@code {storageKey, caption}}
     * @param auth    JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/photos")
    public ResponseEntity<TechPhotoResponse> confirmPhoto(
            @PathVariable Long id,
            @Valid @RequestBody TechConfirmPhotoRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        TechPhotoResponse response = techVisitService.confirmPhoto(id, request, techUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/tech/visits/{id}/flags
     *
     * <p>Creates an OPEN flag on the visit's subscriber (observe → photograph → flag loop).
     *
     * @param id      the visit id
     * @param request {@code {body, severity, photoStorageKey?}}
     * @param auth    JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/flags")
    public ResponseEntity<FlagResponse> createFlag(
            @PathVariable Long id,
            @Valid @RequestBody TechCreateFlagRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        FlagResponse response = techVisitService.createFlag(id, request, techUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PATCH /api/tech/todos/{id}
     *
     * <p>Updates a todo item's status to DONE or DECLINED in the field.
     * A note is required when declining. Returns 404 if the todo is not found or if this
     * technician has no active visit for the todo's subscriber.
     *
     * @param id      the todo item id
     * @param request {@code {status: "DONE"|"DECLINED", note?}}
     * @param auth    JWT principal — Long user id
     */
    @PatchMapping("/todos/{id}")
    public ResponseEntity<TodoResponse> patchTodo(
            @PathVariable Long id,
            @Valid @RequestBody TechPatchTodoRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.patchTodo(id, request, techUserId));
    }

    /**
     * POST /api/tech/visits/{id}/complete
     *
     * <p>Transitions IN_PROGRESS → COMPLETED. Captures actual duration and materials cost.
     * Fires the visit-report notification stub. Returns 409 for illegal state transitions.
     *
     * @param id      the visit id
     * @param request {@code {completionNotes, actualDurationMinutes, materialsCostCents, materialsNotes}}
     * @param auth    JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/complete")
    public ResponseEntity<TechCompleteVisitResponse> completeVisit(
            @PathVariable Long id,
            @Valid @RequestBody TechCompleteVisitRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.completeVisit(id, request, techUserId));
    }

    /**
     * POST /api/tech/visits/{id}/incomplete
     *
     * <p>Transitions IN_PROGRESS → INCOMPLETE and auto-creates a follow-up SCHEDULED visit
     * 7 days from now (same subscriber, property, template, services). Returns 409 for
     * illegal state transitions.
     *
     * @param id      the visit id
     * @param request {@code {reason}}
     * @param auth    JWT principal — Long user id
     */
    @PostMapping("/visits/{id}/incomplete")
    public ResponseEntity<TechIncompleteVisitResponse> incompleteVisit(
            @PathVariable Long id,
            @Valid @RequestBody TechIncompleteVisitRequest request,
            Authentication auth) {
        Long techUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(techVisitService.incompleteVisit(id, request, techUserId));
    }
}
