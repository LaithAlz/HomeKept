/**
 * TanStack Query hooks for the customer-facing subscription + account endpoints
 * (`backend/src/main/java/com/homekept/subscription/SubscriptionController.java`).
 *
 * Field names mirror the backend DTOs verbatim — see
 * `backend/src/main/java/com/homekept/subscription/dto/AppSubscriptionResponse.java` and
 * `AppAccountResponse.java`. Both DTOs are annotated `@JsonInclude(NON_NULL)`: a field that
 * is `null` server-side is omitted from the JSON body entirely rather than sent as a literal
 * `null`. The optional (`?`) fields below reflect "may be absent from the response," not
 * "may be `null`."
 */

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { get } from "@/lib/api";

export type SubscriberStatus =
  | "PENDING_ACTIVATION"
  | "ACTIVE"
  | "PAUSED"
  | "PAYMENT_ISSUE"
  | "CANCELLED";

export type BillingCycle = "MONTHLY" | "ANNUAL";

export interface AppSubscription {
  status: SubscriberStatus;
  planCode?: string; // absent pre-checkout (PENDING_ACTIVATION — no plan tier assigned yet)
  planDisplayName?: string; // absent pre-checkout
  billingCycle: BillingCycle;
  priceCents?: number; // integer cents — the price actually charged; absent pre-checkout
  foundingRate: boolean;
  foundingRateExpiresAt?: string; // ISO instant; absent unless foundingRate is true
  currentPeriodStart?: string; // ISO instant
  currentPeriodEnd?: string; // ISO instant
  nextVisitDate?: string; // ISO instant; absent when no SCHEDULED visit exists
}

export interface AppAccount {
  firstName: string;
  lastName: string;
  email: string;
  streetAddress?: string;
  unit?: string; // absent when the property has no unit number
  city?: string;
  postalCode?: string;
}

/** GET /api/app/subscription — the authenticated customer's plan/billing summary. */
export function useSubscription(): UseQueryResult<AppSubscription> {
  return useQuery({
    queryKey: ["app-subscription"],
    queryFn: () => get<AppSubscription>("/api/app/subscription"),
  });
}

/** GET /api/app/account — the authenticated customer's profile + service property address. */
export function useAccount(): UseQueryResult<AppAccount> {
  return useQuery({
    queryKey: ["app-account"],
    queryFn: () => get<AppAccount>("/api/app/account"),
  });
}
