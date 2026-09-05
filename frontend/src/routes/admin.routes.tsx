import { useEffect, useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import {
  MonthLoadCalendar,
  addDaysToKey,
  getMonthGridRange,
  toMonthKey,
  type DayLoad,
} from "@/components/admin/MonthLoadCalendar";
import { dayKey, formatDayKeyLong, formatTime } from "@/lib/format";
import {
  useAdminVisits,
  useAdminTechnicians,
  useAdminVisitDayLoad,
  type AdminVisitListItem,
} from "@/lib/admin";

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
 * addresses, no reordering), so none of that appears here — only the real scheduled
 * times, visit ids, and subscriber/property ids the visits endpoint actually returns.
 *
 * The day itself lives in the URL (`?date=YYYY-MM-DD`, see `routesSearchSchema` above) so
 * it's linkable and survives a reload; a month-sidebar calendar (`MonthLoadCalendar`)
 * shows honest per-day SCHEDULED-visit counts (never a capacity/percentage figure — the
 * backend doesn't model technician working hours) and doubles as the day picker, next to
 * the original previous/next-day arrows and a native "Jump to date" input.
 */
function RoutesPage() {
  const { date } = Route.useSearch();
  const navigate = Route.useNavigate();

  const todayKey = dayKey(new Date());
  const selectedDay = date ?? todayKey;

  // The visible month for the sidebar calendar. Independent of `selectedDay` so browsing
  // months (prev/next-month, PageUp/PageDown) doesn't require a day to be selected in
  // that month — but it re-centers on `selectedDay` whenever that changes from outside
  // the calendar itself (the day arrows or the "Jump to date" input).
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

  const techNameByUserId = useMemo(() => {
    const map = new Map<number, string>();
    for (const t of technicians ?? []) {
      const name = [t.firstName, t.lastName].filter(Boolean).join(" ");
      map.set(t.userId, name || t.email || `Technician #${t.userId}`);
    }
    return map;
  }, [technicians]);

  const groups = useMemo(() => {
    const byTech = new Map<number | null, AdminVisitListItem[]>();
    for (const v of dayVisits) {
      const list = byTech.get(v.technicianId) ?? [];
      list.push(v);
      byTech.set(v.technicianId, list);
    }
    return [...byTech.entries()].sort(([a], [b]) => {
      if (a === null) return 1;
      if (b === null) return -1;
      return (techNameByUserId.get(a) ?? "").localeCompare(techNameByUserId.get(b) ?? "");
    });
  }, [dayVisits, techNameByUserId]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Routes</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Scheduled visits for the day, grouped by technician.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            size="icon"
            variant="outline"
            aria-label="Previous day"
            onClick={() => goToDay(addDaysToKey(selectedDay, -1))}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </Button>
          <div className="min-w-[200px] rounded-lg border border-border bg-card px-4 py-2 text-center text-sm font-medium">
            {label}
          </div>
          <Button
            size="icon"
            variant="outline"
            aria-label="Next day"
            onClick={() => goToDay(addDaysToKey(selectedDay, 1))}
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
          <div className="flex items-center gap-2 pl-2">
            <Label htmlFor="routes-jump-to-date" className="text-xs text-muted-foreground">
              Jump to date
            </Label>
            <input
              id="routes-jump-to-date"
              type="date"
              value={selectedDay}
              onChange={(e) => {
                if (e.target.value) goToDay(e.target.value);
              }}
              className="h-9 rounded-lg border border-input bg-card px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
        </div>
      </div>

      <div className="mt-6 flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="lg:w-72 lg:shrink-0">
          <MonthLoadCalendar
            month={visibleMonth}
            selectedDay={selectedDay}
            load={dayLoad}
            onSelectDay={goToDay}
            onMonthChange={setVisibleMonth}
          />
        </div>

        <div className="min-w-0 flex-1">
          {isLoading && <PanelLoading label="Loading scheduled visits." />}

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

          {!isLoading && !isError && dayVisits.length === 0 && (
            <p className="text-sm text-muted-foreground">No scheduled visits for this day.</p>
          )}

          {!isLoading && !isError && dayVisits.length > 0 && (
            <div className="grid gap-6 xl:grid-cols-2">
              {groups.map(([technicianId, techVisits]) => (
                <div
                  key={technicianId ?? "unassigned"}
                  className="rounded-2xl border border-border bg-card p-5"
                >
                  <div className="flex items-center justify-between">
                    <h2 className="font-display text-lg font-bold">
                      {technicianId !== null
                        ? (techNameByUserId.get(technicianId) ?? `Technician #${technicianId}`)
                        : "Unassigned"}
                    </h2>
                    <div className="text-xs text-muted-foreground">
                      {techVisits.length} visit{techVisits.length === 1 ? "" : "s"}
                    </div>
                  </div>
                  <div className="mt-4 space-y-2">
                    {techVisits.map((v) => (
                      <div key={v.id} className="rounded-xl border border-border bg-background p-3">
                        <div className="flex items-center justify-between">
                          <span className="font-medium">Visit #{v.id}</span>
                          <span className="text-xs tabular-nums text-muted-foreground">
                            {formatTime(v.scheduledFor)}
                          </span>
                        </div>
                        <div className="mt-0.5 flex items-center justify-between text-xs text-muted-foreground">
                          <span>
                            Subscriber #{v.subscriberId} · Property #{v.propertyId}
                          </span>
                          <span>{TYPE_LABEL[v.type] ?? v.type}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
