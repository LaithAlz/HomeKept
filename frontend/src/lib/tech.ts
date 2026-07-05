/**
 * TanStack Query hooks for the technician app
 * (`backend/src/main/java/com/homekept/visit/TechVisitController.java`).
 *
 * Field names below mirror the backend DTOs verbatim — see
 * `backend/src/main/java/com/homekept/visit/dto/Tech*.java` and
 * `VisitServiceItem.java`. `VisitStatus`/`VisitType`/`VisitServiceItem` are
 * shared with the customer app's visit DTOs (`@/lib/visits`) since the
 * backend enums and the checklist item shape are identical.
 *
 * SENSITIVE DATA: `TechVisitListItem.accessNotes` is decrypted plaintext
 * (lockbox/alarm codes) and the address fields are customer PII. Nothing in
 * this module logs, query-strings, or persists (localStorage/sessionStorage)
 * any field of a `TechVisitListItem` — callers must not do so either.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { get, patch, post } from "@/lib/api";
import type { VisitServiceItem, VisitStatus, VisitType } from "@/lib/visits";

export type { VisitServiceItem, VisitStatus, VisitType };

export type FlagSeverity = "INFO" | "ATTENTION" | "URGENT";

/**
 * A single visit on the technician's day sheet
 * (see `TechVisitListItem.java`).
 *
 * `accessNotes` is decrypted plaintext. Render as plain text only, inside
 * the authenticated day-sheet view — never log it, never put it in a
 * URL/query string, never persist it to localStorage/sessionStorage.
 */
export interface TechVisitListItem {
  id: number;
  name: string;
  scheduledFor: string; // ISO instant (UTC)
  durationMinutes: number;
  status: VisitStatus;
  type: VisitType;
  streetAddress: string;
  unit: string | null;
  city: string;
  postalCode: string;
  accessNotes: string;
  services: VisitServiceItem[];
}

const DAY_SHEET_KEY = ["tech-day-sheet"] as const;

/**
 * GET /api/tech/visits/today — the authenticated technician's day sheet.
 *
 * `enabled` must stay `false` until the `/tech` route guard has confirmed
 * the session is authenticated AND `role === "TECHNICIAN"` — this call
 * returns decrypted access notes and customer PII, so it must never fire on
 * behalf of a signed-out or wrong-role visitor.
 */
export function useTechDaySheet(enabled: boolean): UseQueryResult<TechVisitListItem[]> {
  return useQuery({
    queryKey: DAY_SHEET_KEY,
    queryFn: () => get<TechVisitListItem[]>("/api/tech/visits/today"),
    enabled,
  });
}

export interface TechStartVisitResponse {
  id: number;
  status: VisitStatus;
}

/** POST /api/tech/visits/{id}/start — legal only from SCHEDULED. */
export function useStartVisit(): UseMutationResult<TechStartVisitResponse, unknown, number> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (visitId: number) =>
      post<TechStartVisitResponse>(`/api/tech/visits/${visitId}/start`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DAY_SHEET_KEY }),
  });
}

export interface TechPatchServiceRequest {
  completed: boolean;
  technicianNotes: string | null;
}

interface PatchServiceVariables {
  visitId: number;
  visitServiceId: number;
  request: TechPatchServiceRequest;
}

/** PATCH /api/tech/visits/{id}/services/{visitServiceId} — tick/untick a checklist item. */
export function usePatchService(): UseMutationResult<
  VisitServiceItem,
  unknown,
  PatchServiceVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ visitId, visitServiceId, request }) =>
      patch<VisitServiceItem>(`/api/tech/visits/${visitId}/services/${visitServiceId}`, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DAY_SHEET_KEY }),
  });
}

export interface TechCreateFlagRequest {
  body: string;
  severity: FlagSeverity;
  photoStorageKey?: string | null;
}

export interface FlagResponse {
  id: number;
  subscriberId: number;
  originVisitId: number;
  body: string;
  severity: FlagSeverity;
  status: string;
  photoStorageKey: string | null;
  createdAt: string;
}

interface CreateFlagVariables {
  visitId: number;
  request: TechCreateFlagRequest;
}

/**
 * POST /api/tech/visits/{id}/flags — raises an OPEN flag on the visit's
 * subscriber. Does not change this visit's own checklist (a flag folds into
 * a *future* visit as a `FLAGGED` checklist item), so the day sheet is not
 * invalidated here.
 */
export function useCreateFlag(): UseMutationResult<FlagResponse, unknown, CreateFlagVariables> {
  return useMutation({
    mutationFn: ({ visitId, request }) =>
      post<FlagResponse>(`/api/tech/visits/${visitId}/flags`, request),
  });
}

