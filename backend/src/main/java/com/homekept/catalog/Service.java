package com.homekept.catalog;

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
 * A named maintenance service — either a standing item performed every visit, or a
 * pickable service available as an included pick or à la carte purchase.
 *
 * <p>Seed data is in V2__catalog.sql; changes go through a new Flyway migration.
 * See arch doc §2.3 and docs/pricing-and-visits.md for the full service list.
 */
@Entity
@Table(name = "service")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceCategory category;

    /**
     * Picks classification. BASIC = $49 à la carte, MEDIUM = $89, PREMIUM = $149.
     * Standing items use BASIC (they are included, not sold à la carte).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier_class", nullable = false, length = 10)
    private TierClass tierClass;

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes;

    /**
     * Set for all pickable services. NULL for standing items (not sold separately).
     * Always integer cents — never float.
     */
    @Column(name = "a_la_carte_price_cents")
    private Integer aLaCartePriceCents;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_free_with_every_visit", nullable = false)
    private boolean isFreeWithEveryVisit;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Service() {}

    /**
     * Creates a new admin-authored service ({@code POST /api/admin/services}).
     * {@code active} always starts {@code true} — see the field initializer above; new
     * services are never created pre-archived.
     *
     * @param aLaCartePriceCents integer cents, or {@code null} for a standing item that is
     *                           not sold à la carte
     */
    public Service(String name, ServiceCategory category, TierClass tierClass,
                   int defaultDurationMinutes, Integer aLaCartePriceCents,
                   String description, boolean isFreeWithEveryVisit) {
        this.name = name;
        this.category = category;
        this.tierClass = tierClass;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.aLaCartePriceCents = aLaCartePriceCents;
        this.description = description;
        this.isFreeWithEveryVisit = isFreeWithEveryVisit;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public ServiceCategory getCategory() { return category; }
    public TierClass getTierClass() { return tierClass; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public Integer getALaCartePriceCents() { return aLaCartePriceCents; }
    public String getDescription() { return description; }
    public boolean isFreeWithEveryVisit() { return isFreeWithEveryVisit; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ── Setters — admin CRUD only (CatalogAdminService) ──────────────────────
    // Service fields only. Never add a setter for anything on PlanTier's pricing
    // columns — pricing stays migration + Stripe only, per docs/pricing-and-visits.md.

    public void setName(String name) { this.name = name; }
    public void setCategory(ServiceCategory category) { this.category = category; }
    public void setTierClass(TierClass tierClass) { this.tierClass = tierClass; }
    public void setDefaultDurationMinutes(int defaultDurationMinutes) { this.defaultDurationMinutes = defaultDurationMinutes; }
    public void setALaCartePriceCents(Integer aLaCartePriceCents) { this.aLaCartePriceCents = aLaCartePriceCents; }
    public void setDescription(String description) { this.description = description; }
    public void setFreeWithEveryVisit(boolean freeWithEveryVisit) { this.isFreeWithEveryVisit = freeWithEveryVisit; }

    /** Archive/restore flag. Never a hard delete — see {@code CatalogAdminService}. */
    public void setActive(boolean active) { this.active = active; }
}
