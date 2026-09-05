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
import { get, patch, post, qs } from "@/lib/api";
import { submitWalkthroughBooking, type WalkthroughBookingRequest } from "@/lib/booking";
import type { RescheduleRequestStatus, VisitStatus, VisitType } from "@/lib/visits";

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
  /**
   * ISO instant the activation invite was last sent, `null` before any invite. Backend
   * adds this to `AdminBookingListItem` (issue audit #4) — optional here defensively in
   * case a cached/older response predates the field.
   */
  invitedAt?: string | null;
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
    queryFn: () => get<AdminBookingListItem[]>(`/api/admin/bookings${qs({ status, limit })}`),
  });
}

/**
 * `PATCH /api/admin/bookings/{id}` — status transition (validated server-side).
 * Also invalidates the dashboard aggregate: a booking's status change can move
 * it in or out of the "pending walk-throughs" count.
 */
export function usePatchBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: AdminPatchBookingRequest }) =>
      patch<AdminBookingDetail>(`/api/admin/bookings/${id}`, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
  });
}

/**
 * `POST /api/admin/bookings/{id}/activation-invite` — issue #35. Invalidates the
 * bookings list so the newly-set `invitedAt` (issue audit #4) lands on refetch —
 * callers should still keep a short-lived local "sent" state for the optimistic UI
 * between the mutation resolving and the refetch completing.
 */
export function useSendActivationInvite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (bookingId: number) =>
      post<{ status: string }>(`/api/admin/bookings/${bookingId}/activation-invite`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "bookings"] });
    },
  });
}

/**
 * `POST /api/bookings/walkthrough` — the same public endpoint the customer
 * booking wizard uses (see `@/lib/booking`), invoked here so staff can log a
 * walk-through taken by phone or in person (the admin "New booking" sheet).
 * Invalidates every `["admin", "bookings", ...]` list query so the new
 * PENDING booking appears in the pipeline immediately, and the dashboard
 * aggregate so the "pending walk-throughs" metric/badge stays in sync too.
 */
export function useCreateWalkthroughBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: WalkthroughBookingRequest) => submitWalkthroughBooking(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
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
  billingCycle: string;
  stripeCustomerId?: string;
  stripeSubscriptionId?: string;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  startedAt?: string;
  pausedAt?: string;
  cancelledAt?: string;
  property?: AdminSubscriberPropertySummary;
}

/** `GET /api/admin/subscribers?cursor=&limit=` — cursor-paginated, newest first. */
export function useAdminSubscribers(options?: { cursor?: number; limit?: number }) {
  const { cursor, limit } = options ?? {};
  return useQuery({
    queryKey: ["admin", "subscribers", cursor ?? null, limit ?? null],
    queryFn: () => get<AdminSubscriberListItem[]>(`/api/admin/subscribers${qs({ cursor, limit })}`),
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

/* -------------------------------------------------------------------------- */
/* Subscriber activity + admin-initiated subscription actions                */
/* -------------------------------------------------------------------------- */

/** One row of `GET /api/admin/subscribers/{id}/events` — newest first. */
export interface AdminSubscriberEvent {
  id: number;
  type: string;
  source: "STRIPE_WEBHOOK" | "MANUAL" | "SYSTEM" | string;
  occurredAt: string;
  note: string | null;
  /** Who requested the action, when known (e.g. on `CANCELLATION_REQUESTED`). */
  by?: "ADMIN" | "CUSTOMER" | null;
  /** Whether the action took effect immediately, when known (e.g. an immediate cancellation). */
  immediate?: boolean | null;
}

/** `GET /api/admin/subscribers/{id}/events` — the subscriber's activity history. */
export function useAdminSubscriberEvents(id: number | null) {
  return useQuery({
    queryKey: ["admin", "subscriber-events", id],
    queryFn: () => get<AdminSubscriberEvent[]>(`/api/admin/subscribers/${id}/events`),
    enabled: id !== null,
  });
}

/** Response body shared by the admin cancel/pause/resume subscription actions. */
export interface AdminSubscriptionActionResponse {
  status: string;
  currentPeriodEnd?: string;
}

/** Request body for `POST /api/admin/subscribers/{id}/cancel`. */
export interface AdminCancelSubscriptionRequest {
  reason: string;
  immediately: boolean;
}

/**
 * Every admin subscription action can move the subscriber's status, touch its
 * `currentPeriodEnd`, and append an activity event — so all three invalidate the
 * same four query keys: this subscriber's detail, the subscribers list, this
 * subscriber's activity, and the dashboard aggregate (MRR/active-subscriber counts).
 */
function invalidateAfterAdminSubscriptionAction(
  queryClient: ReturnType<typeof useQueryClient>,
  id: number,
) {
  void queryClient.invalidateQueries({ queryKey: ["admin", "subscriber", id] });
  void queryClient.invalidateQueries({ queryKey: ["admin", "subscribers"] });
  void queryClient.invalidateQueries({ queryKey: ["admin", "subscriber-events", id] });
  void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
}

/**
 * `POST /api/admin/subscribers/{id}/cancel` — staff-initiated cancellation.
 * `409 NO_BILLING_ACCOUNT` if the subscriber has no Stripe subscription yet;
 * `409 ILLEGAL_STATE_TRANSITION` if the subscription's status changed since load.
 */
export function useAdminCancelSubscription(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AdminCancelSubscriptionRequest) =>
      post<AdminSubscriptionActionResponse>(`/api/admin/subscribers/${id}/cancel`, request),
    onSuccess: () => invalidateAfterAdminSubscriptionAction(queryClient, id),
  });
}

