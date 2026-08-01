/**
 * Thin, client-only wrapper around `posthog-js` so callers never import
 * PostHog directly (mirrors the encapsulation of `lib/error-capture.ts` and
 * the backend's `AnalyticsService`). See
 * `backend/homekept-backend-architecture.md` §5.7 for the full spec this
 * implements.
 *
 * **Env-gated, total no-op without a key.** `VITE_PUBLIC_POSTHOG_KEY` is
 * unset in local dev and CI, and the app must work perfectly with analytics
 * off: every export here routes through `ensureReady()`, which only ever
 * calls `posthog.init(...)` when a key is configured, and every export
 * no-ops until that succeeds — no network calls, no thrown errors.
 *
 * **SSR-safe.** This is TanStack Start SSR on a Cloudflare Worker; PostHog
 * is browser-only. `ensureReady()` bails immediately when `window` is
 * undefined, and nothing else in this module touches `posthog` unless
 * `ensureReady()` has already succeeded client-side — so calling any of
 * these functions during a server render is always inert.
 *
 * **Cookieless.** `persistence: "memory"` everywhere (marketing site,
 * booking wizard, customer + tech apps) means no consent banner is needed.
 * The tradeoff (per §5.7): an anonymous visitor's identity only survives one
 * page load.
 *
 * **Session replay is booking-wizard-only.** Recording starts disabled
 * (`disable_session_recording: true`); `startBookingReplay()` /
 * `stopBookingReplay()` toggle it, and only the booking route
 * (`routes/book.tsx`) is allowed to call `startBookingReplay()`. The
 * customer and tech apps must NEVER start a recording — they render
 * addresses, access notes, and photos as page text, which input masking
 * does not cover. When recording is on, inputs and text are still masked
 * (`maskAllInputs` + `maskTextSelector: "*"`): we want interaction patterns,
 * not content.
 *
 * **No PII.** `identify()` takes the internal numeric user id only, never
 * email or name. Event `props` must stay IDs/enums/counts only.
 */

import posthog from "posthog-js";

const POSTHOG_KEY = import.meta.env.VITE_PUBLIC_POSTHOG_KEY as string | undefined;
const POSTHOG_HOST =
  (import.meta.env.VITE_PUBLIC_POSTHOG_HOST as string | undefined) ?? "https://us.i.posthog.com";

/**
 * Canonical event taxonomy owned by the frontend (§5.7's table) — snake_case,
 * past tense. Backend-owned events live in the backend's `AnalyticsService`.
 */
export const ANALYTICS_EVENTS = {
  BOOKING_STEP_COMPLETED: "booking_step_completed",
  REPORT_VIEWED: "report_viewed",
} as const;

/**
 * The authenticated surfaces whose page TEXT is PII — the customer app renders
 * the home address, the tech app renders addresses AND access notes (how to get
 * into someone's house). Autocapture records the clicked element's text and
 * attributes, so on these paths that content must be scrubbed before send
 * (arch doc §5.7: input-masking does not cover page text). Session replay is
 * already banned here; this closes the same leak via autocapture.
 */
function isSensitivePath(path: string | undefined): boolean {
  return !!path && (path.startsWith("/app") || path.startsWith("/tech"));
}

/** Drops the query string + hash from a URL (activation / password-reset tokens live there). */
function stripUrlSecrets(url: unknown): unknown {
  if (typeof url !== "string") return url;
  const cut = Math.min(
    ...[url.indexOf("?"), url.indexOf("#")].filter((i) => i >= 0).concat(url.length),
  );
  return url.slice(0, cut);
}

/**
 * Runs on every event before it leaves the browser. Two PII guards:
 *  1. Strip the query string from `$current_url` / `$referrer` on ALL events, so
 *     single-use activation and password-reset tokens (`/activate?token=…`,
 *     `/reset-password?token=…`) never reach PostHog via `$pageview` or autocapture.
 *  2. On the authenticated `/app` and `/tech` surfaces, remove autocapture element
 *     text + attributes (addresses, access notes render as page text there), so
 *     autocapture records the interaction shape, not the content.
 */
