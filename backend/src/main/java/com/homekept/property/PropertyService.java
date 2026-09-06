package com.homekept.property;

import com.homekept.identity.UserQueryService;
import com.homekept.identity.UserQueryService.UserSummary;
import com.homekept.property.dto.PropertyNoteItem;
import com.homekept.property.exception.PropertyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for the property domain.
 *
 * <p>Cross-domain rule: this service may be called by other domains (e.g., the
 * subscription activation flow, or the visit domain's {@code TechVisitService} for the
 * technician-facing property-notes endpoints) but never exposes its repository or entity to
 * them.
 *
 * <p>Access notes: only the technician day-sheet slice decrypts access notes.
 * This service only creates and stores encrypted bytes.
 *
 * <p>Property notes ({@link #listNotes}/{@link #addNote}) are the OTHER, plaintext note
 * surface (V18's {@code property_note} table) — never confuse the two. This service resolves
 * a note's author via {@link UserQueryService} (identity domain) — never by reaching into
 * the identity domain's repository or entity directly.
 */
@Service
public class PropertyService {

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

    /** Cap on notes returned per property — mirrors {@code VisitNoteService.NOTES_LIMIT}. */
    private static final int NOTES_LIMIT = 100;

    private final PropertyRepository propertyRepository;
    private final PropertyNoteRepository propertyNoteRepository;
    private final AccessNotesCipher cipher;
    private final UserQueryService userQueryService;

    public PropertyService(PropertyRepository propertyRepository,
                           PropertyNoteRepository propertyNoteRepository,
                           AccessNotesCipher cipher,
                           UserQueryService userQueryService) {
        this.propertyRepository = propertyRepository;
        this.propertyNoteRepository = propertyNoteRepository;
        this.cipher = cipher;
        this.userQueryService = userQueryService;
    }

    /**
     * Creates a property from the booking's address data during the activation flow.
     * The property is saved with {@code subscriber_id=NULL}; the caller must set the
     * {@code subscriber_id} after the subscriber row is created (same transaction, deferrable FK).
     *
     * @param request the property creation data from the booking
     * @return the persisted {@link Property}
     */
    @Transactional
    public Property createFromActivation(CreatePropertyRequest request) {
        String fsa = deriveFsa(request.postalCode());

        Property property = new Property(
                request.streetAddress(),
                null, // unit — not on the booking form; can be added in settings
                request.city(),
                request.postalCode(),
                fsa,
                request.yearBuilt(),
                request.squareFootageRange(),
                request.propertyType()
        );

        return propertyRepository.save(property);
    }

    /**
     * Links the subscriber to the property after the subscriber row is created.
     * Called within the same transaction as {@link #createFromActivation} so the
     * deferrable FK check fires at commit with both rows present.
     */
    @Transactional
    public void linkSubscriber(Long propertyId, Long subscriberId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalStateException("Property not found: " + propertyId));
        property.setSubscriberId(subscriberId);
        propertyRepository.save(property);
    }

    /**
     * Finds a property by its id. Returns {@code null} if not found.
     * Used by the admin detail endpoint; 404 is the caller's responsibility.
     */
    @Transactional(readOnly = true)
    public Property findById(Long id) {
        return propertyRepository.findById(id).orElse(null);
    }

    /**
     * Finds properties by id in a single batched query, for cross-domain list views that
     * need several properties' address data without an N+1 (e.g. the admin visit list,
     * which resolves each row's property address). Same entity-crossing acceptance as
     * {@link #findById} — callers must treat the returned entities as read-only, and — as
     * with any cross-domain use of {@link Property} — must never surface
     * {@link Property#getAccessNotes()} or decrypt it outside {@link #decryptAccessNotes}.
     *
     * @param ids the property ids to resolve; may be empty
     * @return map of property id → {@link Property}, for ids that exist (missing ids are
     *         simply absent from the map, never mapped to null). Empty input returns an
     *         empty map without querying the database.
     */
    @Transactional(readOnly = true)
    public Map<Long, Property> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return propertyRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
    }

    /**
     * Decrypts the access notes for the given property and returns the plaintext.
     *
     * <p><strong>This is the ONLY place access notes are decrypted in the entire
     * codebase.</strong> Called exclusively by the technician day-sheet
     * ({@code GET /api/tech/visits/today}) for the assigned technician on visit day.
     *
     * <p><strong>NEVER log the return value.</strong> NEVER return it on any other
     * endpoint. NEVER store it in a field longer than required.
     *
     * @param propertyId the property whose access notes to decrypt
     * @return the plaintext access notes, or {@code null} if the property has none or
     *         if encryption is disabled in dev-mode (blank key)
     * @throws IllegalStateException if the property does not exist, or if decryption
     *         fails (e.g. tampered ciphertext — AEADBadTagException)
     */
    @Transactional(readOnly = true)
    public String decryptAccessNotes(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalStateException("Property not found: " + propertyId));
        if (!property.hasAccessNotes()) {
            return null;
        }
        // Decrypt via the cipher — plaintext is returned to the caller and must not be logged.
        return cipher.decrypt(property.getAccessNotes());
    }

    /**
     * Updates the SKU sheet (technician prep) fields on a property: HVAC filter sizes,
     * smoke/CO detector models, humidifier model, water heater age, and water heater
     * flush eligibility. Captured by the walk-through and refined over subsequent visits
     * per docs/pricing-and-visits.md §Materials.
     *
     * <p>Partial-update semantics (matches other admin PATCH endpoints, e.g.
     * {@code VisitAdminService.patchVisit}): a {@code null} argument leaves the
     * corresponding field untouched, since the SKU sheet is captured incrementally
     * and an admin may only know one or two fields at a time. Pass a non-null value
     * to set or overwrite a field; there is currently no way to clear a field back
     * to {@code null} once set.
     *
     * @param propertyId               the property to update
     * @param hvacFilterSizes          free-text filter sizes/counts; {@code null} = leave unchanged
     * @param smokeCoDetectorModels    free-text detector models; {@code null} = leave unchanged
     * @param humidifierModel          free-text humidifier model; {@code null} = leave unchanged
     * @param waterHeaterAgeYears      water heater age in years (validated at the DTO boundary);
     *                                 {@code null} = leave unchanged
     * @param waterHeaterFlushEligible whether the tank is flush-eligible (skip-rule);
     *                                 {@code null} = leave unchanged
     * @return the updated {@link Property}
     * @throws PropertyNotFoundException if the property does not exist (404)
     */
    @Transactional
    public Property updateSkuSheet(Long propertyId,
                                    String hvacFilterSizes,
                                    String smokeCoDetectorModels,
                                    String humidifierModel,
                                    Integer waterHeaterAgeYears,
                                    Boolean waterHeaterFlushEligible) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        if (hvacFilterSizes != null) {
            property.setHvacFilterSizes(hvacFilterSizes);
        }
        if (smokeCoDetectorModels != null) {
            property.setSmokeCODetectorModels(smokeCoDetectorModels);
        }
        if (humidifierModel != null) {
            property.setHumidifierModel(humidifierModel);
        }
        if (waterHeaterAgeYears != null) {
            property.setWaterHeaterAgeYears(waterHeaterAgeYears);
        }
        if (waterHeaterFlushEligible != null) {
            property.setWaterHeaterFlushEligible(waterHeaterFlushEligible);
        }

        return propertyRepository.save(property);
    }

    /**
     * Returns a property's threaded notes, newest first, capped at {@value #NOTES_LIMIT}
     * rows, with each note's author resolved to a name via a single batched
     * {@link UserQueryService#findSummariesByIds} call for the whole page — never one query
     * per note. Backs {@code GET /api/admin/properties/{propertyId}/notes} and (after its own
     * ownership check) {@code GET /api/tech/properties/{propertyId}/notes}.
     *
     * @param propertyId the property id
     * @return the property's notes, newest first
     * @throws PropertyNotFoundException if the property does not exist (404)
     */
    @Transactional(readOnly = true)
    public List<PropertyNoteItem> listNotes(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException(propertyId);
        }

        List<PropertyNote> notes = propertyNoteRepository
                .findByPropertyIdOrderByCreatedAtDesc(propertyId, PageRequest.of(0, NOTES_LIMIT));
        if (notes.isEmpty()) {
            return List.of();
        }

        List<Long> authorIds = notes.stream().map(PropertyNote::getAuthorUserId).distinct().toList();
        Map<Long, UserSummary> authorsById = userQueryService.findSummariesByIds(authorIds);

        return notes.stream().map(n -> toNoteItem(n, authorsById)).collect(Collectors.toList());
    }

    /**
     * Adds a note to a property's operational log. Backs
     * {@code POST /api/admin/properties/{propertyId}/notes} and (after its own ownership
     * check) {@code POST /api/tech/properties/{propertyId}/notes}.
     *
     * <p>{@code authorUserId} MUST be the authenticated principal's user id — never a value
     * taken from request input.
     *
     * <h2>Why there is no delete (or edit) operation</h2>
     * <p>Same reasoning the V18 migration states explicitly: a technician writing "furnace
     * filter sits behind the stairs" is recording an observation. It matters who saw it and
     * when, notes accumulate across visits, and a later note can supersede an earlier one
     * without erasing that the earlier one was true at the time. Deleting a note would remove
     * that history silently, with no trace anything was ever said — worse than an outdated
     * note staying visible with its timestamp and author, which let a reader judge its
     * currency. A correction is a new note, not erasing the old one.
     *
     * @param propertyId   the property to add a note to
     * @param body         the note text (already validated non-blank, max length, at the DTO
     *                     boundary)
     * @param authorUserId the authenticated principal's user id
     * @return the created note, with the author's name resolved
     * @throws PropertyNotFoundException if the property does not exist (404)
     */
    @Transactional
    public PropertyNoteItem addNote(Long propertyId, String body, Long authorUserId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException(propertyId);
        }

        PropertyNote saved = propertyNoteRepository.save(new PropertyNote(propertyId, authorUserId, body));

        log.info("property_note_added propertyId={} noteId={}", propertyId, saved.getId());

        Map<Long, UserSummary> authorsById = userQueryService.findSummariesByIds(List.of(authorUserId));
        return toNoteItem(saved, authorsById);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PropertyNoteItem toNoteItem(PropertyNote note, Map<Long, UserSummary> authorsById) {
        UserSummary author = authorsById.get(note.getAuthorUserId());
        return new PropertyNoteItem(
                note.getId(),
                note.getBody(),
                note.getCreatedAt(),
                note.getAuthorUserId(),
                author != null ? author.firstName() : null,
                author != null ? author.lastName() : null);
    }

    /**
     * Derives the FSA (forward sortation area) from the postal code.
     * The FSA is the first 3 characters (e.g., "L5L" from "L5L 1A1").
     * Strips spaces and converts to uppercase.
     */
    static String deriveFsa(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) {
            throw new IllegalArgumentException("postalCode must not be blank");
        }
        String stripped = postalCode.replaceAll("\\s+", "").toUpperCase();
        if (stripped.length() < 3) {
            throw new IllegalArgumentException("postalCode too short to derive FSA: " + postalCode);
        }
        return stripped.substring(0, 3);
    }

    /**
     * Data needed to create a property from the activation flow.
     * All fields correspond directly to walk-through booking data.
     */
    public record CreatePropertyRequest(
            String streetAddress,
            String city,
            String postalCode,
            Integer yearBuilt,
            String squareFootageRange,
            PropertyType propertyType
    ) {}
}
