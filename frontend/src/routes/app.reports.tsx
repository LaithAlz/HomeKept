import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, CheckCircle2, FileText, Loader2, User } from "lucide-react";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { formatFullDate } from "@/lib/format";
import { useRecentCompletedVisits, type AppVisitListItem } from "@/lib/visits";

export const Route = createFileRoute("/app/reports")({
  head: () => ({
    meta: [{ title: "Reports: HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: ReportsPage,
});

function ReportsPage() {
  // Every report is a completed visit. Its real checklist, notes, and
  // materials live on the visit detail page. This index never fabricates
  // titles, photo counts, or highlights of its own.
  const query = useRecentCompletedVisits(50);
  useSessionExpiredRedirect(query.error);
  const reports = query.data ?? [];

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Reports</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        A report for every completed visit, with the full checklist and technician notes.
      </p>

      <div className="mt-8">
        {query.isLoading ? (
          <div
            className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your reports.
          </div>
        ) : query.isError ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
            We couldn't load your reports. Try refreshing the page.
          </p>
        ) : reports.length === 0 ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
            Your reports will appear here after your first visit.
          </p>
        ) : (
          <ul
            className="grid gap-4 md:grid-cols-2"
            role="list"
            aria-label="Completed visit reports"
          >
            {reports.map((visit) => (
              <li key={visit.id}>
                <ReportCard visit={visit} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function ReportCard({ visit }: { visit: AppVisitListItem }) {
  const serviceCount = visit.services.length;

  return (
    <Link
      to="/app/visits/$id"
      params={{ id: String(visit.id) }}
      className="group flex h-full flex-col rounded-3xl border border-border bg-card p-5 transition hover:border-foreground/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
        >
          <FileText className="size-5" />
        </span>
        <div className="min-w-0">
          <h2 className="font-display text-lg font-bold text-foreground">{visit.name}</h2>
          <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
            <span>{formatFullDate(visit.scheduledFor)}</span>
            {visit.technicianFirstName && (
              <span className="flex items-center gap-1">
                <User className="size-3" aria-hidden="true" />
                {visit.technicianFirstName}
              </span>
            )}
          </div>
        </div>
      </div>

      {serviceCount > 0 && (
        <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
          <CheckCircle2 className="size-3.5 shrink-0 text-emerald-600" aria-hidden="true" />
          {serviceCount} {serviceCount === 1 ? "service" : "services"} completed
        </p>
      )}

      <div className="mt-auto flex items-center gap-1 pt-5 text-sm font-semibold text-foreground/80 group-hover:text-accent">
        View report
        <ArrowRight className="size-3.5" aria-hidden="true" />
      </div>
    </Link>
  );
}
