package com.homekept.catalog;

import com.homekept.catalog.exception.PlanTierNotFoundException;
import com.homekept.catalog.exception.PlanTierServiceConflictException;
import com.homekept.catalog.exception.PlanTierServiceNotFoundException;
import com.homekept.catalog.exception.ServiceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mutating admin operations for the catalog domain: service CRUD (create/update/archive/
 * restore — archive only, never a hard delete) and plan composition (which services a plan
 * tier includes, and at what frequency).
 *
 * <p><b>Scope boundary the founder set, and it is firm:</b> this service never writes
 * {@code plan_tier.monthly_price_cents}, {@code annual_price_cents}, {@code visits_per_year},
 * {@code included_picks_per_year}, {@code max_premium_picks_per_year}, or any
 * {@code stripe_price_id_*} column. Plan pricing is migration + Stripe only — see
 * docs/pricing-and-visits.md and {@link PlanTier}'s javadoc. If a future change seems to need
 * a price write here, stop and report it instead of adding one.
 *
 * <p>No cross-domain calls — same as {@link CatalogService}. Entities never cross the
 * controller boundary; the controller maps every return value to a DTO.
 */
@Service
public class CatalogAdminService {

    private final ServiceRepository serviceRepository;
    private final PlanTierRepository planTierRepository;
    private final PlanTierServiceRepository planTierServiceRepository;

    public CatalogAdminService(ServiceRepository serviceRepository,
                               PlanTierRepository planTierRepository,
                               PlanTierServiceRepository planTierServiceRepository) {
        this.serviceRepository = serviceRepository;
        this.planTierRepository = planTierRepository;
        this.planTierServiceRepository = planTierServiceRepository;
    }

    // ── Service CRUD ──────────────────────────────────────────────────────────

    /**
     * Returns every service — active and archived — sorted the same way the public picks
     * menu is (tier class, then name), so the admin can see what is archived.
     */
    @Transactional(readOnly = true)
    public List<com.homekept.catalog.Service> listAllServices() {
        return serviceRepository.findAllByOrderByTierClassAscNameAsc();
    }

    /**
     * Creates a new service. {@code category}/{@code tierClass} are already validated
     * against the CHECK-constraint value set at the DTO boundary ({@code @Pattern}), so
     * {@link Enum#valueOf} here cannot throw.
     *
     * @param isFreeWithEveryVisit {@code null} defaults to {@code false} — most services are
     *                             pickable, not standing items
     */
    @Transactional
    public com.homekept.catalog.Service createService(String name,
                                                       String category,
                                                       String tierClass,
                                                       int defaultDurationMinutes,
                                                       Integer aLaCartePriceCents,
                                                       String description,
                                                       Boolean isFreeWithEveryVisit) {
        com.homekept.catalog.Service service = new com.homekept.catalog.Service(
                name,
                ServiceCategory.valueOf(category),
                TierClass.valueOf(tierClass),
                defaultDurationMinutes,
                aLaCartePriceCents,
                description,
                Boolean.TRUE.equals(isFreeWithEveryVisit)
        );
        return serviceRepository.save(service);
    }

    /**
     * Partial update: a {@code null} argument leaves the corresponding column unchanged
     * (matches {@code PropertyService.updateSkuSheet}'s PATCH semantics). Never touches
     * {@code active} — see {@link #archiveService} / {@link #restoreService}.
     *
     * @throws ServiceNotFoundException if no service exists with this id (404)
     */
    @Transactional
    public com.homekept.catalog.Service updateService(Long id,
                                                       String name,
                                                       String category,
                                                       String tierClass,
                                                       Integer defaultDurationMinutes,
                                                       Integer aLaCartePriceCents,
                                                       String description,
                                                       Boolean isFreeWithEveryVisit) {
        com.homekept.catalog.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException(id));

        if (name != null) {
            service.setName(name);
        }
        if (category != null) {
            service.setCategory(ServiceCategory.valueOf(category));
        }
        if (tierClass != null) {
            service.setTierClass(TierClass.valueOf(tierClass));
        }
        if (defaultDurationMinutes != null) {
            service.setDefaultDurationMinutes(defaultDurationMinutes);
        }
        if (aLaCartePriceCents != null) {
            service.setALaCartePriceCents(aLaCartePriceCents);
        }
        if (description != null) {
            service.setDescription(description);
        }
        if (isFreeWithEveryVisit != null) {
            service.setFreeWithEveryVisit(isFreeWithEveryVisit);
        }

