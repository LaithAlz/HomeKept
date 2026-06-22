package com.homekept.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A property (home) being maintained under a HomeKept subscription.
 *
 * <h2>Access-note encryption</h2>
 * <p>{@code accessNotes} is stored as {@code BYTEA} and contains AES-256-GCM encrypted
 * data: {@code IV (12 bytes) || ciphertext || GCM auth tag (16 bytes)}. Encryption and
 * decryption are handled exclusively by {@link AccessNotesCipher}. This field is
 * {@code byte[]} at the JPA layer; the entity never holds the plaintext in memory
 * longer than needed.
 *
 * <p><strong>Never log or expose the decrypted value</strong> — not in response DTOs,
 * not in log statements. Only the assigned technician's day-sheet (a later slice)
 * decrypts access notes on visit day.
 *
 * <h2>Circular FK</h2>
 * <p>{@code subscriberId} references {@code subscriber.id} via a DEFERRABLE INITIALLY
 * DEFERRED FK (V4 migration). Within a single transaction the property row can be
 * inserted with {@code subscriber_id=NULL} and updated after the subscriber is persisted,
 * or both rows can be inserted and the FK checked at commit. See migration for details.
 */
@Entity
@Table(name = "property")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK to {@code subscriber.id}. Nullable — set after the subscriber row is created
     * within the same transaction. The FK is DEFERRABLE INITIALLY DEFERRED in the DB.
     */
    @Column(name = "subscriber_id")
    private Long subscriberId;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(length = 50)
    private String unit;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Forward sortation area — first 3 characters of the postal code. Region key for assignment. */
    @Column(nullable = false, length = 3)
    private String fsa;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(name = "square_footage_range", length = 20)
    private String squareFootageRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    /**
     * AES-256-GCM encrypted access notes: IV (12 bytes) || ciphertext || GCM tag (16 bytes).
     * Never expose decrypted. Decrypted only by {@link AccessNotesCipher} on the technician
     * day-sheet slice.
     */
    @Column(name = "access_notes", columnDefinition = "BYTEA")
    private byte[] accessNotes;

    // ── SKU sheet fields (technician prep) ────────────────────────────────────

    @Column(name = "hvac_filter_sizes", columnDefinition = "TEXT")
    private String hvacFilterSizes;

    @Column(name = "smoke_co_detector_models", columnDefinition = "TEXT")
    private String smokeCODetectorModels;

    @Column(name = "humidifier_model", columnDefinition = "TEXT")
    private String humidifierModel;

    @Column(name = "water_heater_age_years")
    private Integer waterHeaterAgeYears;

    @Column(name = "water_heater_flush_eligible")
    private Boolean waterHeaterFlushEligible;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Property() {}

    public Property(String streetAddress, String unit, String city, String postalCode,
                    String fsa, Integer yearBuilt, String squareFootageRange,
                    PropertyType propertyType) {
        this.streetAddress = streetAddress;
        this.unit = unit;
        this.city = city;
        this.postalCode = postalCode;
        this.fsa = fsa;
        this.yearBuilt = yearBuilt;
        this.squareFootageRange = squareFootageRange;
        this.propertyType = propertyType;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getSubscriberId() { return subscriberId; }
    public void setSubscriberId(Long subscriberId) { this.subscriberId = subscriberId; }
    public String getStreetAddress() { return streetAddress; }
    public String getUnit() { return unit; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getFsa() { return fsa; }
    public Integer getYearBuilt() { return yearBuilt; }
    public String getSquareFootageRange() { return squareFootageRange; }
    public PropertyType getPropertyType() { return propertyType; }

    /** Returns the raw encrypted bytes. Never decrypt here — use {@link AccessNotesCipher}. */
    public byte[] getAccessNotes() { return accessNotes; }
    public void setAccessNotes(byte[] accessNotes) { this.accessNotes = accessNotes; }

    public boolean hasAccessNotes() { return accessNotes != null && accessNotes.length > 0; }

    public String getHvacFilterSizes() { return hvacFilterSizes; }
    public void setHvacFilterSizes(String hvacFilterSizes) { this.hvacFilterSizes = hvacFilterSizes; }
    public String getSmokeCODetectorModels() { return smokeCODetectorModels; }
    public void setSmokeCODetectorModels(String smokeCODetectorModels) { this.smokeCODetectorModels = smokeCODetectorModels; }
    public String getHumidifierModel() { return humidifierModel; }
    public void setHumidifierModel(String humidifierModel) { this.humidifierModel = humidifierModel; }
    public Integer getWaterHeaterAgeYears() { return waterHeaterAgeYears; }
    public void setWaterHeaterAgeYears(Integer waterHeaterAgeYears) { this.waterHeaterAgeYears = waterHeaterAgeYears; }
    public Boolean getWaterHeaterFlushEligible() { return waterHeaterFlushEligible; }
    public void setWaterHeaterFlushEligible(Boolean waterHeaterFlushEligible) { this.waterHeaterFlushEligible = waterHeaterFlushEligible; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
