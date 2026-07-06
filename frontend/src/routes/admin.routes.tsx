import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatTime } from "@/lib/format";
import { useAdminVisits, useAdminTechnicians, type AdminVisitListItem } from "@/lib/admin";

export const Route = createFileRoute("/admin/routes")({
  head: () => ({
    meta: [{ title: "Routes — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: RoutesPage,
});

const TZ = "America/Toronto";

const TYPE_LABEL: Record<string, string> = {
  ROUTINE: "Routine",
  EXTRA: "Extra",
  WARRANTY: "Warranty",
  WALKTHROUGH: "Walkthrough",
};

/**
 * "2026-07-05" style key in America/Toronto, used to group visits onto the
 * calendar day a viewer in any timezone would call "today" (or the offset
 * day chosen with the arrows) rather than the viewer's own local date.
 */
function dayKey(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

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
 */
function RoutesPage() {
  const [dayOffset, setDayOffset] = useState(0);
  const selectedDate = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + dayOffset);
    return d;
  }, [dayOffset]);

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

  const isLoading = visitsLoading || techsLoading;
  const isError = visitsError || techsError;

  const label = new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(selectedDate);

  const dayVisits = useMemo(() => {
    if (!visits) return [];
    const key = dayKey(selectedDate);
    return visits
      .filter((v) => dayKey(new Date(v.scheduledFor)) === key)
      .sort((a, b) => new Date(a.scheduledFor).getTime() - new Date(b.scheduledFor).getTime());
  }, [visits, selectedDate]);

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
        <div className="flex items-center gap-2">
          <Button
            size="icon"
            variant="outline"
            aria-label="Previous day"
            onClick={() => setDayOffset((n) => n - 1)}
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
            onClick={() => setDayOffset((n) => n + 1)}
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading scheduled visits.
        </div>
      )}

      {isError && !isLoading && (
        <div
          role="alert"
          className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>We couldn't load the day's visits.</span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              void refetchVisits();
              void refetchTechs();
            }}
          >
            Try again
          </Button>
        </div>
      )}

      {!isLoading && !isError && dayVisits.length === 0 && (
        <p className="mt-6 text-sm text-muted-foreground">No scheduled visits for this day.</p>
      )}

      {!isLoading && !isError && dayVisits.length > 0 && (
        <div className="mt-6 grid gap-6 lg:grid-cols-2">
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
  );
}
