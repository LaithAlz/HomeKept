/**
 * MonthLoadCalendar — the admin Routes page's month calendar.
 *
 * Its only job, per the ops research this was built from (Jobber / Housecall Pro /
 * ServiceTitan): pick a day, and see how much work is on it. It shows **honest counts
 * only** — total SCHEDULED visits and how many are unassigned — and never a capacity
 * percentage or "slots free" figure, because the backend does not model technician
 * working hours and a fabricated availability signal is worse than none.
 *
 * A day's load renders as a compact, non-textual signal, never a sentence: a small
 * numeral badge (the total) and, only when at least one visit is unassigned, a
 * warning-triangle icon beside it. That pairing (a shape plus a number) is deliberate —
 * it must never be colour alone that tells an admin a day has unassigned work — and,
 * because it's a fixed-size badge rather than wrapped text, it can't overflow the cell
 * at any grid width. The full sentence ("3 visits, 1 unassigned") still lives in the
 * cell's `aria-label` for screen readers.
 *
 * All day values are plain "YYYY-MM-DD" calendar-date strings (a `dayKey`, see
 * `@/lib/format`), not real timestamps — grid arithmetic below is done on a UTC-noon
 * anchor purely to avoid DST edge cases in date math, not to represent a timezone.
 * "Today" is located internally via `dayKey(new Date())` (America/Toronto, per
 * `@/lib/format`) — the one place a real timezone matters.
 *
 * Accessibility: implements the WAI-ARIA Authoring Practices grid pattern (as used by
 * date-picker grids) with a `role="grid"` of `role="row"`/`role="columnheader"`/
 * `role="gridcell"` divs and **roving tabindex** — only the one focused day is
 * `tabIndex=0`, every other cell is `tabIndex=-1`. Keyboard model:
 *   - Arrow keys move by day/week (Left/Right by day, Up/Down by week).
 *   - Home/End move to the start/end of the day's week.
 *   - PageUp/PageDown move by month, keeping the day-of-month (clamped to the
 *     target month's length).
 *   - Enter/Space select the focused day.
 *   - Crossing a month boundary calls `onMonthChange`, then moves DOM focus onto the
 *     target day once it renders in the new grid.
 * No popup, no focus trap — this is a plain, always-visible grid.
 */
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { AlertTriangle, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { dayKey as torontoDayKey } from "@/lib/format";

export interface DayLoad {
  total: number;
  unassigned: number;
}

export interface MonthLoadCalendarProps {
  /** The visible month — a `Date` (any day within it) or a canonical "YYYY-MM" string. */
  month: Date | string;
  /** The currently chosen day, "YYYY-MM-DD". */
  selectedDay: string;
  /** SCHEDULED-visit load by day ("YYYY-MM-DD"), from `GET /api/admin/visits/day-load`. */
  load: Map<string, DayLoad>;
  onSelectDay: (day: string) => void;
  onMonthChange: (month: string) => void;
  /**
   * Forward-compat hook: optional content rendered below a day's load line (nothing
   * unless a caller supplies this). A later phase assigns each town a fixed weekday and
   * will use this to tag the matching days — built in now so that phase doesn't need to
   * rebuild this component.
   */
  dayTag?: (day: string) => ReactNode;
  /**
   * Forward-compat hook: optional second line under a weekday column header (e.g. the
   * town assigned to that weekday, once that exists). `weekdayIndex` is 0 (Sunday)
   * through 6 (Saturday).
   */
  weekdaySubtitle?: (weekdayIndex: number) => ReactNode;
  className?: string;
}

const WEEKDAYS: { abbr: string; full: string }[] = [
  { abbr: "Sun", full: "Sunday" },
  { abbr: "Mon", full: "Monday" },
  { abbr: "Tue", full: "Tuesday" },
  { abbr: "Wed", full: "Wednesday" },
  { abbr: "Thu", full: "Thursday" },
  { abbr: "Fri", full: "Friday" },
  { abbr: "Sat", full: "Saturday" },
];

// ── Pure calendar-date helpers ────────────────────────────────────────────────
// All operate on "YYYY-MM-DD"/"YYYY-MM" strings via a UTC-noon anchor Date, so month/day
// arithmetic never trips over DST — these are calendar values, not real instants.

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/** Parses a "YYYY-MM" string into numeric parts (month 1–12). */
function parseMonthKey(monthKey: string): { year: number; month: number } {
  const [y, m] = monthKey.split("-").map(Number);
  return { year: y, month: m };
}

/** Parses a "YYYY-MM-DD" string into numeric parts (month 1–12). */
function parseDayKey(day: string): { year: number; month: number; day: number } {
  const [y, m, d] = day.split("-").map(Number);
  return { year: y, month: m, day: d };
}

/** UTC-noon anchor for a calendar date — noon avoids any DST-adjacent rounding. */
function utcNoon(year: number, month1to12: number, day: number): Date {
  return new Date(Date.UTC(year, month1to12 - 1, day, 12));
}

function formatDayKeyUTC(date: Date): string {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
}

function daysInMonth(year: number, month1to12: number): number {
  return new Date(Date.UTC(year, month1to12, 0)).getUTCDate();
}

/** Normalizes the `month` prop (a `Date` or a "YYYY-MM" string) to a canonical "YYYY-MM". */
export function toMonthKey(month: Date | string): string {
  if (typeof month === "string") return month;
  return `${month.getFullYear()}-${pad2(month.getMonth() + 1)}`;
}

function monthKeyOfDay(day: string): string {
  return day.slice(0, 7);
}

function shiftMonthKey(monthKey: string, delta: number): string {
  const { year, month } = parseMonthKey(monthKey);
  const total = year * 12 + (month - 1) + delta;
  const y = Math.floor(total / 12);
  const m = total - y * 12 + 1;
  return `${y}-${pad2(m)}`;
}

/** Adds whole days to a "YYYY-MM-DD" calendar-date string, exported for the day-view arrows. */
export function addDaysToKey(day: string, delta: number): string {
  const { year, month, day: d } = parseDayKey(day);
  return formatDayKeyUTC(new Date(utcNoon(year, month, d).getTime() + delta * 86_400_000));
}

/** Adds whole months, clamping the day-of-month to the target month's length. */
function addMonthsToKey(day: string, deltaMonths: number): string {
  const { year, month, day: d } = parseDayKey(day);
  const total = year * 12 + (month - 1) + deltaMonths;
  const targetYear = Math.floor(total / 12);
  const targetMonth = total - targetYear * 12 + 1;
  const clampedDay = Math.min(d, daysInMonth(targetYear, targetMonth));
  return `${targetYear}-${pad2(targetMonth)}-${pad2(clampedDay)}`;
}

function weekdayIndexOf(day: string): number {
  const { year, month, day: d } = parseDayKey(day);
  return utcNoon(year, month, d).getUTCDay();
}

/**
 * Every day shown in the month grid, in order — full Sunday-start weeks, so the grid
 * always includes the leading days from the previous month and trailing days from the
 * next month needed to fill out the first/last week.
 */
export function getMonthGridDays(month: Date | string): string[] {
  const monthKey = toMonthKey(month);
  const { year, month: m } = parseMonthKey(monthKey);
  const firstOfMonth = utcNoon(year, m, 1);
  const gridStartKey = addDaysToKey(formatDayKeyUTC(firstOfMonth), -firstOfMonth.getUTCDay());

  const lastOfMonth = utcNoon(year, m, daysInMonth(year, m));
  const gridEndKey = addDaysToKey(formatDayKeyUTC(lastOfMonth), 6 - lastOfMonth.getUTCDay());

  const days: string[] = [];
  for (let day = gridStartKey; ; day = addDaysToKey(day, 1)) {
    days.push(day);
    if (day === gridEndKey) break;
  }
  return days;
}

/**
 * The inclusive `["from", "to"]` date range covering everything the grid for `month`
 * shows (including the leading/trailing days from adjacent months) — feed this straight
 * to `useAdminVisitDayLoad` so those days' load loads too, not just the current month's.
 */
export function getMonthGridRange(month: Date | string): { from: string; to: string } {
  const days = getMonthGridDays(month);
  return { from: days[0], to: days[days.length - 1] };
}

function monthLabel(monthKey: string): string {
  const { year, month } = parseMonthKey(monthKey);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "long",
    year: "numeric",
  }).format(utcNoon(year, month, 1));
}

