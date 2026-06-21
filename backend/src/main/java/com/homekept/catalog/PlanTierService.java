package com.homekept.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Join entity: which service is included in which plan tier and at what frequency.
 * Composite PK (plan_tier_id, service_id) — matches V2__catalog.sql.
 */
@Entity
@Table(name = "plan_tier_service")
public class PlanTierService {

    @EmbeddedId
    private PlanTierServiceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("planTierId")
    @JoinColumn(name = "plan_tier_id", nullable = false)
    private PlanTier planTier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceId")
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "frequency_per_year", nullable = false)
    private int frequencyPerYear;

    protected PlanTierService() {}

    public PlanTierServiceId getId() { return id; }
    public PlanTier getPlanTier() { return planTier; }
    public Service getService() { return service; }
    public int getFrequencyPerYear() { return frequencyPerYear; }

    // ── Composite key ──────────────────────────────────────────────────────────

    @Embeddable
    public static class PlanTierServiceId implements Serializable {

        @Column(name = "plan_tier_id")
        private Long planTierId;

        @Column(name = "service_id")
        private Long serviceId;

        protected PlanTierServiceId() {}

        public PlanTierServiceId(Long planTierId, Long serviceId) {
            this.planTierId = planTierId;
            this.serviceId = serviceId;
        }

        public Long getPlanTierId() { return planTierId; }
        public Long getServiceId() { return serviceId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlanTierServiceId that)) return false;
            return Objects.equals(planTierId, that.planTierId)
                    && Objects.equals(serviceId, that.serviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(planTierId, serviceId);
        }
    }
}
