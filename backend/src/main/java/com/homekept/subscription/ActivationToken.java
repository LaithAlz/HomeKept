package com.homekept.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Stores the hashed activation magic-link token for a walk-through booking.
 *
 * <h2>Token design</h2>
 * <ul>
 *   <li>The raw token in the magic link is an HMAC-SHA256 signed opaque value
 *       containing: {@code booking_id | nonce | expiry_epoch_seconds}, signed with
 *       the JWT signing key. This is opaque to the client.</li>
 *   <li>Only the SHA-256 hash of the raw token is stored here — the raw value
 *       is never persisted.</li>
 *   <li>Single-use: {@code consumed_at} is set when {@code /activation/complete} runs.</li>
 *   <li>7-day expiry enforced in {@code expires_at}.</li>
 * </ul>
 *
 * <p>A booking may have at most one activation token at a time. The admin
 * {@code POST /api/admin/bookings/{id}/activation-invite} mints a new one
 * (superseding any previous — the hash column is unique so old hash lookup fails
 * gracefully: the token becomes "INVALID" from the client's perspective).
 */
@Entity
@Table(name = "activation_token")
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → walkthrough_booking.id */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /**
     * SHA-256 hex digest of the raw HMAC token that goes in the magic link.
     * UNIQUE index in the DB — each raw token is stored at most once.
     */
    @Column(name = "token_hash", nullable = false, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when {@code POST /activation/complete} successfully consumes this token. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivationToken() {}

    public ActivationToken(Long bookingId, String tokenHash, Instant expiresAt) {
        this.bookingId = bookingId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    /**
     * Marks the token as consumed. Callers must verify the token is not already
     * consumed or expired before calling this.
     */
    public void consume() {
        this.consumedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
}
