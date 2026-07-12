/**
 * Admin console data access — bindings for the ADMIN-only endpoints documented in
 * backend/api-contract.md ("Admin console (role: ADMIN)").
 *
 * DTO shapes mirror the backend records field-for-field:
 *   - AdminBookingListItem / AdminBookingDetail / AdminPatchBookingRequest
 *     (backend/src/main/java/com/homekept/booking/dto/*.java)
 *   - AdminSubscriberListItem / AdminSubscriberDetail / AdminSubscriberPropertySummary
 *     (backend/src/main/java/com/homekept/subscription/dto/*.java)
 *   - AdminUpdateSkuRequest / AdminPropertySkuResponse
 *     (backend/src/main/java/com/homekept/property/dto/*.java)
 *   - AdminVisitListItem (backend/src/main/java/com/homekept/visit/dto/AdminVisitListItem.java)
 *   - AdminTechnicianListItem
 *     (backend/src/main/java/com/homekept/technician/dto/AdminTechnicianListItem.java)
 *   - AdminDashboardResponse
 *     (backend/src/main/java/com/homekept/dashboard/dto/AdminDashboardResponse.java)
 *
 * The subscriber DTOs are annotated `@JsonInclude(NON_NULL)` on the backend, so a
 * null field is omitted from the JSON body entirely rather than sent as `null` —
 * those fields are typed as optional (`?:`) here, not nullable. The booking, visit,
 * and technician DTOs have no such annotation, so nullable fields there are sent as
 * explicit `null` and typed with `| null`.
 *
 * Every hook is a thin TanStack Query wrapper over `get`/`post`/`patch` from
 * `@/lib/api`. Callers are responsible for only mounting these hooks once the
 * ADMIN role check in `AdminShell` has passed — see that component for the guard.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, patch, post } from "@/lib/api";
import { submitWalkthroughBooking, type WalkthroughBookingRequest } from "@/lib/booking";

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

/**
 * `POST /api/bookings/walkthrough` — the same public endpoint the customer
 * booking wizard uses (see `@/lib/booking`), invoked here so staff can log a
 * walk-through taken by phone or in person (the admin "New booking" sheet).
 * Invalidates every `["admin", "bookings", ...]` list query so the new
 * PENDING booking appears in the pipeline immediately.
 */
export function useCreateWalkthroughBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: WalkthroughBookingRequest) => submitWalkthroughBooking(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "bookings"] });
    },
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
  propertyId: number;
  streetAddress: string;
  city: string;
  postalCode: string;
  propertyType?: string;
  hasAccessNotes: boolean;
  /**
   * SKU sheet (technician-prep) fields captured by the walk-through and refined
   * over subsequent visits (#56). `AdminSubscriberPropertySummary` on the backend
   * has no `@JsonInclude(NON_NULL)` of its own, so an unset field arrives as an
   * explicit `null`, not an omitted key — typed `| null` here, not optional.
   */
  hvacFilterSizes: string | null;
  smokeCoDetectorModels: string | null;
  humidifierModel: string | null;
  waterHeaterAgeYears: number | null;
  waterHeaterFlushEligible: boolean | null;
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
 * Request body for `PATCH /api/admin/properties/{propertyId}/sku`. Every field is
 * optional/nullable on the backend (`AdminUpdateSkuRequest.java`) — an omitted key
 * or an explicit `null` both leave that column unchanged (partial/ongoing capture
 * as the SKU sheet is filled in over time; there is currently no way to clear a
 * field that was already set). `waterHeaterAgeYears` must be 0–100 when present.
 */
export interface AdminUpdateSkuRequest {
  hvacFilterSizes?: string | null;
  smokeCoDetectorModels?: string | null;
  humidifierModel?: string | null;
  waterHeaterAgeYears?: number | null;
  waterHeaterFlushEligible?: boolean | null;
}

/** Response body for `PATCH /api/admin/properties/{propertyId}/sku` — the updated SKU sheet. */
export interface AdminPropertySkuResponse {
  propertyId: number;
  hvacFilterSizes: string | null;
  smokeCoDetectorModels: string | null;
  humidifierModel: string | null;
  waterHeaterAgeYears: number | null;
  waterHeaterFlushEligible: boolean | null;
}

/**
 * `PATCH /api/admin/properties/{propertyId}/sku` — updates the property's SKU
 * sheet (#56). Invalidates every `["admin", "subscriber", ...]` detail query
 * (the subscriber whose property this is, keyed by subscriber id, not property
 * id) so the subscriber detail sheet refetches and shows the saved values.
 */
