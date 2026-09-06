package com.homekept.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data repository for the {@code plan_tier_service} join entity.
 * Not tested directly — generated code. Package-private, matching {@link ServiceRepository}
 * and {@link PlanTierRepository}: only {@link CatalogService} and {@link CatalogAdminService}
 * reach into the catalog domain's repositories.
 */
interface PlanTierServiceRepository extends JpaRepository<PlanTierService, PlanTierService.PlanTierServiceId> {

    /**
     * Loads a composition row with {@code service} and {@code planTier} eagerly fetched.
     * Both associations are {@code FetchType.LAZY} on {@link PlanTierService}; a plain
     * {@code findById} returns proxies that throw {@code LazyInitializationException} once
     * the admin service's {@code @Transactional} method returns and the controller maps the
     * entity to a DTO outside that session (mirrors why {@link PlanTierRepository} has its
     * own {@code JOIN FETCH} query).
     */
    @Query("""
            SELECT pts FROM PlanTierService pts
            JOIN FETCH pts.service
            JOIN FETCH pts.planTier
            WHERE pts.id.planTierId = :planTierId AND pts.id.serviceId = :serviceId
            """)
    Optional<PlanTierService> findWithAssociationsById(@Param("planTierId") Long planTierId,
                                                        @Param("serviceId") Long serviceId);
}
