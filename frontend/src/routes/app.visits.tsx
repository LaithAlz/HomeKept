import { createFileRoute, Link } from "@tanstack/react-router";
import { CheckCircle2, Loader2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatVisitWindow, getCalendarParts } from "@/lib/format";
import { useNextVisit, useRecentCompletedVisits, type AppVisitListItem } from "@/lib/visits";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/visits")({
  head: () => ({
    meta: [{ title: "Visits — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: VisitsPage,
});

function VisitsPage() {
  const nextVisitQuery = useNextVisit();
  const pastVisitsQuery = useRecentCompletedVisits(50);
  useSessionExpiredRedirect(nextVisitQuery.error);
  useSessionExpiredRedirect(pastVisitsQuery.error);

  const nextVisit = nextVisitQuery.data?.[0] ?? null;
  const pastVisits = pastVisitsQuery.data ?? [];

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Visits</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Every past and upcoming visit on your home, with photo reports and notes.
      </p>

      <section className="mt-8" aria-labelledby="up-next-heading">
        <h2 id="up-next-heading" className="text-xs uppercase tracking-wide text-muted-foreground">
          Up next
        </h2>
        <div className="mt-3 rounded-3xl border border-border bg-card p-6">
          {nextVisitQuery.isLoading ? (
            <div
              className="flex items-center gap-3 text-sm text-muted-foreground"
              role="status"
              aria-live="polite"
            >
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading your next visit.
            </div>
          ) : nextVisitQuery.isError ? (
            <p className="text-sm text-muted-foreground">
              We couldn't load your next visit. Try refreshing the page.
            </p>
          ) : !nextVisit ? (
            <p className="text-sm text-muted-foreground">
              No visits scheduled yet. We'll be in touch shortly to confirm your first visit.
            </p>
          ) : (
            <UpNextRow visit={nextVisit} />
          )}
        </div>
      </section>

      <section className="mt-10" aria-labelledby="past-visits-heading">
        <h2
          id="past-visits-heading"
          className="text-xs uppercase tracking-wide text-muted-foreground"
        >
          Past visits
        </h2>
        <div className="mt-3 space-y-3">
          {pastVisitsQuery.isLoading ? (
            <div
              className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
              role="status"
              aria-live="polite"
            >
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading your past visits.
            </div>
          ) : pastVisitsQuery.isError ? (
            <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
              We couldn't load your past visits. Try refreshing the page.
            </p>
          ) : pastVisits.length === 0 ? (
            <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
              No completed visits yet. Your visit history will show up here after your first visit.
            </p>
          ) : (
            pastVisits.map((v) => <PastVisitCard key={v.id} visit={v} />)
          )}
        </div>
      </section>
    </div>
  );
}

function UpNextRow({ visit }: { visit: AppVisitListItem }) {
  const { weekday } = getCalendarParts(visit.scheduledFor);
  const window = formatVisitWindow(visit.scheduledFor, visit.durationMinutes);

  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="flex items-start gap-4">
        <DateBlock scheduledFor={visit.scheduledFor} />
        <div>
          <div className="font-display text-xl font-bold">
            {weekday} · {window}
          </div>
          {visit.technicianFirstName && (
            <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
              <User className="h-4 w-4" aria-hidden="true" /> with {visit.technicianFirstName}
            </div>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {visit.services.map((s) => (
              <span
                key={s.id}
                className="rounded-full border border-border bg-background px-2.5 py-0.5 text-xs"
              >
                {s.serviceName}
              </span>
            ))}
          </div>
        </div>
      </div>
      <div className="flex gap-2">
        <Link to="/app/visits/$id" params={{ id: String(visit.id) }}>
          <Button size="sm" variant="outline">
            View details
          </Button>
        </Link>
        <Button size="sm">Add request</Button>
      </div>
    </div>
  );
}

function PastVisitCard({ visit }: { visit: AppVisitListItem }) {
  return (
    <div className="rounded-3xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-start gap-4">
        <DateBlock scheduledFor={visit.scheduledFor} muted />
        <div className="flex-1">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />{" "}
            {visit.technicianFirstName ? `Completed by ${visit.technicianFirstName}` : "Completed"}
          </div>
          <div className="mt-1 font-medium">{visit.name}</div>
          <div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
            {visit.services.map((s) => (
              <span
                key={s.id}
                className="rounded-full border border-border bg-background px-2 py-0.5"
              >
                {s.serviceName}
              </span>
            ))}
          </div>
        </div>
        <Link to="/app/visits/$id" params={{ id: String(visit.id) }}>
          <Button size="sm" variant="outline">
            Open report
          </Button>
        </Link>
      </div>
    </div>
  );
}

function DateBlock({ scheduledFor, muted }: { scheduledFor: string; muted?: boolean }) {
  const { month, day, weekday } = getCalendarParts(scheduledFor);

  return (
    <div
      className={cn(
        "flex h-16 w-16 flex-col items-center justify-center rounded-2xl border text-center",
        muted
          ? "border-border bg-background text-muted-foreground"
          : "border-primary/30 bg-primary/10 text-primary",
      )}
      aria-label={`${weekday}, ${month} ${day}`}
    >
      <div className="text-[10px] font-semibold uppercase tracking-wide">{month}</div>
      <div className="font-display text-2xl font-extrabold leading-none">{day}</div>
    </div>
  );
}