/**
 * `POST /api/admin/subscribers/{id}/pause` — staff-initiated pause. Sends `{}` as
 * the body (not `undefined`) so `post()` sets `Content-Type: application/json` —
 * the endpoint requires it even though there's nothing meaningful to send.
 */
export function useAdminPauseSubscription(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      post<AdminSubscriptionActionResponse>(`/api/admin/subscribers/${id}/pause`, {}),
    onSuccess: () => invalidateAfterAdminSubscriptionAction(queryClient, id),
  });
}

/**
 * `POST /api/admin/subscribers/{id}/resume` — staff-initiated resume. Sends `{}`
 * as the body for the same reason as pause above.
 */
export function useAdminResumeSubscription(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      post<AdminSubscriptionActionResponse>(`/api/admin/subscribers/${id}/resume`, {}),
    onSuccess: () => invalidateAfterAdminSubscriptionAction(queryClient, id),
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

/* -------------------------------------------------------------------------- */
/* Reschedule requests                                                        */
/* -------------------------------------------------------------------------- */

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

/** Request body for `POST /api/admin/reschedule-requests/{id}/confirm`. */
export interface AdminConfirmRescheduleRequest {
  scheduledFor: string;
  adminNote?: string;
}

/**
 * Both mutations below resolve a PENDING reschedule request and, on confirm,
 * reschedule the underlying visit (RESCHEDULED old + new SCHEDULED row, per
 * `RescheduleService`). Either way the request leaves the PENDING queue, the
 * visit list may change, and the dashboard's `upcomingVisits`/badge counts can
 * shift — so both invalidate all three query keys.
 */
function invalidateAfterRescheduleResolution(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["admin", "reschedule-requests"] });
  void queryClient.invalidateQueries({ queryKey: ["admin", "visits"] });
  void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
}

/**
 * `POST /api/admin/reschedule-requests/{id}/confirm` — reschedules the visit to
 * `scheduledFor` (typically one of the customer's proposed times) and marks the
 * request CONFIRMED. 404 if the request is missing; 409 if it's already resolved
 * or the visit is no longer reschedulable.
 */
export function useConfirmRescheduleRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: AdminConfirmRescheduleRequest }) =>
      post<AdminRescheduleRequestListItem>(`/api/admin/reschedule-requests/${id}/confirm`, request),
    onSuccess: () => invalidateAfterRescheduleResolution(queryClient),
  });
}

/**
 * `POST /api/admin/reschedule-requests/{id}/decline` — marks the request DECLINED
 * with a required `adminNote`. 404 if missing; 409 if already resolved.
 */
export function useDeclineRescheduleRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, adminNote }: { id: number; adminNote: string }) =>
      post<AdminRescheduleRequestListItem>(`/api/admin/reschedule-requests/${id}/decline`, {
        adminNote,
      }),
    onSuccess: () => invalidateAfterRescheduleResolution(queryClient),
  });
}

/* -------------------------------------------------------------------------- */
/* Visits                                                                     */
/* -------------------------------------------------------------------------- */

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
    queryFn: () => get<AdminVisitListItem[]>(`/api/admin/visits${qs({ status, cursor, limit })}`),
  });
}

/**
 * Request body for `PATCH /api/admin/visits/{id}` — mirrors
 * `AdminPatchVisitRequest.java` field-for-field. All fields optional; apply
 * only what's present:
 *   - `scheduledFor` → reschedule (old visit marked RESCHEDULED, a new
 *     SCHEDULED visit is created at the new time with the same services).
 *   - `status: "CANCELLED"` → cancel, via the visit state machine.
 *   - `technicianUserId` → assign/reassign the technician (the user's
 *     `userId`, not a `technician_profile` id — see `useAdminTechnicians`).
 * Supplying both `scheduledFor` and `status: "CANCELLED"` is rejected by the
 * backend with a 400 (ambiguous intent).
 */
export interface AdminPatchVisitRequest {
  status?: "CANCELLED";
  scheduledFor?: string;
  technicianUserId?: number;
}

