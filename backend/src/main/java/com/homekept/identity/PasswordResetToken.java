package com.homekept.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Password reset token record for the forgot/reset password flow (api-contract.md §Auth),
 * also reused by the technician staff-invite flow. Table created by the V1 migration;
 * {@code purpose} added by V13.
 *
 * <h2>Token design</h2>
 * <p>Mirrors {@code ActivationToken}: the raw token in the reset link is an HMAC-SHA256
 * signed opaque value containing {@code userId | nonce | expiry_epoch_seconds}, signed
 * with the JWT signing key. Only the SHA-256 hash of the raw token is stored here.
 * Single-use ({@code consumed_at}); expiry enforced in {@code expires_at} (30 minutes for
 * {@link TokenPurpose#PASSWORD_RESET}, 7 days for {@link TokenPurpose#STAFF_INVITE} — see
 * {@code PasswordResetTokenService}).
 *
 * <p>{@code purpose} is the hard boundary between the two flows that share this table —
 * see {@link TokenPurpose}'s Javadoc.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex digest of the reset token. */
    @Column(name = "token_hash", nullable = false, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenPurpose purpose;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt, TokenPurpose purpose) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.purpose = purpose;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Instant getConsumedAt() { return consumedAt; }
    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public TokenPurpose getPurpose() { return purpose; }
    public Instant getCreatedAt() { return createdAt; }
}
