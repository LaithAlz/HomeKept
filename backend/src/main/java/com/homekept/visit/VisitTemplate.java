package com.homekept.visit;

import com.homekept.catalog.PlanCode;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A seasonal visit template: the named visit for a given month and minimum tier.
 *
 * <p>The calendar is cumulative. A subscriber on COMPLETE gets all ESSENTIAL templates
 * plus all COMPLETE templates. A subscriber on PREMIER gets all three tiers.
 *
 * <p>Seeded via V6__visit.sql from docs/pricing-and-visits.md.
 * Templates are read-only at runtime — changes go through a new Flyway migration.
 *
 * <p>See arch doc §2.3 (catalog), §2.6 (visit scheduling).
 */
@Entity
@Table(name = "visit_template")
public class VisitTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Calendar month (1 = January … 12 = December). */
    @Column(nullable = false)
    private int month;

    /** Human-readable visit name (e.g. "Fall winterization"). */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Minimum plan tier at which this template applies (inclusive).
     * ESSENTIAL → all three tiers; COMPLETE → Complete and Premier; PREMIER → Premier only.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "min_tier", nullable = false, length = 20)
    private PlanCode minTier;

    /** Seasonal focus description (trades-safe, human-readable). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "visitTemplate", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<VisitTemplateService> services = new ArrayList<>();

    protected VisitTemplate() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public int getMonth() { return month; }
    public String getName() { return name; }
    public PlanCode getMinTier() { return minTier; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public List<VisitTemplateService> getServices() { return services; }
}
