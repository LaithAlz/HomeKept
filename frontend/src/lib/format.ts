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
