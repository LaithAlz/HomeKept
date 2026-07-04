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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

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

  return (await res.json()) as T;
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
