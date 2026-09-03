/**
 * Small typed fetch wrapper for calls to the HomeKept backend.
 *
 * Base URL comes from `VITE_API_URL` (empty string means same-origin — the
 * default for local dev behind a proxy or for prod once the frontend and API
 * share an origin). Every request is sent with `credentials: "include"` so
 * session cookies travel cross-origin (api.homekept.ca) once auth exists.
 *
 * Non-2xx responses are parsed against the backend's error envelope
 * (see backend/src/main/java/com/homekept/common/GlobalExceptionHandler.java)
 * and thrown as a typed {@link ApiError}. Non-JSON error bodies (e.g. a
 * proxy's plain-text 502) are tolerated and fall back to a generic code.
 */

const BASE_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? "";

interface ErrorEnvelope {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
    request_id?: string;
  };
}

export class ApiError extends Error {
  status: number;
  code: string;
  fields?: Record<string, string>;
  requestId?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    fields?: Record<string, string>,
    requestId?: string,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.requestId = requestId;
  }
}

async function parseErrorBody(res: Response): Promise<ErrorEnvelope["error"]> {
  try {
    const body = (await res.json()) as ErrorEnvelope;
    if (body?.error?.code) return body.error;
  } catch {
    /* non-JSON body (e.g. proxy error page) — fall through to generic error */
  }
  return undefined;
}

function doFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
}

let refreshInFlight: Promise<boolean> | null = null;

const NO_REFRESH_PATHS = new Set([
  "/api/auth/login",
  "/api/auth/refresh",
  "/api/auth/logout",
  "/api/auth/forgot",
  "/api/auth/reset",
]);

/**
 * Silently rotates the access cookie via the refresh cookie (15-min access
 * token, 7-day refresh token — see api-contract.md). Concurrent callers
 * (e.g. several dashboard queries 401ing at once) share one in-flight
 * request rather than each firing their own refresh.
 */
function refreshAccessToken(): Promise<boolean> {
  refreshInFlight ??= doFetch("/api/auth/refresh", { method: "POST" })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await doFetch(path, init);

  // A 401 usually just means the short-lived access cookie expired while the
  // refresh cookie is still valid: try one silent refresh + retry before
  // surfacing the error. /api/auth/me is included (every page load's session
  // check goes through it); the endpoints that issue, rotate or revoke
  // tokens are not, so a wrong password is never silently retried.
  if (res.status === 401 && !NO_REFRESH_PATHS.has(path) && (await refreshAccessToken())) {
    res = await doFetch(path, init);
  }

  if (!res.ok) {
    const envelopeError = await parseErrorBody(res);
    throw new ApiError(
      res.status,
      envelopeError?.code ?? "UNKNOWN_ERROR",
      envelopeError?.message ?? "Something went wrong.",
      envelopeError?.fields,
      envelopeError?.request_id,
    );
  }

  if (res.status === 204) return undefined as T;

  // Several endpoints answer a 2xx with an empty body (e.g. POST
  // /api/auth/login, /refresh, /forgot, /reset all use
  // ResponseEntity.ok().build() / status(ACCEPTED).build()). res.json() on
  // an empty body throws a SyntaxError, which would make a successful
  // request look like a failure to callers. Read as text first and only
  // parse when there's something to parse.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function get<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(path, { ...init, method: "GET" });
}

export function post<T>(path: string, body?: unknown, init?: RequestInit): Promise<T> {
  return request<T>(path, {
    ...init,
    method: "POST",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export function patch<T>(path: string, body?: unknown, init?: RequestInit): Promise<T> {
  return request<T>(path, {
    ...init,
    method: "PATCH",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export function del<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(path, { ...init, method: "DELETE" });
}

/**
 * Builds a `?a=b&c=d` query string from a params object, skipping any value
 * that's undefined, empty, or 0 (matching the truthy-check convention used
 * across the list endpoints — e.g. no `cursor` param on the first page).
 * Returns `""` (not `"?"`) when nothing is set.
 */
export function qs(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}

/** The `ApiError`'s message when there is one, otherwise a generic fallback. */
export function messageFor(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  return "Something went wrong. Try again.";
}
