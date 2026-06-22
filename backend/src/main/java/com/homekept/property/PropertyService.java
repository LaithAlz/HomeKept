package com.homekept.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for the property domain.
 *
 * <p>Cross-domain rule: this service may be called by other domains (e.g., the
 * subscription activation flow) but never exposes its repository or entity to them.
 *
 * <p>Access notes: only the technician day-sheet slice decrypts access notes.
 * This service only creates and stores encrypted bytes.
 */
@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final AccessNotesCipher cipher;

    public PropertyService(PropertyRepository propertyRepository, AccessNotesCipher cipher) {
        this.propertyRepository = propertyRepository;
        this.cipher = cipher;
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
