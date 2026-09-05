import { useEffect, useMemo, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import {
  AlertTriangle,
  ArrowUpRight,
  CalendarOff,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  UserRoundX,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import {
  MonthLoadCalendar,
  addDaysToKey,
  getMonthGridRange,
  toMonthKey,
  type DayLoad,
} from "@/components/admin/MonthLoadCalendar";
import { dayKey, formatDayKeyLong, formatVisitWindow } from "@/lib/format";
import {
  useAdminVisits,
  useAdminTechnicians,
  useAdminVisitDayLoad,
  type AdminVisitListItem,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

/**
 * `date` is a plain "YYYY-MM-DD" local (America/Toronto) calendar-date string. An
 * absent or malformed value `.catch()`es to `undefined` rather than throwing —
 * `RoutesPage` below falls back to "today in Toronto" whenever it's `undefined`, so
 * a bad/missing query string never breaks the route, it just resets to today. The
 * outer `.catch({})` is a second, belt-and-suspenders guard against any unparseable
 * search shape at all (e.g. a non-object) — the schema itself must never throw.
 */
const routesSearchSchema = z
  .object({
    date: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/)
      .optional()
      .catch(undefined),
  })
  .catch({});

export const Route = createFileRoute("/admin/routes")({
  validateSearch: zodValidator(routesSearchSchema),
  head: () => ({
    meta: [{ title: "Routes — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: RoutesPage,
});

const TYPE_LABEL: Record<string, string> = {
  ROUTINE: "Routine",
  EXTRA: "Extra",
  WARRANTY: "Warranty",
  WALKTHROUGH: "Walkthrough",
};

/**
 * Groups real SCHEDULED visits (`GET /api/admin/visits?status=SCHEDULED`) for a chosen
 * day by their assigned technician (`GET /api/admin/technicians`). `Visit.technicianId`
 * stores the technician's *user* id (see `VisitRepository`/`AdminPatchVisitRequest` on the
 * backend, both named `technicianUserId`), so the lookup below matches it against each
 * technician's `userId`, not their row `id`.
 *
 * There is no dispatch/route-optimization backend yet (no drive-time estimate, no
 * addresses, no reordering) and no batched "subscriber by id" lookup either (only
 * `GET /api/admin/subscribers/{id}`, one at a time), so a visit card can't show the
 * customer's name or property address without an N+1 fetch per card — instead each
 * card links straight to that subscriber's full record (`/admin/subscribers?id=`,
 * the same deep link the dashboard uses) so a dispatcher is one click from the name,
 * address, and phone. See the component doc comments below for the rest of that gap.
 *
 * Layout: the day itself lives in the URL (`?date=YYYY-MM-DD`, see `routesSearchSchema`
 * above) so it's linkable and survives a reload. The month calendar (`MonthLoadCalendar`)
 * is the *only* control for jumping to an arbitrary date — it shows honest per-day
 * SCHEDULED-visit counts (never a capacity/percentage figure — the backend doesn't model
 * technician working hours) and doubles as the day picker. A separate previous/next-day
 * stepper lives in the day panel's own heading (not the page header) for the narrower job
 * of stepping one day at a time without leaving the day view; there is deliberately no
 * second "jump to date" input, since that duplicated exactly what clicking a day in the
 * calendar already does.
 */
function RoutesPage() {
  const { date } = Route.useSearch();
  const navigate = Route.useNavigate();

  const todayKey = dayKey(new Date());
  const selectedDay = date ?? todayKey;

  // The visible month for the calendar. Independent of `selectedDay` so browsing months
  // (prev/next-month, PageUp/PageDown) doesn't require a day to be selected in that month
  // — but it re-centers on `selectedDay` whenever that changes from outside the calendar
  // itself (the day panel's prev/next-day arrows).
  const [visibleMonth, setVisibleMonth] = useState<string>(() => toMonthKey(selectedDay));
  useEffect(() => {
    setVisibleMonth(toMonthKey(selectedDay));
  }, [selectedDay]);

  function goToDay(day: string) {
    void navigate({ search: (prev) => ({ ...prev, date: day }), replace: true });
  }

  const {
    data: visits,
    isLoading: visitsLoading,
    isError: visitsError,
    refetch: refetchVisits,
  } = useAdminVisits({ status: "SCHEDULED", limit: 100 });
  const {
    data: technicians,
    isLoading: techsLoading,
    isError: techsError,
    refetch: refetchTechs,
  } = useAdminTechnicians();

  const { from, to } = useMemo(() => getMonthGridRange(visibleMonth), [visibleMonth]);
  const { data: dayLoadRows } = useAdminVisitDayLoad({ from, to });
  const dayLoad = useMemo(() => {
    const map = new Map<string, DayLoad>();
    for (const row of dayLoadRows ?? []) {
      map.set(row.day, { total: row.total, unassigned: row.unassigned });
    }
    return map;
  }, [dayLoadRows]);

  const isLoading = visitsLoading || techsLoading;
  const isError = visitsError || techsError;

  const label = formatDayKeyLong(selectedDay);

  const dayVisits = useMemo(() => {
    if (!visits) return [];
    return visits
      .filter((v) => dayKey(new Date(v.scheduledFor)) === selectedDay)
      .sort((a, b) => new Date(a.scheduledFor).getTime() - new Date(b.scheduledFor).getTime());
  }, [visits, selectedDay]);

  const unassignedCount = useMemo(
    () => dayVisits.filter((v) => v.technicianId === null).length,
    [dayVisits],
  );

  const techNameByUserId = useMemo(() => {
    const map = new Map<number, string>();
    for (const t of technicians ?? []) {
      const name = [t.firstName, t.lastName].filter(Boolean).join(" ");
      map.set(t.userId, name || t.email || `Technician #${t.userId}`);
    }
    return map;
  }, [technicians]);

  // Unassigned sorts first — it's the group that needs a dispatcher's attention — then
  // assigned technicians alphabetically by name.
  const groups = useMemo(() => {
    const byTech = new Map<number | null, AdminVisitListItem[]>();
    for (const v of dayVisits) {
      const list = byTech.get(v.technicianId) ?? [];
      list.push(v);
      byTech.set(v.technicianId, list);
    }
    return [...byTech.entries()].sort(([a], [b]) => {
      if (a === null) return -1;
      if (b === null) return 1;
      return (techNameByUserId.get(a) ?? "").localeCompare(techNameByUserId.get(b) ?? "");
    });
  }, [dayVisits, techNameByUserId]);

  const technicianCount = useMemo(
    () => groups.filter(([technicianId]) => technicianId !== null).length,
    [groups],
  );

  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Routes</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Scheduled visits for the day, grouped by technician.
      </p>

      <div className="mt-6 flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="lg:sticky lg:top-6 lg:w-[336px] lg:shrink-0">
          <MonthLoadCalendar
            month={visibleMonth}
            selectedDay={selectedDay}
            load={dayLoad}
            onSelectDay={goToDay}
            onMonthChange={setVisibleMonth}
          />
        </div>

        <div className="min-w-0 flex-1">
          <DayPanelHeader
            label={label}
            onPrevDay={() => goToDay(addDaysToKey(selectedDay, -1))}
            onNextDay={() => goToDay(addDaysToKey(selectedDay, 1))}
            totalVisits={dayVisits.length}
            unassignedCount={unassignedCount}
            technicianCount={technicianCount}
            showSummary={!isLoading && !isError}
          />

          <div className="mt-4">
            {isLoading && (
              <PanelLoading
                label="Loading scheduled visits."
                className="rounded-2xl border border-border bg-card p-4"
              />
            )}

            {isError && !isLoading && (
              <PanelError
                label="We couldn't load the day's visits."
                onRetry={() => {
                  void refetchVisits();
                  void refetchTechs();
                }}
                className="rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
              />
            )}

            {!isLoading && !isError && dayVisits.length === 0 && <EmptyDayState label={label} />}

            {!isLoading && !isError && dayVisits.length > 0 && (
              <>
                {technicians && technicians.length === 0 && <NoTechniciansHint />}

                <div className="flex flex-wrap gap-4">
                  {groups.map(([technicianId, techVisits]) => (
                    <TechnicianDayCard
                      key={technicianId ?? "unassigned"}
                      technicianId={technicianId}
                      technicianName={
                        technicianId !== null
                          ? (techNameByUserId.get(technicianId) ?? `Technician #${technicianId}`)
                          : null
                      }
                      visits={techVisits}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * The day panel's own heading: a previous/next-day stepper framing the selected day
 * (announced via `aria-live` when it changes, for the "the selected day is announced"
 * requirement) plus the day's honest summary — total scheduled visits, how many are
 * unassigned, and how many technicians have work today. This replaces the old
 * "1 visit · 1 unassigned" line that used to be stranded in tiny type under the
 * calendar; it's the one place that count now lives.
 */
function DayPanelHeader({
  label,
  onPrevDay,
  onNextDay,
  totalVisits,
  unassignedCount,
  technicianCount,
  showSummary,
}: {
  label: string;
  onPrevDay: () => void;
  onNextDay: () => void;
  totalVisits: number;
  unassignedCount: number;
  technicianCount: number;
  showSummary: boolean;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border bg-card px-4 py-3">
      <div className="flex items-center gap-1">
        <Button size="icon" variant="ghost" aria-label="Previous day" onClick={onPrevDay}>
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </Button>
        <h2
          aria-live="polite"
          className="min-w-[190px] text-center font-display text-base font-bold sm:min-w-[220px] sm:text-lg"
        >
          {label}
        </h2>
        <Button size="icon" variant="ghost" aria-label="Next day" onClick={onNextDay}>
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>

      {showSummary && (
        <dl className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm">
          <div className="flex items-center gap-1.5">
            <dt className="sr-only">Scheduled visits</dt>
            <dd className="font-bold tabular-nums">{totalVisits}</dd>
            <span className="text-muted-foreground">visit{totalVisits === 1 ? "" : "s"}</span>
          </div>

          <div>
            <dt className="sr-only">Unassigned visits</dt>
            <dd>
              {unassignedCount > 0 ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-warning/15 px-2.5 py-1 text-xs font-bold text-warning">
                  <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
                  {unassignedCount} unassigned
                </span>
              ) : totalVisits > 0 ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-success/10 px-2.5 py-1 text-xs font-bold text-success">
                  <CircleCheck className="h-3.5 w-3.5" aria-hidden="true" />
                  All assigned
                </span>
              ) : null}
            </dd>
          </div>

          {technicianCount > 0 && (
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <Users className="h-3.5 w-3.5" aria-hidden="true" />
              <dt className="sr-only">Technicians working</dt>
              <dd>
                {technicianCount} technician{technicianCount === 1 ? "" : "s"}
              </dd>
            </div>
          )}
        </dl>
      )}
    </div>
  );
}

function EmptyDayState({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-border bg-card px-6 py-16 text-center">
      <CalendarOff className="size-8 text-muted-foreground" aria-hidden="true" />
      <h2 className="mt-4 font-display text-lg font-bold">No scheduled visits</h2>
      <p className="mt-2 max-w-sm text-sm text-muted-foreground">
        Nothing is on the books for {label}. Pick another day from the calendar, or check back once
        visits are scheduled.
      </p>
    </div>
  );
}

function NoTechniciansHint() {
  return (
    <div className="mb-4 flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning/10 px-4 py-3.5">
      <UserRoundX className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden="true" />
      <p className="text-sm text-foreground">
        There are no technicians on the roster yet, so today's visits can't be assigned to anyone.{" "}
        <Link to="/admin/technicians" className="font-semibold underline underline-offset-2">
          Add a technician
        </Link>
        .
      </p>
    </div>
  );
}

/** Sum of `durationMinutes` in hours, rounded to one decimal (matches the tech app's own
 * "X.Xh est." total, `routes/tech.tsx`), so a technician's card shows how much of their
 * day is booked, not just how many stops.
 */
function totalHoursOf(visits: AdminVisitListItem[]): number {
  return Math.round((visits.reduce((sum, v) => sum + v.durationMinutes, 0) / 60) * 10) / 10;
}

/**
 * One technician's (or "Unassigned"'s) scheduled visits for the day. Cards use
 * `flex-1`/`basis`/`max-w` rather than a fixed grid so a single group stretches to use a
 * wide screen instead of leaving it empty, while several groups wrap into a multi-column
 * row instead of a single cramped column.
 */
function TechnicianDayCard({
  technicianId,
  technicianName,
  visits,
}: {
  technicianId: number | null;
  technicianName: string | null;
  visits: AdminVisitListItem[];
}) {
  const isUnassigned = technicianId === null;
  const hours = totalHoursOf(visits);

  return (
    <div
      className={cn(
        "min-w-[300px] max-w-[560px] flex-1 basis-96 rounded-2xl border bg-card p-5",
        isUnassigned ? "border-warning/40 bg-warning/5" : "border-border",
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 font-display text-lg font-bold">
          {isUnassigned && (
            <AlertTriangle className="h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
          )}
          {isUnassigned ? "Unassigned" : technicianName}
        </h3>
        <div className="shrink-0 text-xs tabular-nums text-muted-foreground">
          {visits.length} visit{visits.length === 1 ? "" : "s"} · {hours}h
        </div>
      </div>

      {/* A `grid`, not a stacked list: on a card wide enough to fit two, visits sit
          side by side instead of leaving the extra card width blank. */}
      <ul className="mt-4 grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-2">
        {visits.map((v) => (
          <li key={v.id} className="rounded-xl border border-border bg-background p-3">
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
              <span className="text-sm font-semibold tabular-nums">
                {formatVisitWindow(v.scheduledFor, v.durationMinutes)}
              </span>
              <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                {TYPE_LABEL[v.type] ?? v.type}
              </span>
            </div>
            <div className="mt-1.5 space-y-0.5 text-xs">
              <Link
                to="/admin/subscribers"
                search={{ id: v.subscriberId }}
                className="inline-flex items-center gap-1 font-medium text-primary underline-offset-2 hover:underline"
              >
                Subscriber #{v.subscriberId}
                <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
              </Link>
              <p className="text-muted-foreground">
                Property #{v.propertyId} · Visit #{v.id}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
