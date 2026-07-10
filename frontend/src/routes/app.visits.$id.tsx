import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowLeft,
  CalendarCheck,
  CheckCircle2,
  Circle,
  Clock,
  Loader2,
  RefreshCcw,
  User,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { RescheduleDialog } from "@/components/app/reschedule-dialog";
import { VisitDateBlock, VisitStatusBadge } from "@/components/app/visit-status";
import { ApiError } from "@/lib/api";
import { useSessionExpiredRedirect } from "@/lib/auth";
import {
  formatCentsCad,
  formatDateTime,
  formatFullDate,
  formatTime,
  formatVisitWindow,
  getCalendarParts,
} from "@/lib/format";
import { useVisit, type AppVisitDetail } from "@/lib/visits";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/visits/$id")({
  head: () => ({
    meta: [{ title: "Visit details: HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: VisitDetailPage,
});

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

function VisitDetailPage() {
  const { id } = Route.useParams();
  const visitId = Number(id);
  const validId = Number.isFinite(visitId);

  const query = useVisit(visitId);
  useSessionExpiredRedirect(query.error);

  const is404 = query.isError && query.error instanceof ApiError && query.error.status === 404;

  let body: React.ReactNode;
  if (!validId || is404) {
    body = <VisitNotFound />;
  } else if (query.isLoading) {
    body = <VisitLoading />;
  } else if (query.isError) {
    body = <VisitLoadError />;
  } else if (!query.data) {
    body = <VisitLoading />;
  } else if (query.data.status === "SCHEDULED") {
    body = <ScheduledDetail visit={query.data} />;
  } else {
    body = <CompletedDetail visit={query.data} />;
  }

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
            {query.data
              ? query.data.status === "SCHEDULED"
                ? "Upcoming visit"
                : "Visit details"
              : "Visit details"}
          </li>
        </ol>
      </nav>

      {body}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Scheduled visit
// ---------------------------------------------------------------------------

function ScheduledDetail({ visit }: { visit: AppVisitDetail }) {
  const { weekday } = getCalendarParts(visit.scheduledFor);
  const fullDate = formatFullDate(visit.scheduledFor);
  const window = formatVisitWindow(visit.scheduledFor, visit.durationMinutes);

  const [dialogOpen, setDialogOpen] = useState(false);
  // Client-side only: there's no field on the visit yet that tells us a reschedule
  // request is pending (the backend only exposes that to admins), so this resets on
  // reload. Good enough to stop an immediate second submit in the same session.
  const [requested, setRequested] = useState(false);

  return (
    <>
      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-4">
          <VisitDateBlock scheduledFor={visit.scheduledFor} />
          <div>
            <h1 className="font-display text-2xl font-extrabold tracking-tight md:text-3xl">
              {weekday}, {fullDate}
            </h1>
            <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <span className="flex items-center gap-1.5 tabular-nums">
                <Clock className="size-4" aria-hidden="true" />
                {window}
              </span>
              {visit.technicianFirstName && (
                <span className="flex items-center gap-1.5">
                  <User className="size-4" aria-hidden="true" />
                  with {visit.technicianFirstName}
                </span>
              )}
            </div>
            <div className="mt-2">
              <VisitStatusBadge status={visit.status} />
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          {requested ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-3 py-1.5 text-xs font-semibold text-muted-foreground">
              <RefreshCcw className="size-3.5" aria-hidden="true" />
              Reschedule requested, pending confirmation
            </span>
          ) : (
            <Button variant="outline" size="sm" onClick={() => setDialogOpen(true)}>
              <RefreshCcw className="size-4" aria-hidden="true" />
              Reschedule
            </Button>
          )}
        </div>
      </header>

      <RescheduleDialog
        visitId={visit.id}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onRequested={() => setRequested(true)}
      />

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
        {/* Services */}
        <section aria-labelledby="services-heading">
          <h2
            id="services-heading"
            className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
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
                <span>{s.serviceName}</span>
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
              You'll receive a visit report with the checklist and technician notes afterward,
              usually within a few hours.
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
              Standard consumables (filters, batteries) are included in your plan. Any specialty
              parts are at cost with a receipt.
            </li>
          </ul>

          <div className="mt-6 border-t border-border pt-5">
            <p className="text-xs text-muted-foreground">
              Need to move this visit? Use the reschedule link above or contact us. We ask for 24
              hours' notice when possible.
            </p>
          </div>
        </section>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Completed (and other terminal-status) visit
// ---------------------------------------------------------------------------

function CompletedDetail({ visit }: { visit: AppVisitDetail }) {
  const { weekday } = getCalendarParts(visit.scheduledFor);
  const fullDate = formatFullDate(visit.scheduledFor);
  const window = formatVisitWindow(visit.scheduledFor, visit.durationMinutes);
  const hasMaterials = visit.materialsCostCents !== null && visit.materialsCostCents > 0;
  const hasSidebar = Boolean(visit.completionNotes) || hasMaterials;

  return (
    <>
      {/* Header */}
      <header>
        <div className="flex items-start gap-4">
          <VisitDateBlock scheduledFor={visit.scheduledFor} muted />
          <div>
            <h1 className="font-display text-2xl font-extrabold tracking-tight md:text-3xl">
              {weekday}, {fullDate}
            </h1>
            <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <span className="flex items-center gap-1.5 tabular-nums">
                <Clock className="size-4" aria-hidden="true" />
                {window}
              </span>
              {visit.technicianFirstName && (
                <span className="flex items-center gap-1.5">
                  <User className="size-4" aria-hidden="true" />
                  {visit.technicianFirstName}
                </span>
              )}
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <VisitStatusBadge status={visit.status} />
              {visit.completedAt && (
                <span className="text-xs tabular-nums text-muted-foreground">
                  Completed at{" "}
                  <time dateTime={visit.completedAt}>{formatTime(visit.completedAt)}</time>
                </span>
              )}
            </div>
          </div>
        </div>

        <p className="mt-4 max-w-2xl text-muted-foreground">{visit.name}</p>
      </header>

      <div className={cn("mt-8 grid gap-6", hasSidebar && "lg:grid-cols-[1fr_360px]")}>
        {/* Services */}
        <div className="space-y-6">
          <section aria-labelledby="services-heading">
            <h2
              id="services-heading"
              className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
            >
              Services completed
            </h2>
            <ul className="mt-3 space-y-2" role="list">
              {visit.services.map((s) => (
                <li
                  key={s.id}
                  className={cn(
                    "rounded-2xl border bg-card px-4 py-3 text-sm",
                    s.completed ? "border-border" : "border-warning/30 bg-warning/10",
                  )}
                >
                  <div className="flex items-center gap-3">
                    {s.completed ? (
                      <CheckCircle2
                        className="size-4 shrink-0 text-success"
                        aria-label="Completed"
                      />
                    ) : (
                      <Circle className="size-4 shrink-0 text-warning" aria-label="Not completed" />
                    )}
                    <span className={cn("font-medium", !s.completed && "text-muted-foreground")}>
                      {s.serviceName}
                    </span>
                  </div>
                  {s.technicianNotes && (
                    <p className="ml-7 mt-1 text-xs text-muted-foreground">{s.technicianNotes}</p>
                  )}
                </li>
              ))}
            </ul>
          </section>
        </div>

        {/* Sidebar: notes + materials */}
        {hasSidebar && (
          <div className="space-y-4">
            {visit.completionNotes && (
              <section
                aria-labelledby="notes-heading"
                className="rounded-3xl border border-border bg-card p-6"
              >
                <h2 id="notes-heading" className="font-display text-base font-bold text-foreground">
                  Technician notes
                </h2>
                <p className="mt-3 text-sm text-muted-foreground">{visit.completionNotes}</p>
              </section>
            )}

            {hasMaterials && (
              <section
                aria-labelledby="materials-heading"
                className="rounded-3xl border border-border bg-card p-6"
              >
                <h2
                  id="materials-heading"
                  className="font-display text-base font-bold text-foreground"
                >
                  Materials
                </h2>
                <p className="mt-3 font-display text-2xl font-extrabold tabular-nums text-foreground">
                  {formatCentsCad(visit.materialsCostCents ?? 0)}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Specialty parts used during this visit, billed separately from your plan.
                </p>
              </section>
            )}
          </div>
        )}
      </div>

      <VisitPhotos visit={visit} />
    </>
  );
}

// ---------------------------------------------------------------------------
// Photos
// ---------------------------------------------------------------------------

function VisitPhotos({ visit }: { visit: AppVisitDetail }) {
  const { photos } = visit;
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  if (photos.length === 0) {
    // Only a COMPLETED visit is a natural place to expect photos. For any other
    // terminal status (cancelled, rescheduled, incomplete) with none, say nothing
    // rather than imply photos should exist.
    if (visit.status !== "COMPLETED") return null;
    return (
      <section aria-labelledby="photos-heading" className="mt-8">
        <h2
          id="photos-heading"
          className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
        >
          Photos
        </h2>
        <p className="mt-3 text-sm text-muted-foreground">No photos for this visit yet.</p>
      </section>
    );
  }

  const selected = openIndex !== null ? photos[openIndex] : null;

  return (
    <section aria-labelledby="photos-heading" className="mt-8">
      <h2
        id="photos-heading"
        className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
      >
        Photos
      </h2>
      <ul role="list" className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {photos.map((photo, i) => (
          <li key={i}>
            <button
              type="button"
              onClick={() => setOpenIndex(i)}
              aria-label={
                photo.caption
                  ? `Open photo: ${photo.caption}`
                  : `Open visit photo ${i + 1} of ${photos.length}`
              }
              className="group relative block aspect-[4/3] w-full cursor-pointer overflow-hidden rounded-2xl border border-border bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <img
                src={photo.url}
                alt={photo.caption || "Visit photo"}
                loading="lazy"
                className="size-full object-cover transition-transform duration-200 group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
              />
              {photo.caption && (
                <span className="absolute inset-x-0 bottom-0 truncate bg-gradient-to-t from-foreground/70 to-transparent px-2.5 py-2 text-left text-xs font-medium text-background">
                  {photo.caption}
                </span>
              )}
            </button>
          </li>
        ))}
      </ul>

      <Dialog open={selected !== null} onOpenChange={(open) => !open && setOpenIndex(null)}>
        <DialogContent className="max-w-2xl gap-3">
          {selected && (
            <>
              <DialogHeader>
                <DialogTitle className="font-display">
                  {selected.caption || "Visit photo"}
                </DialogTitle>
                <DialogDescription>
                  {selected.takenAt
                    ? `Taken ${formatDateTime(selected.takenAt)}`
                    : "Full-size view of this visit photo."}
                </DialogDescription>
              </DialogHeader>
              <img
                src={selected.url}
                alt={selected.caption || "Visit photo"}
                className="max-h-[70vh] w-full rounded-xl object-contain"
              />
            </>
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Loading / error / not found
// ---------------------------------------------------------------------------

function VisitLoading() {
  return (
    <div
      className="flex items-center justify-center gap-3 py-20 text-sm text-muted-foreground"
      role="status"
      aria-live="polite"
    >
      <Loader2 className="size-5 animate-spin" aria-hidden="true" />
      Loading visit details.
    </div>
  );
}

function VisitLoadError() {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <h1 className="font-display text-2xl font-semibold text-primary">
        We couldn't load this visit.
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">Check your connection and try again.</p>
      <div className="mt-8">
        <Link to="/app/visits">
          <Button>
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to visits
          </Button>
        </Link>
      </div>
    </div>
  );
}

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
