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

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { get, post } from "@/lib/api";

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

// ---------------------------------------------------------------------------
// Billing self-serve: portal handoff + pause/resume/cancel
// ---------------------------------------------------------------------------

interface PortalSessionResponse {
  portalUrl: string;
}

export interface SubscriptionActionResponse {
  status: SubscriberStatus;
  currentPeriodEnd?: string; // ISO instant
}

/**
 * Defense-in-depth for a money-adjacent redirect, mirroring
 * `trustedStripeCheckoutUrl` in `routes/plans.tsx`: only ever returns a URL whose
 * scheme is `https:` and whose host is exactly `billing.stripe.com` or a
 * `*.stripe.com` subdomain (the leading dot in `.stripe.com` matters — it's what
 * keeps a host like `evilstripe.com` from matching). Returns `null` for anything
 * else, including unparseable strings.
 */
function trustedStripeBillingUrl(portalUrl: string): string | null {
  try {
    const u = new URL(portalUrl);
    if (
      u.protocol === "https:" &&
      (u.hostname === "billing.stripe.com" || u.hostname.endsWith(".stripe.com"))
    ) {
      return u.toString();
    }
  } catch {
    /* unparseable — fall through to null below */
  }
  return null;
}

/**
 * POST /api/billing/portal-session — full-page redirect to Stripe's hosted
 * customer portal (plan change, payment method, invoices). Throws if the
 * backend somehow returns a non-Stripe URL rather than silently navigating
 * away from the app on a money-adjacent path.
 */
export function usePortalSession(): UseMutationResult<void, unknown, void> {
  return useMutation({
    mutationFn: async () => {
      const { portalUrl } = await post<PortalSessionResponse>("/api/billing/portal-session");
      const dest = trustedStripeBillingUrl(portalUrl);
      if (!dest) throw new Error("Untrusted billing portal URL.");
      window.location.assign(dest);
    },
  });
}

/**
 * POST /api/app/subscription/pause — requires ACTIVE, else `409
 * ILLEGAL_STATE_TRANSITION`; no Stripe subscription yet → `409
 * NO_BILLING_ACCOUNT`. Invalidates the subscription query on success; the
 * status itself flips when the Stripe webhook lands.
 */
export function usePauseSubscription(): UseMutationResult<
  SubscriptionActionResponse,
  unknown,
  void
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => post<SubscriptionActionResponse>("/api/app/subscription/pause"),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["app-subscription"] });
    },
  });
}

/** POST /api/app/subscription/resume — requires PAUSED, else `409 ILLEGAL_STATE_TRANSITION`. */
export function useResumeSubscription(): UseMutationResult<
  SubscriptionActionResponse,
  unknown,
  void
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => post<SubscriptionActionResponse>("/api/app/subscription/resume"),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["app-subscription"] });
    },
  });
}

/**
 * POST /api/app/subscription/cancel — cancel-at-period-end; `reason` is
 * required churn data (blank → `400`). Already-cancelled → `409`.
 */
export function useCancelSubscription(): UseMutationResult<
  SubscriptionActionResponse,
  unknown,
  string
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) =>
      post<SubscriptionActionResponse>("/api/app/subscription/cancel", { reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["app-subscription"] });
    },
  });
}
