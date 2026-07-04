/**
 * TanStack Query hooks for the customer-facing visit endpoints
 * (`backend/src/main/java/com/homekept/visit/AppVisitController.java`).
 *
 * Field names below mirror the backend DTOs verbatim — see
 * `backend/src/main/java/com/homekept/visit/dto/AppVisitListItem.java`,
 * `AppVisitDetail.java`, and `VisitServiceItem.java`.
 */

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { get } from "@/lib/api";

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
