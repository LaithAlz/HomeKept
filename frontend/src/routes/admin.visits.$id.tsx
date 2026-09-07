import { useEffect, useMemo, useRef } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, CheckCircle2, Circle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { NotesLog } from "@/components/admin/NotesLog";
import { formatCentsCad, formatDateTime, formatVisitWindow } from "@/lib/format";
import { ApiError } from "@/lib/api";
import {
  useAdminVisit,
  useAdminVisitEvents,
  useAdminVisitNotes,
  useAddAdminVisitNote,
  useAdminTechnicians,
  visitCustomerFullName,
  VISIT_STATUS_LABEL,
  VISIT_STATUS_TONE,
  VISIT_TYPE_LABEL,
  type AdminVisitDetail,
  type AdminVisitEvent,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/visits/$id")({
  head: ({ params }) => ({
    meta: [
      { title: `Visit #${params.id} — HomeKept Admin` },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: VisitDetailPage,
});

// ---------------------------------------------------------------------------
// Page shell: breadcrumb, loading/error/not-found routing, focus management
// ---------------------------------------------------------------------------

/**
 * The visit detail page (founder's ask: "I want each visit to take to a link with
 * more details showing it's been rescheduled (a log) rather than adding another one
 * to the list"). Follows `routes/admin.subscribers.$id.tsx`'s established shape: a
 * layout route (`admin.visits.tsx`) hands off via `<Outlet />` to either the list
 * (`admin.visits.index.tsx`) or this record page; breadcrumb back to the list; the
 * visit's resolved display name as the page heading; focus moved to that heading on
 * navigation; honest loading/error/not-found states.
 *
 * The activity log (`ActivitySection` below) is the point of this page: it replaces
 * the old behaviour where every reschedule inserted a duplicate row into the visit
 * list, breaking the "Visit #N" identity an operator uses to refer to a visit. This
 * page is read-only otherwise (no reschedule/cancel/assign controls) — those stay on
 * the list (`admin.visits.index.tsx`), unchanged.
 */
function VisitDetailPage() {
  const { id } = Route.useParams();
  const visitId = Number(id);
  const validId = Number.isInteger(visitId) && visitId > 0;

  const query = useAdminVisit(validId ? visitId : null);

  // Moves focus to the page heading on every navigation to this route (including
  // switching from one visit to another) — see `admin.subscribers.$id.tsx` for why
  // this single ref/effect pair covers every state below.
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus();
  }, [id]);

  const is404 = query.isError && query.error instanceof ApiError && query.error.status === 404;

  let body: React.ReactNode;
  let crumbLabel: string;
  if (!validId || is404) {
    body = <VisitNotFound headingRef={headingRef} />;
    crumbLabel = "Not found";
  } else if (query.isLoading || !query.data) {
    body = <VisitDetailLoading headingRef={headingRef} />;
    crumbLabel = "Loading…";
  } else if (query.isError) {
    body = <VisitDetailLoadError headingRef={headingRef} onRetry={() => void query.refetch()} />;
    crumbLabel = "Visit detail";
  } else {
    body = <VisitDetailView visit={query.data} headingRef={headingRef} />;
    crumbLabel = query.data.name || `Visit #${query.data.id}`;
  }

  return (
    <div className="px-6 py-8">
      <nav aria-label="Breadcrumb" className="mb-6">
        <ol className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <li>
            <Link
              to="/admin/visits"
              className="inline-flex items-center gap-1 font-medium text-muted-foreground hover:text-foreground focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <ArrowLeft className="size-3.5" aria-hidden="true" />
              Visits
            </Link>
          </li>
          <li aria-hidden="true" className="select-none">
            /
          </li>
          <li className="truncate text-foreground" aria-current="page">
            {crumbLabel}
          </li>
        </ol>
      </nav>

      {body}
    </div>
  );
}

type HeadingRef = React.RefObject<HTMLHeadingElement | null>;

function VisitDetailLoading({ headingRef }: { headingRef: HeadingRef }) {
  return (
    <div className="py-16">
      <h1 ref={headingRef} tabIndex={-1} className="sr-only outline-none">
        Loading visit
      </h1>
      <PanelLoading label="Loading visit." className="justify-center" />
    </div>
  );
}

