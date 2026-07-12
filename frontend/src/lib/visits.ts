/**
 * TanStack Query hooks for the customer-facing visit endpoints
 * (`backend/src/main/java/com/homekept/visit/AppVisitController.java`).
 *
 * Field names below mirror the backend DTOs verbatim — see
 * `backend/src/main/java/com/homekept/visit/dto/AppVisitListItem.java`,
 * `AppVisitDetail.java`, and `VisitServiceItem.java`.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { get, post } from "@/lib/api";

export type VisitStatus =
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "INCOMPLETE"
  | "CANCELLED"
  | "RESCHEDULED";

export type VisitType = "ROUTINE" | "EXTRA" | "WARRANTY" | "WALKTHROUGH";

export interface VisitServiceItem {
  id: number;
  serviceId: number;
  serviceName: string;
  source: string;
  completed: boolean;
  technicianNotes: string | null;
}

export interface AppVisitListItem {
  id: number;
  name: string;
  scheduledFor: string; // ISO instant (UTC)
  durationMinutes: number;
  status: VisitStatus;
  type: VisitType;
  technicianFirstName: string | null; // nullable — technician slice not yet built
  services: VisitServiceItem[];
  hasPendingRescheduleRequest: boolean; // true if a PENDING reschedule_request exists for this visit
}

/**
 * A single photo attached to a visit, as shown to the customer. Mirrors
 * `AppVisitPhoto.java` verbatim (issue #135). `url` is a signed R2 download URL with a
 * ~15-minute TTL, generated fresh on every `GET /api/app/visits/{id}` call — never
 * cache or persist it client-side. A photo is simply absent from this list (not a
 * placeholder) if R2 is unconfigured or signing failed for it.
 */
export interface AppVisitPhoto {
  url: string;
  caption: string | null;
  takenAt: string | null; // ISO instant (UTC)
}

export interface AppVisitDetail {
  id: number;
  name: string;
  scheduledFor: string;
  durationMinutes: number;
  actualDurationMinutes: number | null;
  materialsCostCents: number | null;
  status: VisitStatus;
  type: VisitType;
  completionNotes: string | null;
  completedAt: string | null;
  technicianFirstName: string | null; // nullable — technician slice not yet built
  services: VisitServiceItem[];
  photos: AppVisitPhoto[]; // empty if R2 unconfigured or no photos — never fabricated
  hasPendingRescheduleRequest: boolean; // true if a PENDING reschedule_request exists for this visit
}

interface ListVisitsParams {
  status?: VisitStatus;
  limit?: number;
  cursor?: number;
}

function visitsPath({ status, limit, cursor }: ListVisitsParams): string {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  if (limit) params.set("limit", String(limit));
  if (cursor) params.set("cursor", String(cursor));
  const qs = params.toString();
  return qs ? `/api/app/visits?${qs}` : "/api/app/visits";
}

/** GET /api/app/visits — cursor-paginated, newest/soonest first. */
export function useVisits(params: ListVisitsParams = {}): UseQueryResult<AppVisitListItem[]> {
  return useQuery({
    queryKey: ["app-visits", params],
    queryFn: () => get<AppVisitListItem[]>(visitsPath(params)),
  });
}

/** The subscriber's next scheduled visit (list capped to one row). */
export function useNextVisit(): UseQueryResult<AppVisitListItem[]> {
  return useVisits({ status: "SCHEDULED", limit: 1 });
}

/** Recent completed visits, newest first — used for the activity feed and past-visits list. */
export function useRecentCompletedVisits(limit = 10): UseQueryResult<AppVisitListItem[]> {
  return useVisits({ status: "COMPLETED", limit });
}

/** GET /api/app/visits/{id} */
export function useVisit(id: number): UseQueryResult<AppVisitDetail> {
  return useQuery({
    queryKey: ["app-visit", id],
    queryFn: () => get<AppVisitDetail>(`/api/app/visits/${id}`),
    enabled: Number.isFinite(id),
  });
}

/* -------------------------------------------------------------------------- */
/* Reschedule requests (issue #128)                                          */
/* -------------------------------------------------------------------------- */

export type RescheduleRequestStatus = "PENDING" | "CONFIRMED" | "DECLINED";

/**
 * Mirrors `backend/src/main/java/com/homekept/visit/dto/RescheduleRequestResponse.java`
 * verbatim (the body of a successful `POST .../reschedule-request`).
 */
export interface RescheduleRequestResponse {
  id: number;
  visitId: number;
  status: RescheduleRequestStatus;
  preferredDates: string[]; // ISO instants (UTC)
  createdAt: string;
}

/**
 * `POST /api/app/visits/{id}/reschedule-request` — records a PENDING reschedule request
 * with 1-5 proposed start times for admin confirmation. The mutation variable mirrors
 * `CreateRescheduleRequest.java` field-for-field: `preferredDates`, a list of 1-5 ISO
 * instants. There is no reason/note field on this endpoint — don't add one client-side.
 *
 * 404s if the visit isn't the caller's. 409s if the visit isn't SCHEDULED or a PENDING
 * request already exists for it (`RescheduleService.createRequest`); in both cases the
 * thrown `ApiError.message` is a pre-canned, safe string from the backend, safe to show
 * to the customer directly.
 *
 * On success, invalidates both the visits list(s) and this visit's detail query so
 * anything reading visit data picks up any change (the visit's own status is unchanged
 * by this call, but the request marks the underlying data stale regardless).
 */
export function useCreateRescheduleRequest(
  visitId: number,
): UseMutationResult<RescheduleRequestResponse, unknown, string[]> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (preferredDates: string[]) =>
      post<RescheduleRequestResponse>(`/api/app/visits/${visitId}/reschedule-request`, {
        preferredDates,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["app-visits"] });
      void queryClient.invalidateQueries({ queryKey: ["app-visit", visitId] });
    },
  });
}
