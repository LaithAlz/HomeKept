package com.homekept.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link PlanTier}. Not tested directly — generated code. */
interface PlanTierRepository extends JpaRepository<PlanTier, Long> {

    /**
     * Returns all tiers with their service associations eagerly loaded.
     * Uses JOIN FETCH to avoid N+1 on the plan_tier_service join table.
     * Ordered so COMPLETE → PREMIER always renders in price order.
     */
    @Query("""
            SELECT DISTINCT pt FROM PlanTier pt
            LEFT JOIN FETCH pt.planTierServices pts
            LEFT JOIN FETCH pts.service s
            ORDER BY pt.monthlyPriceCents ASC
            """)
    List<PlanTier> findAllWithServices();

    /**
     * Find a plan tier by its Stripe price id (monthly or annual column).
     *
     * <p>Used by the webhook handler to map the price id from a Stripe subscription
     * object back to the HomeKept {@link PlanTier}. The query checks both price-id
     * columns so that the same lookup works regardless of billing cycle.
     */
    @Query("""
            SELECT pt FROM PlanTier pt
            WHERE pt.stripePriceIdMonthly = :priceId
               OR pt.stripePriceIdAnnual  = :priceId
            """)
    Optional<PlanTier> findByAnyStripePriceId(@Param("priceId") String priceId);

    /** Find a plan tier by its {@link PlanCode}. */
    Optional<PlanTier> findByCode(PlanCode code);
}