function VisitDetailLoadError({
  headingRef,
  onRetry,
}: {
  headingRef: HeadingRef;
  onRetry: () => void;
}) {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="rounded-md font-display text-2xl font-semibold text-primary outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        We couldn't load this visit.
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">Check your connection and try again.</p>
      <div className="mt-8 flex flex-wrap justify-center gap-2">
        <Button onClick={onRetry}>Try again</Button>
        <Button variant="outline" asChild>
          <Link to="/admin/visits">
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to visits
          </Link>
        </Button>
      </div>
    </div>
  );
}

function VisitNotFound({ headingRef }: { headingRef: HeadingRef }) {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Not found</p>
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="mt-4 rounded-md font-display text-3xl font-semibold text-primary outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        No such visit
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        We couldn't find that visit. It may have been removed or the link may be incorrect.
      </p>
      <div className="mt-8">
        <Button asChild>
          <Link to="/admin/visits">
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to visits
          </Link>
        </Button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Loaded detail
// ---------------------------------------------------------------------------

function VisitDetailView({
  visit,
  headingRef,
}: {
  visit: AdminVisitDetail;
  headingRef: HeadingRef;
}) {
  const customerName = visitCustomerFullName(visit);
  const technicianName = [visit.technicianFirstName, visit.technicianLastName]
    .filter(Boolean)
    .join(" ")
    .trim();

  return (
    <>
      <header>
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="rounded-md font-display text-2xl font-extrabold tracking-tight outline-none focus-visible:ring-2 focus-visible:ring-ring md:text-3xl"
        >
          {visit.name}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Visit #{visit.id} · Subscriber #{visit.subscriberId}
        </p>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span
            className={cn(
              "rounded-full px-2 py-0.5 text-xs font-medium",
              VISIT_STATUS_TONE[visit.status] ?? "bg-muted text-muted-foreground",
            )}
          >
            {VISIT_STATUS_LABEL[visit.status] ?? visit.status}
          </span>
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-foreground">
            {VISIT_TYPE_LABEL[visit.type] ?? visit.type}
          </span>
        </div>
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="min-w-0 space-y-6">
          <OverviewSection visit={visit} />
          <ServicesSection visit={visit} />
          {(visit.completionNotes || visit.materialsNotes || visit.materialsCostCents != null) && (
            <CompletionSection visit={visit} />
          )}
          <NotesSection visitId={visit.id} />
          <PhotosSection visit={visit} />
          <ActivitySection visitId={visit.id} />
        </div>

        <div className="space-y-6">
          <CustomerSection visit={visit} customerName={customerName} />
          <PropertySection visit={visit} />
          <TechnicianSection visit={visit} technicianName={technicianName} />
        </div>
      </div>
    </>
  );
}

/** One card/section shell shared by every section of the visit detail page. */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
      <h2 className="font-display text-base font-bold">{title}</h2>
      <div className="mt-3">{children}</div>
    </section>
  );
}

/** Compact label/value row, label muted on the left, value right-aligned. */
function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 py-2 text-sm">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 flex-1 break-words text-right font-medium text-foreground">{value}</dd>
    </div>
  );
}

function OverviewSection({ visit }: { visit: AdminVisitDetail }) {
  return (
    <Section title="Overview">
      <dl className="divide-y divide-border">
        <Row label="Scheduled for" value={formatDateTime(visit.scheduledFor)} />
        <Row
          label="Time window"
          value={formatVisitWindow(visit.scheduledFor, visit.durationMinutes)}
        />
        <Row label="Planned duration" value={`${visit.durationMinutes} min`} />
        {visit.actualDurationMinutes != null && (
          <Row label="Actual duration" value={`${visit.actualDurationMinutes} min`} />
        )}
        {visit.completedAt && <Row label="Completed" value={formatDateTime(visit.completedAt)} />}
      </dl>
    </Section>
  );
}

