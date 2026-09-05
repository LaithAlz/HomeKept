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
import { ApiError, get, patch, post } from "@/lib/api";

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
  currentPeriodStart?: string; // ISO instant
  currentPeriodEnd?: string; // ISO instant
  nextVisitDate?: string; // ISO instant; absent when no SCHEDULED visit exists
}

export interface AppAccount {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string; // absent until the phone-on-file work (PATCH /api/app/account) lands
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

export interface UpdateAccountRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
}

/**
 * PATCH /api/app/account — updates the editable slice of the profile (name, phone).
 * Returns the same shape as `GET /api/app/account`; on success that response replaces
 * the cached account so every reader (this page, AppShell's avatar/name) sees it
 * immediately without a refetch.
 */
export function useUpdateAccount(): UseMutationResult<AppAccount, unknown, UpdateAccountRequest> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateAccountRequest) => patch<AppAccount>("/api/app/account", body),
    onSuccess: (data) => {
      queryClient.setQueryData(["app-account"], data);
    },
  });
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** POST /api/auth/change-password — 204 on success; 400 when `currentPassword` is wrong. */
export function useChangePassword(): UseMutationResult<void, unknown, ChangePasswordRequest> {
  return useMutation({
    mutationFn: (body: ChangePasswordRequest) => post<void>("/api/auth/change-password", body),
  });
}

// ---------------------------------------------------------------------------
// Billing details: payment method + invoices
// ---------------------------------------------------------------------------

export interface AppPaymentMethod {
  brand: string;
  last4: string;
  expMonth: number;
  expYear: number;
}

/**
 * GET /api/app/billing/payment-method — the default card on file, or `null` when there
 * is none. Treats a `404` (endpoint not deployed yet, or no Stripe customer/payment
 * method at all) the same as a `null` body so the page always renders the same honest
 * empty state rather than an error.
 */
export function usePaymentMethod(): UseQueryResult<AppPaymentMethod | null> {
  return useQuery({
    queryKey: ["app-billing-payment-method"],
    queryFn: async () => {
      try {
        return (await get<AppPaymentMethod | null>("/api/app/billing/payment-method")) ?? null;
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null;
        throw err;
      }
    },
  });
}

export type InvoiceStatus = "draft" | "open" | "paid" | "uncollectible" | "void" | string;

export interface AppInvoice {
  id: string;
  number: string;
  createdAt: string; // ISO instant
  amountPaidCents: number;
  currency: string; // e.g. "cad"
  status: InvoiceStatus;
  hostedInvoiceUrl: string | null;
  invoicePdf: string | null;
}

/**
 * GET /api/app/billing/invoices — newest first. Treats a `404` (endpoint not deployed
 * yet, or no Stripe customer at all) the same as an empty list, so the page always
 * renders the "No invoices yet." empty state rather than an error.
 */
export function useInvoices(): UseQueryResult<AppInvoice[]> {
  return useQuery({
    queryKey: ["app-billing-invoices"],
    queryFn: async () => {
      try {
        return (await get<AppInvoice[]>("/api/app/billing/invoices")) ?? [];
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return [];
        throw err;
      }
    },
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