        return serviceRepository.save(service);
    }

    /**
     * Archives a service (sets {@code active = false}). Never a hard delete — every FK into
     * {@code service} ({@code visit_service}, {@code plan_tier_service},
     * {@code visit_template_service}) is {@code ON DELETE RESTRICT}, and archiving is the
     * intended mechanism: existing {@code visit_service} rows keep pointing at this service
     * (its id, name, and history are untouched), it simply drops out of
     * {@code GET /api/catalog/picks} (which filters {@code active = true}) for future
     * bookings. Idempotent — archiving an already-archived service is a no-op success.
     *
     * @throws ServiceNotFoundException if no service exists with this id (404)
     */
    @Transactional
    public com.homekept.catalog.Service archiveService(Long id) {
        com.homekept.catalog.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException(id));
        service.setActive(false);
        return serviceRepository.save(service);
    }

    /**
     * Restores an archived service (sets {@code active = true}). Idempotent — restoring an
     * already-active service is a no-op success.
     *
     * @throws ServiceNotFoundException if no service exists with this id (404)
     */
    @Transactional
    public com.homekept.catalog.Service restoreService(Long id) {
        com.homekept.catalog.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException(id));
        service.setActive(true);
        return serviceRepository.save(service);
    }

    // ── Plan composition ──────────────────────────────────────────────────────

    /**
     * Adds a service to a plan tier's composition at the given frequency.
     *
     * <p>{@code plan_tier_service} has a composite primary key
     * (plan_tier_id, service_id) — a second "add" for the same pair is a duplicate, not a
     * new row. Checked with an explicit {@code existsById} before the insert so a duplicate
     * surfaces as a clean 409 rather than a {@code DataIntegrityViolationException} from a
     * constraint violation. Callers who want to change an existing row's frequency use
     * {@link #updateFrequency}.
     *
     * @throws PlanTierNotFoundException        if planTierId does not exist (404)
     * @throws ServiceNotFoundException         if serviceId does not exist (404)
     * @throws PlanTierServiceConflictException if this service is already part of this plan
     *                                           tier (409)
     */
    @Transactional
    public PlanTierService addServiceToPlan(Long planTierId, Long serviceId, int frequencyPerYear) {
        PlanTier planTier = planTierRepository.findById(planTierId)
                .orElseThrow(() -> new PlanTierNotFoundException(planTierId));
        com.homekept.catalog.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        PlanTierService.PlanTierServiceId id = new PlanTierService.PlanTierServiceId(planTierId, serviceId);
        if (planTierServiceRepository.existsById(id)) {
            throw new PlanTierServiceConflictException(
                    "Service " + serviceId + " is already part of plan tier " + planTierId
                            + ". Use PATCH /api/admin/plan-tiers/" + planTierId + "/services/" + serviceId
                            + " to change its frequency.");
        }

        return planTierServiceRepository.save(new PlanTierService(planTier, service, frequencyPerYear));
    }

    /**
     * Changes the frequency of a service already in a plan tier's composition.
     *
     * @throws PlanTierNotFoundException        if planTierId does not exist (404)
     * @throws ServiceNotFoundException         if serviceId does not exist (404)
     * @throws PlanTierServiceNotFoundException if the pair exists as entities but this
     *                                           service is not currently part of this plan
     *                                           tier's composition (404)
     */
    @Transactional
    public PlanTierService updateFrequency(Long planTierId, Long serviceId, int frequencyPerYear) {
        PlanTierService pts = findComposition(planTierId, serviceId);
        pts.setFrequencyPerYear(frequencyPerYear);
        return planTierServiceRepository.save(pts);
    }

    /**
     * Removes a service from a plan tier's composition.
     *
     * @throws PlanTierNotFoundException        if planTierId does not exist (404)
     * @throws ServiceNotFoundException         if serviceId does not exist (404)
     * @throws PlanTierServiceNotFoundException if this service is not currently part of this
     *                                           plan tier's composition (404)
     */
    @Transactional
    public void removeServiceFromPlan(Long planTierId, Long serviceId) {
        PlanTierService pts = findComposition(planTierId, serviceId);
        planTierServiceRepository.delete(pts);
    }

    /**
     * Resolves an existing composition row, checking the plan tier and service ids
     * individually first so the 404 message names whichever id is actually wrong, rather
     * than a generic "no such composition."
     */
    private PlanTierService findComposition(Long planTierId, Long serviceId) {
        if (!planTierRepository.existsById(planTierId)) {
            throw new PlanTierNotFoundException(planTierId);
        }
        if (!serviceRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }
        return planTierServiceRepository
                .findWithAssociationsById(planTierId, serviceId)
                .orElseThrow(() -> new PlanTierServiceNotFoundException(planTierId, serviceId));
    }
}