function ServicesSection({ visit }: { visit: AdminVisitDetail }) {
  return (
    <Section title="Services">
      {visit.services.length === 0 ? (
        <p className="text-xs text-muted-foreground">No services attached to this visit.</p>
      ) : (
        <ul className="space-y-2" role="list">
          {visit.services.map((s) => (
            <li
              key={s.id}
              className={cn(
                "flex items-start gap-3 rounded-xl border px-3 py-2 text-sm",
                s.completed ? "border-border" : "border-warning/30 bg-warning/10",
              )}
            >
              {s.completed ? (
                <CheckCircle2
                  className="mt-0.5 size-4 shrink-0 text-success"
                  aria-label="Completed"
                />
              ) : (
                <Circle
                  className="mt-0.5 size-4 shrink-0 text-warning"
                  aria-label="Not completed"
                />
              )}
              <div className="min-w-0">
                <span className={cn("font-medium", !s.completed && "text-muted-foreground")}>
                  {s.serviceName}
                </span>
                {s.technicianNotes && (
                  <p className="mt-0.5 text-xs text-muted-foreground">{s.technicianNotes}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

function CompletionSection({ visit }: { visit: AdminVisitDetail }) {
  return (
    <Section title="Completion">
      <dl className="divide-y divide-border">
        {visit.materialsCostCents != null && (
          <Row label="Materials cost" value={formatCentsCad(visit.materialsCostCents)} />
        )}
      </dl>
      {visit.completionNotes && (
        <div className="mt-3">
          <h3 className="text-xs font-bold uppercase tracking-wide text-muted-foreground">
            Completion notes
          </h3>
          <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">
            {visit.completionNotes}
          </p>
        </div>
      )}
      {visit.materialsNotes && (
        <div className="mt-3">
          <h3 className="text-xs font-bold uppercase tracking-wide text-muted-foreground">
            Materials notes
          </h3>
          <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">{visit.materialsNotes}</p>
        </div>
      )}
    </Section>
  );
}

/**
 * The visit's threaded operational notes log (issue: "each visit should also have
 * notes... technician notes for tracking and whatever") — a running log any number of
 * staff/technicians can add to, each entry keeping its own author and timestamp.
 *
 * Deliberately a separate section from `CompletionSection`'s "Completion notes"/
 * "Materials notes": those are single fields a technician fills in once, at completion.
 * This is a log (see the description text `NotesLog` renders, which spells out that
 * distinction for the reader) and it's always shown, whether or not this visit has been
 * completed yet.
 */
function NotesSection({ visitId }: { visitId: number }) {
  const query = useAdminVisitNotes(visitId);
  const addNote = useAddAdminVisitNote(visitId);

  const notes = useMemo(() => query.data?.pages.flatMap((page) => page.notes) ?? [], [query.data]);

  return (
    <Section title="Notes log">
      <NotesLog
        notes={notes}
        isLoading={query.isLoading}
        isError={query.isError && !query.data}
        onRetry={() => void query.refetch()}
        hasMore={query.hasNextPage}
        onLoadMore={() => void query.fetchNextPage()}
        isLoadingMore={query.isFetchingNextPage}
        loadMoreError={query.isFetchNextPageError}
        onAddNote={(body) => addNote.mutateAsync(body)}
        isAdding={addNote.isPending}
        description="A running log of notes about this visit, from staff and technicians, most recent first. It's separate from the one-time completion notes a technician records when the visit finishes: this log can hold any number of entries, each showing who wrote it and when."
        emptyMessage="No notes yet for this visit."
        logLabel="Visit notes"
        formLabel="Add a note about this visit"
      />
    </Section>
  );
}

function PhotosSection({ visit }: { visit: AdminVisitDetail }) {
  if (visit.photos.length === 0) return null;
  return (
    <Section title="Photos">
      <ul role="list" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {visit.photos.map((photo, i) => (
          <li key={i}>
            <a
              href={photo.url}
              target="_blank"
              rel="noreferrer"
              className="group relative block aspect-[4/3] w-full overflow-hidden rounded-xl border border-border bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <img
                src={photo.url}
                alt={photo.caption || `Visit photo ${i + 1} of ${visit.photos.length}`}
                loading="lazy"
                className="size-full object-cover"
              />
              {photo.caption && (
                <span className="absolute inset-x-0 bottom-0 truncate bg-gradient-to-t from-foreground/70 to-transparent px-2 py-1.5 text-left text-xs font-medium text-background">
                  {photo.caption}
                </span>
              )}
            </a>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function CustomerSection({
  visit,
  customerName,
}: {
  visit: AdminVisitDetail;
  customerName: string;
}) {
  return (
    <Section title="Customer">
      {customerName || visit.customerEmail || visit.customerPhone ? (
        <div className="space-y-1 text-sm">
          {customerName && <p className="font-medium text-foreground">{customerName}</p>}
          {visit.customerEmail && (
            <a
              href={`mailto:${visit.customerEmail}`}
              className="block text-primary underline-offset-2 hover:underline"
            >
              {visit.customerEmail}
            </a>
          )}
          {visit.customerPhone && (
            <a
              href={`tel:${visit.customerPhone}`}
              className="block text-primary underline-offset-2 hover:underline"
            >
              {visit.customerPhone}
            </a>
          )}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">Customer identity unavailable.</p>
      )}
    </Section>
  );
}

function PropertySection({ visit }: { visit: AdminVisitDetail }) {
  const { property } = visit;
  return (
    <Section title="Property">
      <dl className="divide-y divide-border">
        <Row
          label="Address"
          value={
            property.unit
              ? `${property.unit}-${property.streetAddress}, ${property.city} ${property.postalCode}`
              : `${property.streetAddress}, ${property.city} ${property.postalCode}`
          }
        />
      </dl>
    </Section>
  );
}

function TechnicianSection({
  visit,
  technicianName,
}: {
  visit: AdminVisitDetail;
  technicianName: string;
}) {
  return (
    <Section title="Technician">
      {visit.technicianId != null && technicianName ? (
        <p className="text-sm font-medium text-foreground">{technicianName}</p>
      ) : visit.technicianId != null ? (
        <p className="text-sm text-muted-foreground">Technician #{visit.technicianId}</p>
      ) : (
        <p className="text-xs text-muted-foreground">Not yet assigned.</p>
      )}
    </Section>
  );
}

// ---------------------------------------------------------------------------
// Activity log — the point of this page
// ---------------------------------------------------------------------------

/**
 * Who (or what) is credited as having done something, phrased as a sentence subject
 * ("Staff rescheduled…", "The customer rescheduled…"). Falls back to "Someone" for an
 * event source this build doesn't recognize — `source` is typed as a string, not a
 * closed union, on the wire (see `AdminVisitEvent`), so a future value must degrade
 * rather than crash.
 */
const ACTOR_LABEL: Record<string, string> = {
  ADMIN: "Staff",
  CUSTOMER: "The customer",
  TECHNICIAN: "The technician",
  SYSTEM: "The system",
};

function actorFor(source: string): string {
  return ACTOR_LABEL[source] ?? "Someone";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** `{ from: string, to: string }` (ISO instants) — the shape `RESCHEDULED` carries. */
function asRescheduledPayload(payload: unknown): { from: string; to: string } | null {
  if (!isRecord(payload)) return null;
  const { from, to } = payload;
  if (typeof from === "string" && typeof to === "string") return { from, to };
  return null;
}

/** `{ from: number | null, to: number }` (user ids) — the shape `TECHNICIAN_ASSIGNED` carries. */
function asTechnicianAssignedPayload(payload: unknown): { from: number | null; to: number } | null {
  if (!isRecord(payload)) return null;
  const { from, to } = payload;
  if (typeof to !== "number") return null;
  if (from !== null && typeof from !== "number") return null;
  return { from: from === null ? null : from, to };
}

/** Any JSON-safe value, rendered as a short, safe string — never throws. */
function stringifyPayloadValue(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return "(unreadable)";
  }
}

/**
 * One event, rendered as a sentence a human reads rather than dumped JSON — this is
 * the whole point of the activity log (it replaced the old behaviour of adding a
 * duplicate row to the visit list on every reschedule). `technicianNameById` resolves
 * a `TECHNICIAN_ASSIGNED` payload's user ids to names when the roster has them.
 *
 * Any `type` this build doesn't recognize, or a known `type` whose `payload` doesn't
 * match the expected shape (a future backend change, a partially-migrated event, or
 * simply a bug elsewhere), degrades to a generic sentence plus a raw key/value dump of
 * whatever `payload` actually contains — it never throws and never hides the event.
 */
function EventDescription({
  event,
  technicianNameById,
}: {
  event: AdminVisitEvent;
  technicianNameById: Map<number, string>;
}) {
  const actor = actorFor(event.source);
  const technicianLabel = (userId: number) =>
    technicianNameById.get(userId) ?? `technician #${userId}`;

  if (event.type === "RESCHEDULED") {
    const p = asRescheduledPayload(event.payload);
    if (p) {
      return (
        <>
          {actor} rescheduled this visit from {formatDateTime(p.from)} to {formatDateTime(p.to)}.
        </>
      );
    }
  }

  if (event.type === "TECHNICIAN_ASSIGNED") {
    const p = asTechnicianAssignedPayload(event.payload);
    if (p) {
      if (p.from === null) {
        return (
          <>
            {actor} assigned {technicianLabel(p.to)} to this visit.
          </>
        );
      }
      return (
        <>
          {actor} reassigned this visit from {technicianLabel(p.from)} to {technicianLabel(p.to)}.
        </>
      );
    }
  }

  if (event.type === "CANCELLED") {
    return <>{actor} cancelled this visit.</>;
  }

  // Unknown event type, or a known type with an unexpected payload shape: degrade to
  // a generic sentence plus a raw dump of whatever the payload actually contains.
  const entries = isRecord(event.payload) ? Object.entries(event.payload) : [];
  return (
    <>
      {actor} recorded {humanizeEventType(event.type)}.
      {entries.length > 0 && (
        <dl className="mt-1 space-y-0.5">
          {entries.map(([key, value]) => (
            <div key={key} className="flex gap-1 text-xs text-muted-foreground">
              <dt className="font-medium">{key}:</dt>
              <dd className="min-w-0 truncate">{stringifyPayloadValue(value)}</dd>
            </div>
          ))}
        </dl>
      )}
    </>
  );
}

/**
 * `RESCHEDULED` -> "a reschedule", `TECHNICIAN_ASSIGNED` -> "a technician assigned"
 * event, `SOME_NEW_TYPE` -> "a some new type" event. Deliberately generic (this only
 * fires for a `type` this build doesn't have a dedicated sentence for) — lowercase,
 * underscores to spaces.
 */
function humanizeEventType(type: string): string {
  return `a ${type.toLowerCase().replace(/_/g, " ")} event`;
}

function ActivitySection({ visitId }: { visitId: number }) {
  const { data: events, isLoading, isError, refetch } = useAdminVisitEvents(visitId);
  const { data: technicians } = useAdminTechnicians();

  const technicianNameById = useMemo(() => {
    const map = new Map<number, string>();
    for (const t of technicians ?? []) {
      const name = [t.firstName, t.lastName].filter(Boolean).join(" ").trim();
      if (name) map.set(t.userId, name);
    }
    return map;
  }, [technicians]);

  return (
    <Section title="Activity">
      {isLoading && <PanelLoading label="Loading activity." className="p-0" />}

      {isError && !isLoading && (
        <PanelError label="We couldn't load activity." onRetry={() => void refetch()} />
      )}

      {events && events.length === 0 && (
        <p className="text-xs text-muted-foreground">
          No activity yet. This visit hasn't been rescheduled, reassigned, or cancelled.
        </p>
      )}

      {events && events.length > 0 && (
        <ul className="space-y-3" role="list">
          {events.map((event) => (
            <li key={event.id} className="border-l-2 border-border pl-3 text-sm">
              <p className="text-foreground">
                <EventDescription event={event} technicianNameById={technicianNameById} />
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {formatDateTime(event.occurredAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
