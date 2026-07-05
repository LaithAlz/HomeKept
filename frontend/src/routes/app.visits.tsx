import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, BellRing, Loader2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { VisitDateBlock, VisitStatusBadge } from "@/components/app/visit-status";
import { formatVisitWindow, getCalendarParts } from "@/lib/format";
import { useNextVisit, useRecentCompletedVisits, type AppVisitListItem } from "@/lib/visits";
import { useSessionExpiredRedirect } from "@/lib/auth";

export const Route = createFileRoute("/app/visits")({
  head: () => ({
    meta: [{ title: "Visits: HomeKept" }, { name: "robots", content: "noindex" }],
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
        Every past and upcoming visit on your home, with the full checklist and technician notes.
      </p>

      {/* Up next */}
      <section className="mt-8" aria-labelledby="up-next-heading">
        <h2
          id="up-next-heading"
          className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
        >
          Up next
        </h2>
        <div className="mt-3">
          {nextVisitQuery.isLoading ? (
            <StatusPanel>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading your next visit.
            </StatusPanel>
          ) : nextVisitQuery.isError ? (
            <StatusPanel>We couldn't load your next visit. Try refreshing the page.</StatusPanel>
          ) : !nextVisit ? (
            <StatusPanel>
              No visits scheduled yet. We'll be in touch shortly to confirm your first visit.
            </StatusPanel>
          ) : (
            <NextVisitHero visit={nextVisit} />
          )}
        </div>
      </section>

      {/* Past visits */}
      <section className="mt-10" aria-labelledby="past-visits-heading">
        <h2
          id="past-visits-heading"
          className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
        >
          Past visits
        </h2>
        <div className="mt-3">
          {pastVisitsQuery.isLoading ? (
            <StatusPanel>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading your past visits.
            </StatusPanel>
          ) : pastVisitsQuery.isError ? (
            <StatusPanel>We couldn't load your past visits. Try refreshing the page.</StatusPanel>
          ) : pastVisits.length === 0 ? (
            <StatusPanel>
              No completed visits yet. Your visit history will show up here after your first visit.
            </StatusPanel>
          ) : (
            <ul className="space-y-3" role="list" aria-label="Past visits">
              {pastVisits.map((v) => (
                <li key={v.id}>
                  <PastVisitCard visit={v} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Loading / error / empty
// ---------------------------------------------------------------------------

function StatusPanel({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
      role="status"
      aria-live="polite"
    >
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Up next: immersive emphasis panel
// ---------------------------------------------------------------------------

function NextVisitHero({ visit }: { visit: AppVisitListItem }) {
  const { month, day, weekday } = getCalendarParts(visit.scheduledFor);
  const window = formatVisitWindow(visit.scheduledFor, visit.durationMinutes);

  return (
    <article
      aria-label="Next visit"
      className="overflow-hidden rounded-3xl bg-primary text-primary-foreground shadow-sm"
    >
      <div className="flex items-center gap-2 border-b border-primary-foreground/10 px-6 py-3">
        <BellRing className="size-4 text-primary-foreground/80" aria-hidden="true" />
        <span className="text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground/80">
          Next visit
        </span>
      </div>

      <div className="grid gap-6 p-6 md:grid-cols-[auto_1fr] md:items-center md:gap-8 md:p-8">
        <div
          className="flex w-full max-w-[140px] flex-col overflow-hidden rounded-2xl border border-primary-foreground/15 text-center"
          aria-hidden="true"
        >
          <div className="bg-accent py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-accent-foreground">
            {month}
          </div>
          <div className="bg-primary-foreground/5 py-3">
            <div className="font-display text-5xl font-extrabold leading-none tracking-tight tabular-nums">
              {day}
            </div>
            <div className="mt-1 text-xs font-medium uppercase tracking-wider text-primary-foreground/70">
              {weekday}
            </div>
          </div>
        </div>

        <div className="min-w-0">
          <h3 className="font-display text-xl font-bold tracking-tight tabular-nums md:text-2xl">
            {visit.technicianFirstName ? `${window} with ${visit.technicianFirstName}` : window}
          </h3>
          <p className="mt-1 text-sm text-primary-foreground/75">
            {visit.services.length} {visit.services.length === 1 ? "service" : "services"}{" "}
            scheduled. You'll get a report afterward.
          </p>

          {visit.services.length > 0 && (
            <ul className="mt-4 flex flex-wrap gap-2" role="list" aria-label="Scheduled services">
              {visit.services.map((s) => (
                <li
                  key={s.id}
                  className="rounded-full bg-primary-foreground/10 px-3 py-1 text-xs font-medium"
                >
                  {s.serviceName}
                </li>
              ))}
            </ul>
          )}

          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild size="sm" variant="accent">
              <Link to="/app/visits/$id" params={{ id: String(visit.id) }}>
                View details
              </Link>
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="border-primary-foreground/40 bg-transparent text-primary-foreground hover:bg-primary-foreground hover:text-primary"
            >
              Add request
            </Button>
          </div>
        </div>
      </div>
    </article>
  );
}

// ---------------------------------------------------------------------------
// Past visits: scannable card list
// ---------------------------------------------------------------------------

function PastVisitCard({ visit }: { visit: AppVisitListItem }) {
  return (
    <Link
      to="/app/visits/$id"
      params={{ id: String(visit.id) }}
      className="group flex items-start gap-4 rounded-3xl border border-border bg-card p-5 transition hover:border-foreground/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <VisitDateBlock scheduledFor={visit.scheduledFor} muted />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <VisitStatusBadge status={visit.status} />
          {visit.technicianFirstName && (
            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
              <User className="size-3" aria-hidden="true" />
              {visit.technicianFirstName}
            </span>
          )}
        </div>
        <p className="mt-2 font-display text-base font-bold text-foreground">{visit.name}</p>
        {visit.services.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {visit.services.map((s) => (
              <span
                key={s.id}
                className="rounded-full bg-surface px-2.5 py-0.5 text-xs text-muted-foreground"
              >
                {s.serviceName}
              </span>
            ))}
          </div>
        )}
      </div>
      <ArrowRight
        className="mt-1.5 size-4 shrink-0 self-center text-muted-foreground/60 transition group-hover:translate-x-0.5 group-hover:text-accent"
        aria-hidden="true"
      />
    </Link>
  );
}
