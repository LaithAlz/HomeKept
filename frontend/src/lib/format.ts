// Date/time formatting helpers for the customer app.
//
// Visit times are always rendered in America/Toronto — the business's
// operating region — regardless of the viewer's device timezone, mirroring
// the backend's rendering rule (UTC stored, America/Toronto rendered).

export const TZ = "America/Toronto";

export interface CalendarParts {
  /** Short month, e.g. "Jul" */
  month: string;
  /** Day of month, e.g. 4 */
  day: number;
  /** Full weekday name, e.g. "Saturday" */
  weekday: string;
}

export function getCalendarParts(iso: string): CalendarParts {
  const date = new Date(iso);
  const month = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, month: "short" }).format(date);
  const day = Number(
    new Intl.DateTimeFormat("en-CA", { timeZone: TZ, day: "numeric" }).format(date),
  );
  const weekday = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, weekday: "long" }).format(date);
  return { month, day, weekday };
}

export function formatFullDate(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(new Date(iso));
}

export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  }).format(new Date(iso));
}

/** Renders a "1:00 – 4:00 PM" style window from a start time + duration. */
export function formatVisitWindow(scheduledForIso: string, durationMinutes: number): string {
  const start = new Date(scheduledForIso);
  const end = new Date(start.getTime() + durationMinutes * 60_000);
  return `${formatTime(start.toISOString())} – ${formatTime(end.toISOString())}`;
}

/** Whole days between now and the given ISO timestamp, floored at 0. */
export function daysUntil(iso: string, now: Date = new Date()): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - now.getTime()) / 86_400_000));
}

export function greetingFor(date: Date = new Date()): "morning" | "afternoon" | "evening" {
  const h = Number(
    new Intl.DateTimeFormat("en-CA", { timeZone: TZ, hour: "numeric", hourCycle: "h23" }).format(
      date,
    ),
  );
  if (h < 12) return "morning";
  if (h < 17) return "afternoon";
  return "evening";
}

/**
 * Formats integer cents as whole-dollar CAD, e.g. `14900` -> `"$149"`.
 * Money is always integer cents on the wire (never floats) — divide by 100 only for
 * display. Returns "—" for null/undefined (fields that are nullable pre-checkout
 * or before a value is recorded).
 */
export function formatCentsCad(cents: number | null | undefined): string {
  if (cents === undefined || cents === null) return "—";
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
    maximumFractionDigits: 0,
  }).format(cents / 100);
}

/**
 * Formats integer cents as an exact currency amount with cents shown, e.g. `16900` ->
 * `"$169.00"`. Unlike `formatCentsCad`, this never floors to a whole dollar: use it
 * wherever the amount is a real charged/paid total (a Stripe invoice, "your next charge
 * is exactly $X") rather than a marketing price. `currency` defaults to CAD (the only
 * currency subscriptions bill in) but accepts an invoice's own `currency` field verbatim.
 * Returns "—" for null/undefined, matching `formatCentsCad`.
 */
export function formatCentsExact(cents: number | null | undefined, currency = "CAD"): string {
  if (cents === undefined || cents === null) return "—";
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: currency.toUpperCase(),
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(cents / 100);
}

/**
 * Parses a dollar-amount string into integer cents without floating-point arithmetic —
 * splits on the decimal point and pads the fractional part instead of multiplying by 100,
 * so `"49.99"` always becomes `4999` exactly, never a float-rounding artifact like `4998`
 * or `5000`. Returns `null` for a blank input (the caller decides what "no price" means)
 * and `"invalid"` for anything that isn't a non-negative amount with at most two decimal
 * places. Used by the admin catalog's service form, which collects dollars but the API
 * (`AdminCreateServiceRequest`/`AdminUpdateServiceRequest`) takes integer cents.
 */
export function parseDollarsToCents(raw: string): number | null | "invalid" {
  const trimmed = raw.trim();
  if (trimmed === "") return null;
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) return "invalid";
  const [dollarsStr, centsStr = ""] = trimmed.split(".");
  const cents = Number(dollarsStr) * 100 + Number(centsStr.padEnd(2, "0"));
  return Number.isSafeInteger(cents) ? cents : "invalid";
}

/**
 * Inverse of `parseDollarsToCents`, for prefilling an editable dollar-amount input from a
 * stored cents value — integer division/modulo only, never a float divide. `null` (no
 * price set) becomes an empty string.
 */
export function centsToDollarsInput(cents: number | null): string {
  if (cents === null) return "";
  const dollars = Math.floor(cents / 100);
  const remainder = cents % 100;
  return remainder === 0 ? String(dollars) : `${dollars}.${String(remainder).padStart(2, "0")}`;
}

/**
 * "Jul 5" style short date, always rendered in America/Toronto. Relocated
 * from the deleted `@/lib/mock-admin` (used by the admin console).
 */
