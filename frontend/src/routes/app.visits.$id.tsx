import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import {
  ArrowLeft,
  CalendarCheck,
  CheckCircle2,
  Circle,
  Clock,
  FileText,
  Flag,
  RefreshCcw,
  User,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { findVisitById, type MockVisit } from "@/lib/mock-account";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/visits/$id")({
  head: () => ({
    meta: [{ title: "Visit details — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  loader: ({ params }) => {
    const visit = findVisitById(params.id);
    if (!visit) throw notFound();
    return { visit };
  },
  component: VisitDetailPage,
  notFoundComponent: VisitNotFound,
});

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

function VisitDetailPage() {
  const { visit } = Route.useLoaderData();

  return (
    <div className="px-6 py-10 md:px-10">
      {/* Breadcrumb */}
      <nav aria-label="Breadcrumb" className="mb-6">
        <ol className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <li>
            <Link
              to="/app/visits"
              className="inline-flex items-center gap-1 font-medium text-muted-foreground hover:text-foreground focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <ArrowLeft className="size-3.5" aria-hidden="true" />
              Visits
            </Link>
          </li>
          <li aria-hidden="true" className="select-none">
            /
          </li>
          <li className="text-foreground" aria-current="page">
            {visit.status === "SCHEDULED" ? "Upcoming visit" : "Visit details"}
          </li>
        </ol>
      </nav>

      {visit.status === "SCHEDULED" ? (
        <ScheduledDetail visit={visit} />
      ) : (
        <CompletedDetail visit={visit} />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Scheduled visit
// ---------------------------------------------------------------------------

function ScheduledDetail({ visit }: { visit: MockVisit }) {
  const date = new Date(visit.dateISO);
  const weekday = date.toLocaleDateString("en-CA", { weekday: "long" });
  const fullDate = date.toLocaleDateString("en-CA", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });

  return (
    <>
      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-4">
          <DateBlock date={date} />
          <div>
            <h1 className="font-display text-2xl font-extrabold tracking-tight md:text-3xl">
              {weekday}, {fullDate}
            </h1>
            <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <Clock className="size-4" aria-hidden="true" />
                {visit.window}
              </span>
              <span className="flex items-center gap-1.5">
                <User className="size-4" aria-hidden="true" />
                with {visit.technician}
              </span>
            </div>
            <div className="mt-2">
              <StatusBadge status="SCHEDULED" />
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          <Button variant="outline" size="sm">
            <RefreshCcw className="size-4" aria-hidden="true" />
            Reschedule
          </Button>
        </div>
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
        {/* Services */}
        <section aria-labelledby="services-heading">
          <h2
            id="services-heading"
            className="text-xs font-bold uppercase tracking-wide text-muted-foreground"
          >
            Scheduled services
          </h2>
          <ul className="mt-3 space-y-2" role="list">
            {visit.services.map((s) => (
              <li
                key={s.id}
                className="flex items-center gap-3 rounded-2xl border border-border bg-card px-4 py-3 text-sm"
              >
                <CalendarCheck className="size-4 shrink-0 text-primary/60" aria-hidden="true" />
                <span>{s.label}</span>
              </li>
            ))}
          </ul>
        </section>

        {/* What to expect */}
        <section
          aria-labelledby="expect-heading"
          className="rounded-3xl border border-border bg-card p-6"
        >
          <h2 id="expect-heading" className="font-display text-lg font-bold text-foreground">
            What to expect
          </h2>
          <ul className="mt-4 space-y-3 text-sm text-muted-foreground">
            <li className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
              Your technician will arrive within the scheduled window.
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
              Each service is completed and logged with notes.
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
              You'll receive a photo report after the visit — usually within a few hours.
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
              Standard consumables (filters, batteries) are included in your plan. Any specialty
              parts are at cost with a receipt.
            </li>
          </ul>

          <div className="mt-6 border-t border-border pt-5">
            <p className="text-xs text-muted-foreground">
              Need to move this visit? Use the reschedule link above or contact us — we ask for 24
              hours' notice when possible.
            </p>
          </div>
        </section>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Completed visit
// ---------------------------------------------------------------------------

function CompletedDetail({ visit }: { visit: MockVisit }) {
  const date = new Date(visit.dateISO);
  const completedAt = visit.completedAtISO ? new Date(visit.completedAtISO) : null;
  const weekday = date.toLocaleDateString("en-CA", { weekday: "long" });
  const fullDate = date.toLocaleDateString("en-CA", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });

  const visibleNotes = visit.notes.filter((n) => n.visibleToCustomer);

  return (
    <>
      {/* Header */}
      <header>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <DateBlock date={date} muted />
            <div>
              <h1 className="font-display text-2xl font-extrabold tracking-tight md:text-3xl">
                {weekday}, {fullDate}
              </h1>
              <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <Clock className="size-4" aria-hidden="true" />
                  {visit.window}
                </span>
                <span className="flex items-center gap-1.5">
                  <User className="size-4" aria-hidden="true" />
                  {visit.technician}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <StatusBadge status="COMPLETED" />
                {completedAt && (
                  <span className="text-xs text-muted-foreground">
                    Completed at{" "}
                    <time dateTime={completedAt.toISOString()}>
                      {completedAt.toLocaleTimeString("en-CA", {
                        hour: "numeric",
                        minute: "2-digit",
                        hour12: true,
                      })}
                    </time>
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Report link */}
          {visit.reportId && (
            <Link to="/app/reports">
              <Button variant="outline" size="sm">
                <FileText className="size-4" aria-hidden="true" />
                Open report
              </Button>
            </Link>
          )}
        </div>

        {/* Summary */}
        {visit.summary && <p className="mt-4 max-w-2xl text-muted-foreground">{visit.summary}</p>}
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
        {/* Services */}
        <div className="space-y-6">
          <section aria-labelledby="services-heading">
            <h2
              id="services-heading"
              className="text-xs font-bold uppercase tracking-wide text-muted-foreground"
            >
              Services completed
            </h2>
            <ul className="mt-3 space-y-2" role="list">
              {visit.services.map((s) => (
                <li
                  key={s.id}
                  className={cn(
                    "rounded-2xl border bg-card px-4 py-3 text-sm",
                    s.completed ? "border-border" : "border-amber-300 bg-amber-50/50",
                  )}
                >
                  <div className="flex items-center gap-3">
                    {s.completed ? (
                      <CheckCircle2
                        className="size-4 shrink-0 text-emerald-600"
                        aria-label="Completed"
                      />
                    ) : (
                      <Circle
                        className="size-4 shrink-0 text-muted-foreground"
                        aria-label="Not completed"
                      />
                    )}
                    <span className={cn("font-medium", !s.completed && "text-muted-foreground")}>
                      {s.label}
                    </span>
                  </div>
                  {s.note && <p className="ml-7 mt-1 text-xs text-muted-foreground">{s.note}</p>}
                </li>
              ))}
            </ul>
          </section>

          {/* Flagged item */}
          {visit.flagged && (
            <section aria-labelledby="flagged-heading">
              <h2
                id="flagged-heading"
                className="text-xs font-bold uppercase tracking-wide text-muted-foreground"
              >
                Flagged item
              </h2>
              <div className="mt-3 flex items-start gap-3 rounded-2xl border border-amber-300 bg-amber-50/60 px-4 py-3">
                <Flag className="mt-0.5 size-4 shrink-0 text-amber-700" aria-hidden="true" />
                <p className="text-sm text-amber-900">{visit.flagged}</p>
              </div>
            </section>
          )}
        </div>

        {/* Sidebar: notes + report */}
        <div className="space-y-4">
          {visibleNotes.length > 0 && (
            <section
              aria-labelledby="notes-heading"
              className="rounded-3xl border border-border bg-card p-6"
            >
              <h2 id="notes-heading" className="font-display text-base font-bold text-foreground">
                Technician notes
              </h2>
              <ul className="mt-3 space-y-3" role="list">
                {visibleNotes.map((n) => (
                  <li key={n.id} className="text-sm text-muted-foreground">
                    {n.body}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {visit.reportId && (
            <div className="rounded-3xl border border-border bg-card p-6">
              <h2 className="font-display text-base font-bold text-foreground">Visit report</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Full photo report with all checklist items and technician commentary.
              </p>
              <div className="mt-4">
                <Link to="/app/reports">
                  <Button size="sm" className="w-full">
                    <FileText className="size-4" aria-hidden="true" />
                    Open full report
                  </Button>
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Not found
// ---------------------------------------------------------------------------

function VisitNotFound() {
  return (
    <div className="px-6 py-20 md:px-10">
      <div className="mx-auto max-w-md text-center">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Not found</p>
        <h1 className="mt-4 font-display text-3xl font-semibold text-primary">Visit not found</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          We couldn't find that visit. It may have been removed or the link may be incorrect.
        </p>
        <div className="mt-8">
          <Link to="/app/visits">
            <Button>
              <ArrowLeft className="size-4" aria-hidden="true" />
              Back to visits
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

function DateBlock({ date, muted }: { date: Date; muted?: boolean }) {
  const month = date.toLocaleString("en-CA", { month: "short" });
  const day = date.getDate();
  const weekday = date.toLocaleDateString("en-CA", { weekday: "long" });

  return (
    <div
      className={cn(
        "flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-2xl border text-center",
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

function StatusBadge({ status }: { status: "SCHEDULED" | "COMPLETED" }) {
  if (status === "COMPLETED") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
        <CheckCircle2 className="size-3.5" aria-hidden="true" />
        Completed
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
      <CalendarCheck className="size-3.5" aria-hidden="true" />
      Scheduled
    </span>
  );
}
