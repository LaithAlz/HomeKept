package com.homekept.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Spring Data repository for {@link PlanTier}. Not tested directly — generated code. */
interface PlanTierRepository extends JpaRepository<PlanTier, Long> {

    /**
     * Returns all tiers with their service associations eagerly loaded.
     * Uses JOIN FETCH to avoid N+1 on the plan_tier_service join table.
     * Ordered so ESSENTIAL → COMPLETE → PREMIER always renders in price order.
     */
    @Query("""
            SELECT DISTINCT pt FROM PlanTier pt
            LEFT JOIN FETCH pt.planTierServices pts
            LEFT JOIN FETCH pts.service s
            ORDER BY pt.monthlyPriceCents ASC
            """)
    List<PlanTier> findAllWithServices();
}
