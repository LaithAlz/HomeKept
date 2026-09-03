/**
 * Session plumbing for the customer app.
 *
 * Auth is cookie-based (httpOnly, set by the backend on `/api/auth/login`);
 * the frontend never sees or stores a token. `getSession()` is the only way
 * to learn "am I signed in" — it calls `GET /api/auth/me` and treats a `401`
 * as signed-out rather than an error (any other failure, e.g. a network
 * error or `5xx`, is rethrown so callers can distinguish "not signed in"
 * from "couldn't find out").
 *
 * Cookies live on the API origin, so this must only ever be called
 * client-side — a server-side render on Cloudflare has no user cookies to
 * forward. See `AppShell` for the guard that enforces this.
 */

import { useEffect } from "react";
import { useNavigate, useRouterState } from "@tanstack/react-router";
import { ApiError, get, post } from "@/lib/api";

export type Role = "CUSTOMER" | "TECHNICIAN" | "ADMIN";

export interface Session {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
}

/** Resolves the signed-in user, or `null` if there is no session. */
export async function getSession(): Promise<Session | null> {
  try {
    return await get<Session>("/api/auth/me");
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      return null;
    }
    throw err;
  }
}

/**
 * The app shell a given role lands in. Used both after sign-in (when there's
 * no explicit `next`) and by each shell's role guard when it rejects a
 * session that's signed in but holds the wrong role, so every entry point
 * sends a role to the same home consistently.
 */
export function homeFor(role: Role): "/app" | "/tech" | "/admin" {
  if (role === "ADMIN") return "/admin";
  if (role === "TECHNICIAN") return "/tech";
  return "/app";
}

/** Revokes the session (clears cookies server-side). Always resolves. */
export async function logout(): Promise<void> {
  try {
    await post<void>("/api/auth/logout");
  } catch {
    // Logout is best-effort from the client's point of view: the backend
    // clears cookies unconditionally and never leaks token validity, so
    // there is nothing actionable to surface here.
  }
}

/**
 * Redirects to sign-in when a query/mutation fails with 401.
 *
 * `/app/*` routes are guarded by `AppShell` before they render, so this
 * should be rare in normal use — it only fires if the session cookie
 * expires mid-visit. Mirrors the exact redirect `AppShell`'s guard performs
 * (same destination, same `next` param) rather than introducing a new auth
 * flow.
 */
export function useSessionExpiredRedirect(error: unknown): void {
  const navigate = useNavigate();

  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      // Read the path once here rather than depending on the router's reactive
      // pathname, which changes mid-transition and would re-fire with next=/signin.
      navigate({ to: "/signin", search: { next: window.location.pathname }, replace: true });
    }
  }, [error, navigate]);
}
