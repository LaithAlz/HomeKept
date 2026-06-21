package com.homekept.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A subscription plan tier (ESSENTIAL / COMPLETE / PREMIER).
 *
 * <p>Pricing is in integer cents — never floats. Stripe price IDs are set by the founder
 * once Stripe products are created (issue #21); they are nullable until then.
 * Founding rate fields are only populated for COMPLETE per docs/pricing-and-visits.md.
 *
 * <p>See arch doc §2.3 and docs/pricing-and-visits.md for canonical numbers.
 */
@Entity
@Table(name = "plan_tier")
public class PlanTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PlanCode code;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /** Monthly subscription price in cents. Integer — never float. */
    @Column(name = "monthly_price_cents", nullable = false)
    private int monthlyPriceCents;

    /** Annual subscription price in cents (= 10 months; 2 months free). Integer. */
    @Column(name = "annual_price_cents", nullable = false)
    private int annualPriceCents;

    @Column(name = "visits_per_year", nullable = false)
    private int visitsPerYear;

    /** Included picks per subscription anniversary year. */
    @Column(name = "included_picks_per_year", nullable = false)
    private int includedPicksPerYear;

    /** Maximum number of the included picks that may be Premium-tier picks. */
    @Column(name = "max_premium_picks_per_year", nullable = false)
    private int maxPremiumPicksPerYear;

    /** Set by founder once Stripe products are created. NULL until then. */
    @Column(name = "stripe_price_id_monthly", length = 255)
    private String stripePriceIdMonthly;

    @Column(name = "stripe_price_id_annual", length = 255)
    private String stripePriceIdAnnual;

    @Column(name = "stripe_price_id_founding", length = 255)
    private String stripePriceIdFounding;

    /**
     * Founding-member monthly price in cents. Only set for COMPLETE ($129/mo locked 12 months).
     * NULL for ESSENTIAL and PREMIER — founding rate is not available on those tiers.
     */
    @Column(name = "founding_monthly_price_cents")
    private Integer foundingMonthlyPriceCents;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Nullable soft-archive timestamp. Set when this tier is retired (not hard-deleted). */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "planTier", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PlanTierService> planTierServices = new ArrayList<>();

    protected PlanTier() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public int getMonthlyPriceCents() { return monthlyPriceCents; }
    public int getAnnualPriceCents() { return annualPriceCents; }
    public int getVisitsPerYear() { return visitsPerYear; }
    public int getIncludedPicksPerYear() { return includedPicksPerYear; }
    public int getMaxPremiumPicksPerYear() { return maxPremiumPicksPerYear; }
    public String getStripePriceIdMonthly() { return stripePriceIdMonthly; }
    public String getStripePriceIdAnnual() { return stripePriceIdAnnual; }
    public String getStripePriceIdFounding() { return stripePriceIdFounding; }
    public Integer getFoundingMonthlyPriceCents() { return foundingMonthlyPriceCents; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public List<PlanTierService> getPlanTierServices() { return planTierServices; }

    /**
     * True if this tier has a seeded founding-member price in the DB.
     * This is the per-tier half of the founding-rate gate; the other half is the global
     * slot count checked via {@link FoundingRateAvailability}. See
     * {@link com.homekept.catalog.dto.PlanTierResponse} for how both are ANDed together.
     */
    public boolean hasFoundingPrice() {
        return foundingMonthlyPriceCents != null;
    }
}