export function useUpdatePropertySku(propertyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AdminUpdateSkuRequest) =>
      patch<AdminPropertySkuResponse>(`/api/admin/properties/${propertyId}/sku`, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscriber"] });
    },
  });
}

/**
 * Formats integer cents as whole-dollar CAD. Every money field from the
 * backend is integer cents per CLAUDE.md, so this always divides by 100
 * before formatting (mirrors `formatCentsCad` in `@/lib/format`, which takes
 * the same cents-in shape for the customer-facing app).
 */
export function formatCentsCAD(cents: number | null | undefined): string {
  if (cents === undefined || cents === null) return "—";
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
    maximumFractionDigits: 0,
  }).format(cents / 100);
}

/* -------------------------------------------------------------------------- */
/* Reschedule requests                                                        */
/* -------------------------------------------------------------------------- */

export type RescheduleRequestStatus = "PENDING" | "CONFIRMED" | "DECLINED";

export interface AdminRescheduleRequestListItem {
  id: number;
  visitId: number;
  subscriberId: number;
  status: RescheduleRequestStatus;
  preferredDates: string[];
  adminNote: string | null;
  confirmedVisitId: number | null;
  createdAt: string;
}

/**
 * `GET /api/admin/reschedule-requests` — PENDING customer reschedule requests only,
 * oldest first. The endpoint itself scopes to PENDING (per api-contract.md), so
 * there's no status param to pass here.
 */
export function useAdminRescheduleRequests() {
  return useQuery({
    queryKey: ["admin", "reschedule-requests"],
    queryFn: () => get<AdminRescheduleRequestListItem[]>("/api/admin/reschedule-requests"),
  });
}

/* -------------------------------------------------------------------------- */
/* Visits                                                                     */
/* -------------------------------------------------------------------------- */

export type VisitStatus =
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "INCOMPLETE"
  | "CANCELLED"
  | "RESCHEDULED";

export type VisitType = "ROUTINE" | "EXTRA" | "WARRANTY" | "WALKTHROUGH";

export interface AdminVisitListItem {
  id: number;
  subscriberId: number;
  propertyId: number;
  technicianId: number | null;
  scheduledFor: string;
  durationMinutes: number;
  actualDurationMinutes: number | null;
  materialsCostCents: number | null;
  status: VisitStatus;
  type: VisitType;
  completedAt: string | null;
  createdAt: string;
}

/**
 * `GET /api/admin/visits?status=&cursor=&limit=` — cursor-paginated, newest first,
 * optional status filter. The admin visits page fetches a single page (limit 100,
 * no status param) and filters client-side, matching the pattern already used for
 * `useAdminBookings`/`useAdminSubscribers` — the pipeline is small enough at MVP
 * that this avoids a network round-trip per filter change.
 */
export function useAdminVisits(options?: {
  status?: VisitStatus;
  cursor?: number;
  limit?: number;
}) {
  const { status, cursor, limit } = options ?? {};
  return useQuery({
    queryKey: ["admin", "visits", status ?? "all", cursor ?? null, limit ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set("status", status);
      if (cursor) params.set("cursor", String(cursor));
      if (limit) params.set("limit", String(limit));
      const qs = params.toString();
      return get<AdminVisitListItem[]>(`/api/admin/visits${qs ? `?${qs}` : ""}`);
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Technicians                                                                */
/* -------------------------------------------------------------------------- */

export interface AdminTechnicianListItem {
  id: number;
  userId: number;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  role: string | null;
  userStatus: string | null;
  employeeStatus: string | null;
  hireDate: string | null;
  fullyLoadedHourlyCostCents: number | null;
  createdAt: string;
}

/**
 * `GET /api/admin/technicians` — full roster, no pagination (small dataset at MVP).
 */
export function useAdminTechnicians() {
  return useQuery({
    queryKey: ["admin", "technicians"],
    queryFn: () => get<AdminTechnicianListItem[]>("/api/admin/technicians"),
  });
}

/* -------------------------------------------------------------------------- */
/* Dashboard                                                                  */
/* -------------------------------------------------------------------------- */

export interface AdminDashboardResponse {
  activeSubscribers: number;
  mrrCents: number;
  pendingWalkthroughs: number;
  upcomingVisits: number;
  foundingRateSlotsRemaining: number;
}

/**
 * `GET /api/admin/dashboard` — aggregate metrics for the console home. Also the
 * source for the sidebar badge counts (Subscribers/Walk-throughs/Visits) in
 * `AdminShell`, so those badges never disagree with the dashboard page.
 */
export function useAdminDashboard() {
  return useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: () => get<AdminDashboardResponse>("/api/admin/dashboard"),
  });
}
