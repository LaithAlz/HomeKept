package com.homekept.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Password reset token record for the forgot/reset password flow (api-contract.md §Auth).
 * Table created by the V1 migration.
 *
 * <h2>Token design</h2>
 * <p>Mirrors {@code ActivationToken}: the raw token in the reset link is an HMAC-SHA256
 * signed opaque value containing {@code userId | nonce | expiry_epoch_seconds}, signed
 * with the JWT signing key. Only the SHA-256 hash of the raw token is stored here.
 * Single-use ({@code consumed_at}); 30-minute expiry enforced in {@code expires_at}.
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public Instant getCreatedAt() { return createdAt; }
}
