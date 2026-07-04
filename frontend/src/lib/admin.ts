/**
 * Admin console data access — bindings for the ADMIN-only endpoints documented in
 * backend/api-contract.md ("Admin console (role: ADMIN)").
 *
 * DTO shapes mirror the backend records field-for-field:
 *   - AdminBookingListItem / AdminBookingDetail / AdminPatchBookingRequest
 *     (backend/src/main/java/com/homekept/booking/dto/*.java)
 *   - AdminSubscriberListItem / AdminSubscriberDetail / AdminSubscriberPropertySummary
 *     (backend/src/main/java/com/homekept/subscription/dto/*.java)
 *
 * The subscriber DTOs are annotated `@JsonInclude(NON_NULL)` on the backend, so a
 * null field is omitted from the JSON body entirely rather than sent as `null` —
 * those fields are typed as optional (`?:`) here, not nullable. The booking DTOs
 * have no such annotation, so nullable fields there are sent as explicit `null`
 * and typed with `| null`.
 *
 * Every hook is a thin TanStack Query wrapper over `get`/`post`/`patch` from
 * `@/lib/api`. Callers are responsible for only mounting these hooks once the
 * ADMIN role check in `AdminShell` has passed — see that component for the guard.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, patch, post } from "@/lib/api";

/* -------------------------------------------------------------------------- */
/* Bookings (walk-through pipeline)                                           */
/* -------------------------------------------------------------------------- */

export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "PERFORMED"
  | "CONVERTED"
  | "LOST"
  | "NO_SHOW";

export interface AdminBookingListItem {
  id: number;
  status: BookingStatus;
  fullName: string;
  email: string;
  phone: string;
  city: string;
  propertyType: string;
  preferredWeek: string;
  timeOfDay: string;
  dayPreferences: string[];
  leadSource: string;
  scheduledFor: string | null;
  createdAt: string;
}

export interface AdminBookingDetail extends AdminBookingListItem {
  streetAddress: string;
  postalCode: string;
  yearBuilt: number | null;
  squareFootageRange: string | null;
  notes: string | null;
  performedAt: string | null;
  contactConsentAt: string | null;
  updatedAt: string;
}

export interface AdminPatchBookingRequest {
  status?: BookingStatus;
  scheduledFor?: string;
}

const bookingsKey = (status?: BookingStatus, limit?: number) =>
  ["admin", "bookings", status ?? "all", limit ?? null] as const;

/**
 * `GET /api/admin/bookings?status=&limit=` — the walk-through pipeline list.
 * `limit` is capped at 100 server-side; the pipeline is small enough at MVP
 * that a single page covers it (no cursor pagination UI yet).
 */
export function useAdminBookings(options?: { status?: BookingStatus; limit?: number }) {
  const { status, limit } = options ?? {};
  return useQuery({
    queryKey: bookingsKey(status, limit),
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set("status", status);
      if (limit) params.set("limit", String(limit));
      const qs = params.toString();
      return get<AdminBookingListItem[]>(`/api/admin/bookings${qs ? `?${qs}` : ""}`);
    },
  });
}

/** `PATCH /api/admin/bookings/{id}` — status transition (validated server-side). */
export function usePatchBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: AdminPatchBookingRequest }) =>
      patch<AdminBookingDetail>(`/api/admin/bookings/${id}`, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "bookings"] });
    },
  });
}

/** `POST /api/admin/bookings/{id}/activation-invite` — issue #35. */
export function useSendActivationInvite() {
  return useMutation({
    mutationFn: (bookingId: number) =>
      post<{ status: string }>(`/api/admin/bookings/${bookingId}/activation-invite`),
  });
}

/* -------------------------------------------------------------------------- */
/* Subscribers                                                                */
/* -------------------------------------------------------------------------- */

export interface AdminSubscriberListItem {
  id: number;
  status: string;
  planCode?: string;
  mrrCents?: number;
  foundingRate: boolean;
}

export interface AdminSubscriberPropertySummary {
  streetAddress: string;
  city: string;
  postalCode: string;
  propertyType?: string;
  hasAccessNotes: boolean;
}

export interface AdminSubscriberDetail {
  id: number;
  userId: number;
  status: string;
  planCode?: string;
  mrrCents?: number;
  foundingRate: boolean;
  billingCycle: string;
  stripeCustomerId?: string;
  stripeSubscriptionId?: string;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  startedAt?: string;
  pausedAt?: string;
  cancelledAt?: string;
  property?: AdminSubscriberPropertySummary;
  visits: unknown[];
}

/** `GET /api/admin/subscribers?cursor=&limit=` — cursor-paginated, newest first. */
export function useAdminSubscribers(options?: { cursor?: number; limit?: number }) {
  const { cursor, limit } = options ?? {};
  return useQuery({
    queryKey: ["admin", "subscribers", cursor ?? null, limit ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (cursor) params.set("cursor", String(cursor));
      if (limit) params.set("limit", String(limit));
      const qs = params.toString();
      return get<AdminSubscriberListItem[]>(`/api/admin/subscribers${qs ? `?${qs}` : ""}`);
    },
  });
}

/** `GET /api/admin/subscribers/{id}` — full detail incl. property summary. */
export function useAdminSubscriber(id: number | null) {
  return useQuery({
    queryKey: ["admin", "subscriber", id],
    queryFn: () => get<AdminSubscriberDetail>(`/api/admin/subscribers/${id}`),
    enabled: id !== null,
  });
}

/**
 * Formats integer cents as whole-dollar CAD (matches the existing
 * `formatCAD` convention in `@/lib/mock-admin`, which takes whole dollars —
 * this variant takes cents, since every money field from the backend is
 * integer cents per CLAUDE.md).
 */
export function formatCentsCAD(cents: number | undefined): string {
  if (cents === undefined) return "—";
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
    maximumFractionDigits: 0,
  }).format(cents / 100);
}
