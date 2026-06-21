package com.homekept.booking;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A walk-through booking submitted by a prospective subscriber.
 *
 * <p>Status transitions are enforced by {@link WalkthroughBookingStateMachine}.
 * No direct status writes — always go through the state machine.
 *
 * <p>Day preferences are stored in a normalized child table
 * ({@code walkthrough_booking_day_preference}) mapped as an {@link ElementCollection}.
 * This avoids JSONB while keeping the mapping simple.
 *
 * <p>{@code convertedToSubscriberId} and {@code activationTokenId} are bare BIGINT columns
 * with no FK constraint at this stage — the subscriber and activation_token tables do not
 * yet exist. A later migration adds the constraints.
 */
@Entity
@Table(name = "walkthrough_booking")
public class WalkthroughBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(name = "square_footage_range", length = 20)
    private String squareFootageRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    @Column(name = "preferred_week", nullable = false)
    private LocalDate preferredWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_of_day", nullable = false, length = 20)
    private TimeOfDay timeOfDay;

    /**
     * Day-of-week preferences stored in the child table
     * {@code walkthrough_booking_day_preference}. Loaded eagerly — small set
     * (up to 7 values), always needed when reading a booking.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "walkthrough_booking_day_preference",
            joinColumns = @JoinColumn(name = "booking_id")
    )
    @Column(name = "day_of_week", length = 3)
    @Enumerated(EnumType.STRING)
    private Set<BookingDayOfWeek> dayPreferences = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_source", nullable = false, length = 30)
    private LeadSource leadSource;

    @Column(name = "posthog_distinct_id", length = 255)
    private String posthogDistinctId;

    @Column(name = "contact_consent_at", nullable = false)
    private Instant contactConsentAt;

    /** Bare FK — no constraint until the subscriber table exists (later migration). */
    @Column(name = "converted_to_subscriber_id")
    private Long convertedToSubscriberId;

    /** Bare FK — no constraint until the activation_token table exists (later migration). */
    @Column(name = "activation_token_id")
    private Long activationTokenId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalkthroughBooking() {}

    public WalkthroughBooking(
            String fullName, String email, String phone,
            String streetAddress, String city, String postalCode,
            Integer yearBuilt, String squareFootageRange,
            PropertyType propertyType, LocalDate preferredWeek,
            TimeOfDay timeOfDay, Set<BookingDayOfWeek> dayPreferences,
            String notes, LeadSource leadSource, String posthogDistinctId,
            Instant contactConsentAt) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.streetAddress = streetAddress;
        this.city = city;
        this.postalCode = postalCode;
        this.yearBuilt = yearBuilt;
        this.squareFootageRange = squareFootageRange;
        this.propertyType = propertyType;
        this.preferredWeek = preferredWeek;
        this.timeOfDay = timeOfDay;
        this.dayPreferences = dayPreferences != null ? new HashSet<>(dayPreferences) : new HashSet<>();
        this.notes = notes;
        this.leadSource = leadSource;
        this.posthogDistinctId = posthogDistinctId;
        this.contactConsentAt = contactConsentAt;
        this.status = BookingStatus.PENDING;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStreetAddress() { return streetAddress; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public Integer getYearBuilt() { return yearBuilt; }
    public String getSquareFootageRange() { return squareFootageRange; }
    public PropertyType getPropertyType() { return propertyType; }
    public LocalDate getPreferredWeek() { return preferredWeek; }
    public TimeOfDay getTimeOfDay() { return timeOfDay; }
    public Set<BookingDayOfWeek> getDayPreferences() { return dayPreferences; }
    public String getNotes() { return notes; }
    public BookingStatus getStatus() { return status; }

    /**
     * Sets the booking status. Callers MUST invoke
     * {@link WalkthroughBookingStateMachine#canTransition(BookingStatus, BookingStatus)}
     * and verify it returns {@code true} before calling this setter.
     * Direct status writes without the state machine check are forbidden.
     */
    public void setStatus(BookingStatus status) { this.status = status; }

    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
    public LeadSource getLeadSource() { return leadSource; }
    public String getPosthogDistinctId() { return posthogDistinctId; }
    public Instant getContactConsentAt() { return contactConsentAt; }
    public Long getConvertedToSubscriberId() { return convertedToSubscriberId; }
    public void setConvertedToSubscriberId(Long convertedToSubscriberId) {
        this.convertedToSubscriberId = convertedToSubscriberId;
    }
    public Long getActivationTokenId() { return activationTokenId; }
    public void setActivationTokenId(Long activationTokenId) {
        this.activationTokenId = activationTokenId;
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