function fullDayLabel(day: string): string {
  const { year, month, day: d } = parseDayKey(day);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(utcNoon(year, month, d));
}

/** "3 visits, 1 unassigned" — comma-joined for the accessible name, same omission rule. */
function formatLoadForName(load: DayLoad): string {
  const visitsPart = `${load.total} visit${load.total === 1 ? "" : "s"}`;
  if (load.unassigned <= 0) return visitsPart;
  return `${visitsPart}, ${load.unassigned} unassigned`;
}

export function MonthLoadCalendar({
  month,
  selectedDay,
  load,
  onSelectDay,
  onMonthChange,
  dayTag,
  weekdaySubtitle,
  className,
}: MonthLoadCalendarProps) {
  const monthKey = toMonthKey(month);
  const grid = getMonthGridDays(monthKey);
  const gridSet = new Set(grid);
  const today = torontoDayKey(new Date());

  const headingId = useId();

  // Roving tabindex: exactly one day cell is tabbable at a time.
  const [focusedDay, setFocusedDay] = useState<string>(
    gridSet.has(selectedDay)
      ? selectedDay
      : (grid.find((d) => monthKeyOfDay(d) === monthKey) ?? grid[0]),
  );
  const dayRefs = useRef(new Map<string, HTMLButtonElement>());
  // Set by keyboard navigation / selection when the target day isn't in the currently
  // rendered grid yet (a month change is in flight) — the DOM-focus effect below moves
  // focus there once its cell exists.
  const pendingFocusRef = useRef<string | null>(null);

  // Keeps the roving cell in sync with external changes (a new `selectedDay` from the
  // "Jump to date" input, the day-view arrows, or a plain prev/next-month click) without
  // clobbering an in-flight keyboard-navigation target.
  useEffect(() => {
    if (pendingFocusRef.current) {
      setFocusedDay(pendingFocusRef.current);
      return;
    }
    if (gridSet.has(selectedDay)) {
      setFocusedDay(selectedDay);
    } else if (gridSet.has(today)) {
      setFocusedDay(today);
    } else {
      setFocusedDay(grid.find((d) => monthKeyOfDay(d) === monthKey) ?? grid[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-syncs on month/selection change only
  }, [monthKey, selectedDay]);

  // Moves real DOM focus onto a pending target once its cell exists in the grid.
  useEffect(() => {
    const target = pendingFocusRef.current;
    if (!target) return;
    const el = dayRefs.current.get(target);
    if (el) {
      el.focus();
      pendingFocusRef.current = null;
    }
  });

  function navigateTo(target: string) {
    const targetMonth = monthKeyOfDay(target);
    pendingFocusRef.current = target;
    setFocusedDay(target);
    if (targetMonth !== monthKey) {
      onMonthChange(targetMonth);
    }
  }

  function selectDay(day: string) {
    pendingFocusRef.current = day;
    setFocusedDay(day);
    onSelectDay(day);
    const dayMonth = monthKeyOfDay(day);
    if (dayMonth !== monthKey) {
      onMonthChange(dayMonth);
    }
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, day: string) {
    switch (event.key) {
      case "ArrowLeft":
        event.preventDefault();
        navigateTo(addDaysToKey(day, -1));
        break;
      case "ArrowRight":
        event.preventDefault();
        navigateTo(addDaysToKey(day, 1));
        break;
      case "ArrowUp":
        event.preventDefault();
        navigateTo(addDaysToKey(day, -7));
        break;
      case "ArrowDown":
        event.preventDefault();
        navigateTo(addDaysToKey(day, 7));
        break;
      case "Home":
        event.preventDefault();
        navigateTo(addDaysToKey(day, -weekdayIndexOf(day)));
        break;
      case "End":
        event.preventDefault();
        navigateTo(addDaysToKey(day, 6 - weekdayIndexOf(day)));
        break;
      case "PageUp":
        event.preventDefault();
        navigateTo(addMonthsToKey(day, -1));
        break;
      case "PageDown":
        event.preventDefault();
        navigateTo(addMonthsToKey(day, 1));
        break;
      case "Enter":
      case " ":
        event.preventDefault();
        selectDay(day);
        break;
      default:
        break;
    }
  }

  // A dispatcher pressing Today means "show me today's work", so this selects the day
  // as well as moving the view and the roving focus. `navigateTo` already calls
  // `onMonthChange` when today isn't in the currently displayed month.
  function goToToday() {
    navigateTo(today);
    onSelectDay(today);
  }

  return (
    <div className={cn("rounded-2xl border border-border bg-card p-4", className)}>
      <div className="flex items-center justify-between gap-2">
        <h2 id={headingId} className="font-display text-sm font-bold">
          {monthLabel(monthKey)}
        </h2>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-8 w-8"
            aria-label="Previous month"
            onClick={() => onMonthChange(shiftMonthKey(monthKey, -1))}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="h-8 px-3 text-xs"
            onClick={goToToday}
          >
            Today
          </Button>
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-8 w-8"
            aria-label="Next month"
            onClick={() => onMonthChange(shiftMonthKey(monthKey, 1))}
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      <div role="grid" aria-labelledby={headingId} className="mt-3">
        <div role="row" className="grid grid-cols-7 gap-1">
          {WEEKDAYS.map((wd, i) => {
            const subtitle = weekdaySubtitle?.(i);
            return (
              <div
                key={wd.full}
                role="columnheader"
                className="flex flex-col items-center pb-1 text-center text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
              >
                <span className="sr-only">{wd.full}</span>
                <span aria-hidden="true">{wd.abbr}</span>
                {subtitle && (
                  <span className="mt-0.5 text-[10px] font-normal normal-case text-muted-foreground/80">
                    {subtitle}
                  </span>
                )}
              </div>
            );
          })}
        </div>

        {Array.from({ length: grid.length / 7 }).map((_, weekIndex) => (
          <div role="row" key={weekIndex} className="grid grid-cols-7 gap-1">
            {grid.slice(weekIndex * 7, weekIndex * 7 + 7).map((day) => {
              const isCurrentMonth = monthKeyOfDay(day) === monthKey;
              const isToday = day === today;
              const isSelected = day === selectedDay;
              const dayLoad = load.get(day);
              const dayNum = parseDayKey(day).day;
              const tag = dayTag?.(day);

              const accessibleName = dayLoad
                ? `${fullDayLabel(day)}, ${formatLoadForName(dayLoad)}`
                : fullDayLabel(day);

              return (
                <button
                  key={day}
                  ref={(el) => {
                    if (el) dayRefs.current.set(day, el);
                    else dayRefs.current.delete(day);
                  }}
                  type="button"
                  role="gridcell"
                  tabIndex={day === focusedDay ? 0 : -1}
                  aria-current={isToday ? "date" : undefined}
                  aria-selected={isSelected}
                  aria-label={accessibleName}
                  onClick={() => selectDay(day)}
                  onKeyDown={(e) => handleKeyDown(e, day)}
                  onFocus={() => setFocusedDay(day)}
                  className={cn(
                    "flex min-h-16 flex-col items-center justify-start gap-1 rounded-lg px-1 py-2 text-sm font-medium transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background",
                    !isCurrentMonth && "text-muted-foreground/50",
                    isToday && !isSelected && "ring-1 ring-inset ring-primary/60",
                    isSelected && "bg-primary text-primary-foreground hover:bg-primary/90",
                  )}
                >
                  <span aria-hidden="true" className={cn(isToday && "font-bold")}>
                    {dayNum}
                  </span>
                  {/* Fixed-height slot, reserved whether or not this day has load, so every
                      cell in a row lines up regardless of which days have a badge. */}
                  <span className="flex h-4 items-center justify-center gap-0.5">
                    {dayLoad && (
                      <>
                        <span
                          aria-hidden="true"
                          className={cn(
                            "inline-flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-bold leading-none tabular-nums",
                            isSelected
                              ? "bg-primary-foreground/20 text-primary-foreground"
                              : "bg-foreground/10 text-foreground/70",
                          )}
                        >
                          {dayLoad.total}
                        </span>
                        {dayLoad.unassigned > 0 && (
                          <AlertTriangle
                            aria-hidden="true"
                            className="h-3 w-3 shrink-0 text-warning"
                            strokeWidth={2.5}
                          />
                        )}
                      </>
                    )}
                  </span>
                  {tag && (
                    <span aria-hidden="true" className="text-[10px] leading-tight">
                      {tag}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
