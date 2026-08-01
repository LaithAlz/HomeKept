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