/** Response body for `PATCH /api/admin/visits/{id}` — the updated (or newly created, on reschedule) visit. */
export interface AdminVisitResponse extends AdminVisitListItem {
  visitTemplateId: number | null;
  completionNotes: string | null;
}

/**
 * `PATCH /api/admin/visits/{id}` — reschedule / cancel / assign technician (see
 * `AdminPatchVisitRequest` above). Invalidates the visits list and the dashboard
 * aggregate so the "upcoming visits" card/badge and any visit-derived counts
 * stay in sync with the change.
 */
export function usePatchAdminVisit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: AdminPatchVisitRequest }) =>
      patch<AdminVisitResponse>(`/api/admin/visits/${id}`, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "visits"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
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

/**
 * Request body for `POST /api/admin/technicians` (`CreateTechnicianRequest.java`).
 * Onboards an existing user (who already has the TECHNICIAN role, assigned separately)
 * by creating their `technician_profile` row.
 */
export interface CreateTechnicianRequest {
  userId: number;
  fullyLoadedHourlyCostCents: number;
  employeeStatus?: string;
  hireDate?: string;
}

/** Response body for `POST /api/admin/technicians` (`TechnicianProfileResponse.java`). */
export interface TechnicianProfileResponse {
  id: number;
  userId: number;
  employeeStatus: string | null;
  hireDate: string | null;
  fullyLoadedHourlyCostCents: number | null;
  createdAt: string;
}

/**
 * `employeeStatus` is a free-form `VARCHAR(50)` on the backend (no CHECK constraint,
 * no Java enum — see `TechnicianProfile.java`/the V7 migration), so this option list
 * is a frontend convention, not a contract. Keep in sync with `humanize()`'s display
 * in `routes/admin.technicians.tsx` if it changes.
 */
export const EMPLOYEE_STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "ON_LEAVE", label: "On leave" },
  { value: "TERMINATED", label: "Terminated" },
];

/**
 * `POST /api/admin/technicians` — onboard a technician profile for an existing user
 * (issue audit #1). Invalidates the roster so the new row appears immediately.
 */
export function useCreateTechnician() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateTechnicianRequest) =>
      post<TechnicianProfileResponse>("/api/admin/technicians", request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "technicians"] });
    },
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

/* -------------------------------------------------------------------------- */
/* Display maps — subscriber status/plan, shared by the dashboard, the        */
/* subscribers list, and the subscriber detail sheet.                         */
/* -------------------------------------------------------------------------- */

export const STATUS_LABEL: Record<string, string> = {
  PENDING_ACTIVATION: "Pending activation",
  ACTIVE: "Active",
  PAUSED: "Paused",
  PAYMENT_ISSUE: "Payment issue",
  CANCELLED: "Cancelled",
};

export const STATUS_TONE: Record<string, string> = {
  PENDING_ACTIVATION: "bg-sky-500/10 text-sky-700",
  ACTIVE: "bg-emerald-500/10 text-emerald-700",
  PAUSED: "bg-muted text-muted-foreground",
  PAYMENT_ISSUE: "bg-rose-500/10 text-rose-700",
  CANCELLED: "bg-muted text-muted-foreground",
};

export const PLAN_LABEL: Record<string, string> = {
  COMPLETE: "Complete",
  PREMIER: "Premier",
};

/**
 * `preferredWeek` is a LocalDate ("YYYY-MM-DD") with no time-of-day meaning.
 * Anchoring it to UTC noon before formatting with an explicit UTC timeZone
 * avoids an off-by-one day depending on the viewer's local timezone.
 */
export function formatWeekOf(dateStr: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
  }).format(new Date(`${dateStr}T12:00:00Z`));
}

/* -------------------------------------------------------------------------- */
/* Visit day load (Routes page month-sidebar aggregate)                      */
/* -------------------------------------------------------------------------- */

/**
 * One row of `GET /api/admin/visits/day-load` — a local calendar day ("YYYY-MM-DD", in
 * the backend's configured render zone) with at least one SCHEDULED visit. Days with none
 * are omitted by the backend entirely, never sent as zero.
 */
export interface AdminVisitDayLoadItem {
  day: string;
  total: number;
  unassigned: number;
}

/**
 * `GET /api/admin/visits/day-load?from=&to=` — SCHEDULED-visit counts per local day,
 * backing the Routes page's month-sidebar calendar (`MonthLoadCalendar`). `from`/`to` are
 * inclusive "YYYY-MM-DD" local dates; the backend rejects a span over 62 days with
 * `INVALID_REQUEST`. Honest counts only by design — there is deliberately no
 * capacity/percentage field here (technician working hours aren't modelled).
 */
export function useAdminVisitDayLoad(options: { from: string; to: string }) {
  const { from, to } = options;
  return useQuery({
    queryKey: ["admin", "visit-day-load", from, to],
    queryFn: () => get<AdminVisitDayLoadItem[]>(`/api/admin/visits/day-load${qs({ from, to })}`),
  });
}