export type TodoItemStatus = "OPEN" | "SCHEDULED" | "DONE" | "DECLINED";

export interface TechPatchTodoRequest {
  status: "DONE" | "DECLINED";
  note?: string | null;
}

export interface TodoResponse {
  id: number;
  subscriberId: number;
  body: string;
  status: TodoItemStatus;
  visitId: number | null;
  declineNote: string | null;
  createdAt: string;
  updatedAt: string;
}

interface PatchTodoVariables {
  todoId: number;
  request: TechPatchTodoRequest;
}

/**
 * PATCH /api/tech/todos/{id} — mark a customer "your list" item done or
 * declined in the field.
 *
 * NOT wired into the day-sheet UI: `GET /api/tech/visits/today`
 * (`TechVisitListItem`) does not return todo items, and there is no list
 * endpoint that enumerates a visit's todo ids for the technician — so the
 * client has nothing to PATCH against yet. Implemented per the DTO contract
 * so the read side can wire straight in once it exists.
 */
export function usePatchTodo(): UseMutationResult<TodoResponse, unknown, PatchTodoVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ todoId, request }) => patch<TodoResponse>(`/api/tech/todos/${todoId}`, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DAY_SHEET_KEY }),
  });
}

export interface TechCompleteVisitRequest {
  completionNotes: string | null;
  actualDurationMinutes: number;
  materialsCostCents: number;
  materialsNotes: string | null;
}

export interface TechCompleteVisitResponse {
  id: number;
  status: VisitStatus;
  completedAt: string;
  actualDurationMinutes: number;
  materialsCostCents: number;
}

interface CompleteVisitVariables {
  visitId: number;
  request: TechCompleteVisitRequest;
}

/** POST /api/tech/visits/{id}/complete — legal only from IN_PROGRESS. */
export function useCompleteVisit(): UseMutationResult<
  TechCompleteVisitResponse,
  unknown,
  CompleteVisitVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ visitId, request }) =>
      post<TechCompleteVisitResponse>(`/api/tech/visits/${visitId}/complete`, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DAY_SHEET_KEY }),
  });
}

export interface TechIncompleteVisitRequest {
  reason: string;
}

export interface TechIncompleteVisitResponse {
  id: number;
  status: VisitStatus;
  followUpVisitId: number;
  followUpScheduledFor: string;
}

interface IncompleteVisitVariables {
  visitId: number;
  request: TechIncompleteVisitRequest;
}

/**
 * POST /api/tech/visits/{id}/incomplete — legal only from IN_PROGRESS.
 * Auto-creates a follow-up SCHEDULED visit 7 days out (server-side).
 */
export function useIncompleteVisit(): UseMutationResult<
  TechIncompleteVisitResponse,
  unknown,
  IncompleteVisitVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ visitId, request }) =>
      post<TechIncompleteVisitResponse>(`/api/tech/visits/${visitId}/incomplete`, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DAY_SHEET_KEY }),
  });
}

// ---------------------------------------------------------------------------
// Photos — requires Cloudflare R2 (#58). Not configured in every environment;
// the upload-url step responds 503 (STORAGE_UNAVAILABLE) until it is.
// ---------------------------------------------------------------------------

export interface TechPhotoUploadUrlResponse {
  uploadUrl: string;
  storageKey: string;
}

export interface TechPhotoResponse {
  id: number;
  visitId: number;
  storageKey: string;
  caption: string | null;
  takenAt: string | null;
  createdAt: string;
}

interface UploadPhotoVariables {
  visitId: number;
  file: File;
  caption?: string | null;
}

/**
 * Full photo-attach flow: presign → PUT to R2 → confirm.
 *
 * Depends on Cloudflare R2 being configured on the backend (#58). Until
 * then, the presign call 503s and this mutation rejects; callers must
 * surface a calm error and must NOT record a photo as saved when it wasn't.
 */
export function useUploadPhoto(): UseMutationResult<
  TechPhotoResponse,
  unknown,
  UploadPhotoVariables
> {
  return useMutation({
    mutationFn: async ({ visitId, file, caption }: UploadPhotoVariables) => {
      const { uploadUrl, storageKey } = await post<TechPhotoUploadUrlResponse>(
        `/api/tech/visits/${visitId}/photos/upload-url`,
        { contentType: file.type },
      );

      // Direct PUT to the signed R2 URL — deliberately not routed through
      // the api.ts wrapper (different origin, no cookies to send, no JSON
      // error envelope to parse).
      const putRes = await fetch(uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type },
        body: file,
      });
      if (!putRes.ok) {
        throw new Error("Photo upload failed.");
      }

      return post<TechPhotoResponse>(`/api/tech/visits/${visitId}/photos`, {
        storageKey,
        caption: caption ?? null,
      });
    },
  });
}
