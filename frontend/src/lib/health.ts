/**
 * TanStack Query hook for the customer-facing Home Health Score endpoint
 * (`backend/src/main/java/com/homekept/visit/AppHealthScoreController.java`).
 *
 * Field names below mirror `backend/src/main/java/com/homekept/visit/dto/HealthScoreResponse.java`
 * and `HealthScoreFlaggedItem.java` verbatim. `severity` mirrors
 * `backend/src/main/java/com/homekept/visit/FlagSeverity.java` verbatim.
 */

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { get } from "@/lib/api";

export type FlagSeverity = "INFO" | "ATTENTION" | "URGENT";

/** See `HealthScoreFlaggedItem.java`. */
export interface HealthScoreFlaggedItem {
  id: number;
  body: string;
  severity: FlagSeverity;
  createdAt: string; // ISO instant (UTC)
}

/** See `HealthScoreResponse.java`. */
export interface HealthScoreResponse {
  score: number; // 0..100, computed on read
  delta: number; // change since the most recent snapshot (written at the last completed visit); 0 if none
  computedAt: string; // ISO instant (UTC) — when this score was computed (now)
  flagged: HealthScoreFlaggedItem[]; // the subscriber's OPEN flags
}

/** GET /api/app/health-score — the authenticated customer's live Home Health Score. */
export function useHealthScore(): UseQueryResult<HealthScoreResponse> {
  return useQuery({
    queryKey: ["app-health-score"],
    queryFn: () => get<HealthScoreResponse>("/api/app/health-score"),
  });
}