function sanitizeProperties(properties: Record<string, unknown>): Record<string, unknown> {
  if (!properties) return properties;

  properties.$current_url = stripUrlSecrets(properties.$current_url);
  if ("$referrer" in properties) properties.$referrer = stripUrlSecrets(properties.$referrer);

  let path: string | undefined;
  if (typeof properties.$pathname === "string") {
    path = properties.$pathname;
  } else if (typeof properties.$current_url === "string") {
    try {
      path = new URL(properties.$current_url).pathname;
    } catch {
      path = undefined;
    }
  }

  if (isSensitivePath(path)) {
    for (const key of Object.keys(properties)) {
      if (key === "$el_text" || key === "$elements_chain" || key.includes("attr__")) {
        delete properties[key];
      }
    }
    if (Array.isArray(properties.$elements)) {
      properties.$elements = (properties.$elements as Array<Record<string, unknown>>).map((el) => {
        const clean: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(el)) {
          if (k === "$el_text" || k === "text" || k.includes("attr__")) continue;
          clean[k] = v;
        }
        return clean;
      });
    }
  }

  return properties;
}

let initialized = false;

/**
 * Lazily turns PostHog on, client-side only, the first time any wrapper
 * function is actually used — and only if a publishable key is configured.
 * Idempotent (safe to call from every export below on every call).
 *
 * This is deliberately NOT gated behind "has `initAnalytics()` run yet":
 * `RootComponent`'s mount effect (the app's normal init point) is a
 * *descendant* effect of nothing, but routes like the booking wizard mount
 * effects of their OWN on the same initial commit (e.g. a cold load of
 * `/book`) — and React fires child mount effects before parent mount
 * effects, so `BookFlow`'s effect can run before `__root.tsx`'s. Making
 * every entry point self-initializing removes that ordering hazard instead
 * of depending on it.
 *
 * Returns whether PostHog is active after this call, so callers can bail
 * out in one line.
 */
function ensureReady(): boolean {
  if (typeof window === "undefined") return false;
  if (initialized) return true;
  if (!POSTHOG_KEY) return false;

  posthog.init(POSTHOG_KEY, {
    api_host: POSTHOG_HOST,
    persistence: "memory",
    autocapture: true,
    capture_pageview: true,
    // Scrub PII out of every event before it is sent: query-string tokens from
    // all URLs, and autocapture element text/attributes on the /app + /tech
    // surfaces (addresses, access notes). See sanitizeProperties.
    sanitize_properties: sanitizeProperties,
    // Recording stays off until `startBookingReplay()` is called — booking
    // route only. Masking config applies whenever it does run.
    disable_session_recording: true,
    session_recording: {
      maskAllInputs: true,
      maskTextSelector: "*",
    },
  });
  initialized = true;
  return true;
}

/**
 * Initializes PostHog once, client-side only, and only if a publishable key
 * is configured. Called from the app root (`routes/__root.tsx`) so
 * initialization normally happens as early as possible; every other export
 * below also self-initializes on first use (see `ensureReady`), so calling
 * this is not load-bearing for correctness, only for eagerness. No-ops
 * entirely when `VITE_PUBLIC_POSTHOG_KEY` is unset — no network, no errors.
 */
export function initAnalytics(): void {
  ensureReady();
}

/** Identifies the signed-in user by internal id only — never email or name. */
export function identify(userId: number): void {
  if (!ensureReady()) return;
  posthog.identify(String(userId));
}

/** Clears identity (call on logout) so the next person on this device starts anonymous. */
export function resetIdentity(): void {
  if (!ensureReady()) return;
  posthog.reset();
}

/** Captures a product event. `props` must stay PII-free: ids, enums, counts only. */
export function capture(event: string, props?: Record<string, unknown>): void {
  if (!ensureReady()) return;
  posthog.capture(event, props);
}

/**
 * The current (anonymous or identified) distinct id, for identity stitching —
 * e.g. the booking wizard attaches this to `WalkthroughBookingRequest.posthogDistinctId`
 * so the backend can alias it to the new user at activation.
 */
export function getDistinctId(): string | undefined {
  if (!ensureReady()) return undefined;
  return posthog.get_distinct_id();
}

/**
 * Turns session replay ON. Booking wizard ONLY — see module doc. Do not call
 * this from the customer or tech app.
 */
export function startBookingReplay(): void {
  if (!ensureReady()) return;
  posthog.startSessionRecording();
}

/** Turns session replay back OFF. Call on booking route unmount. */
export function stopBookingReplay(): void {
  if (!ensureReady()) return;
  posthog.stopSessionRecording();
}
