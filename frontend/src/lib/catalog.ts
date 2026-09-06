/**
 * Public catalog data access — bindings for the unauthenticated catalog endpoints
 * documented in `backend/api-contract.md` (`GET /api/catalog/plans`, `GET /api/catalog/picks`).
 *
 * DTO shapes mirror the backend records field-for-field:
 *   - PlanTierResponse / ServiceSummary
 *     (backend/src/main/java/com/homekept/catalog/dto/PlanTierResponse.java, ServiceSummary.java)
 *   - PicksMenuResponse / PickServiceResponse
 *     (backend/src/main/java/com/homekept/catalog/dto/PicksMenuResponse.java, PickServiceResponse.java)
 *
 * Money is integer cents on both endpoints — format with `formatCentsCad` from `@/lib/format`,
 * never a float-dollar helper. These endpoints require no auth (see SecurityConfig's
 * permitAll allowlist), so the hooks below have no role guard.
 */

import { useQuery } from "@tanstack/react-query";
import { get } from "@/lib/api";

export type PlanCode = "ESSENTIAL" | "COMPLETE" | "PREMIER";
export type TierClass = "BASIC" | "MEDIUM" | "PREMIUM";
export type ServiceCategory = "HVAC" | "PLUMBING" | "EXTERIOR" | "SMART_HOME";

export interface ServiceSummary {
  /**
   * The service's id — added alongside the admin catalog-editing endpoints so the admin
   * console can address this composition row (`PATCH`/`DELETE
   * /api/admin/plan-tiers/{planTierId}/services/{serviceId}`) without a separate lookup.
   */
  id: number;
  name: string;
  tierClass: TierClass;
  frequencyPerYear: number;
}

export interface PlanTierResponse {
  /**
   * The plan tier's id — added alongside the admin catalog-editing endpoints so the admin
   * console can target `POST/PATCH/DELETE /api/admin/plan-tiers/{planTierId}/services...`.
   * Not stable/guessable across environments (ESSENTIAL was deleted and later re-inserted
   * by migration, so it does not keep its original id) — always read this field.
   */
  id: number;
  code: PlanCode;
  displayName: string;
  monthlyPriceCents: number;
  annualPriceCents: number;
  visitsPerYear: number;
  includedPicksPerYear: number;
  maxPremiumPicksPerYear: number;
  description: string;
  services: ServiceSummary[];
}

/** `GET /api/catalog/plans` — plan tiers with their included, standing services. */
export function useCatalogPlans() {
  return useQuery({
    queryKey: ["catalog", "plans"],
    queryFn: () => get<PlanTierResponse[]>("/api/catalog/plans"),
  });
}

export interface PickServiceResponse {
  id: number;
  name: string;
  category: ServiceCategory;
  aLaCartePriceCents: number;
  description: string;
  defaultDurationMinutes: number;
}

export interface PickGroup {
  aLaCartePriceCents: number;
  services: PickServiceResponse[];
}

export interface PicksMenuResponse {
  basic: PickGroup;
  medium: PickGroup;
  premium: PickGroup;
}

/** `GET /api/catalog/picks` — the à la carte pick menu, grouped by tier class. */
export function useCatalogPicks() {
  return useQuery({
    queryKey: ["catalog", "picks"],
    queryFn: () => get<PicksMenuResponse>("/api/catalog/picks"),
  });
}