export function formatDateShort(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    month: "short",
    day: "numeric",
  }).format(new Date(iso));
}

/**
 * "Jul 5, 1:00 PM" style date + time, always rendered in America/Toronto.
 * Relocated from the deleted `@/lib/mock-admin` (used by the admin console).
 */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(iso));
}

/**
 * "Saturday, July 5, 2026" style long date, defaults to today. Relocated
 * from the deleted `@/lib/mock-admin` (used by the admin dashboard header).
 */
export function formatTodayLong(date: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

/**
 * "2026-07-05" style key in America/Toronto, used to group a real timestamp onto the
 * calendar day a viewer in any timezone would call "today" (or another specific day)
 * rather than the viewer's own local date. Relocated from `routes/admin.routes.tsx` (the
 * Routes page's original inline helper) so the new month-sidebar calendar can share it
 * instead of re-deriving the same "today in Toronto" key a second way.
 */
export function dayKey(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

/**
 * "Tuesday, September 8" style long date from a plain "YYYY-MM-DD" calendar-date string
 * (a `dayKey`, not a real timestamp) — anchored at UTC noon before formatting with an
 * explicit UTC timeZone, the same off-by-one-day guard `formatWeekOf` (`@/lib/admin`)
 * uses for a bare LocalDate. Used by the admin Routes page's day view and month sidebar.
 */
export function formatDayKeyLong(day: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(new Date(`${day}T12:00:00Z`));
}

/**
 * Wall-clock date/time parts of an instant, as they read in `timeZone` — the building
 * block for both directions of the `<input type="datetime-local">` conversion below.
 * `hourCycle: "h23"` avoids the AM/PM parts `formatTime` uses elsewhere: a
 * `datetime-local` value's `HH` is always 24-hour.
 */
function zonedParts(iso: string, timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso));
  const byType = Object.fromEntries(parts.map((p) => [p.type, p.value]));
  return {
    year: Number(byType.year),
    month: Number(byType.month),
    day: Number(byType.day),
    hour: Number(byType.hour),
    minute: Number(byType.minute),
  };
}

/**
 * Converts an ISO instant to the value a `<input type="datetime-local">` expects,
 * rendered as wall-clock time in `timeZone` (defaults to `TZ`, America/Toronto).
 * A `datetime-local` input carries no timezone of its own — it is exactly a
 * "YYYY-MM-DDTHH:mm" local wall-clock string — so prefilling one from an ISO instant
 * requires rendering that instant in a specific zone first. Used to prefill the admin
 * reschedule dialog with a visit's current `scheduledFor` so opening the picker starts
 * at the visit's own time rather than blank/today (the founder's reported bug).
 */
export function toDatetimeLocalValue(iso: string, timeZone: string = TZ): string {
  const { year, month, day, hour, minute } = zonedParts(iso, timeZone);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}`;
}

/**
 * Inverse of `toDatetimeLocalValue`: converts a `<input type="datetime-local">` value
 * ("YYYY-MM-DDTHH:mm"), interpreted as wall-clock time in `timeZone` (defaults to `TZ`,
 * America/Toronto), to an ISO instant (UTC).
 *
 * Deliberately does NOT do `new Date(value).toISOString()` — the runtime parses a
 * timezone-less `datetime-local` string as the SYSTEM clock's local timezone, not
 * America/Toronto. That's only correct by coincidence when the admin's device happens
 * to be set to Toronto time; anywhere else it silently shifts the submitted instant by
 * the difference between the two zones' UTC offsets (the exact bug class the founder
 * flagged: "getting this wrong shifts every visit by the UTC offset").
 *
 * Standard two-pass technique: treat the wall-clock value as if it were already UTC to
 * get a first guess, render that guess back in `timeZone` to read off the zone's actual
 * offset at that moment (correct across the DST boundary), then apply the offset once.
 */
export function fromDatetimeLocalValue(value: string, timeZone: string = TZ): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) {
    throw new Error(`Invalid datetime-local value: ${value}`);
  }
  const [, y, mo, d, h, mi] = match.map(Number);
  const guessUtcMs = Date.UTC(y, mo - 1, d, h, mi);
  const shown = zonedParts(new Date(guessUtcMs).toISOString(), timeZone);
  const shownAsUtcMs = Date.UTC(shown.year, shown.month - 1, shown.day, shown.hour, shown.minute);
  const offsetMs = guessUtcMs - shownAsUtcMs;
  return new Date(guessUtcMs + offsetMs).toISOString();
}

export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffMs = now.getTime() - then.getTime();
  const minutes = Math.round(diffMs / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return then.toLocaleDateString("en-CA", { timeZone: TZ, month: "short", day: "numeric" });
}
