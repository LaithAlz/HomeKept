import { useEffect, useRef } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PanelLoading } from "@/components/admin/PanelStates";
import { PipelineActionsFor } from "@/components/admin/BookingPipelineActions";
import { formatDateTime } from "@/lib/format";
import { ApiError } from "@/lib/api";
import {
  useAdminBooking,
  formatWeekOf,
  isBookingOpen,
  BOOKING_STATUS_LABEL,
  BOOKING_STATUS_TONE,
  BOOKING_PROPERTY_TYPE_LABEL,
  BOOKING_TIME_OF_DAY_LABEL,
  BOOKING_DAY_LABEL,
  BOOKING_LEAD_SOURCE_LABEL,
  BOOKING_SQFT_LABEL,
  type AdminBookingDetail,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/walkthroughs/$id")({
  head: ({ params }) => ({
    meta: [
      { title: `Booking #${params.id} — HomeKept Admin` },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: BookingDetailPage,
});

// ---------------------------------------------------------------------------
// Page shell: breadcrumb, loading/error/not-found routing, focus management
// ---------------------------------------------------------------------------

/**
 * The walk-through booking detail page (founder's ask: "each walk thru should be
 * clickable with the details of everything about them"). Follows the same shape as
 * `routes/admin.subscribers.$id.tsx`: a layout route (`admin.walkthroughs.tsx`) hands
 * off via `<Outlet />` to either the pipeline list (`admin.walkthroughs.index.tsx`) or
 * this record page; breadcrumb back to the list; the contact's name as the page
 * heading; focus moved to that heading on navigation; honest loading/error/not-found
 * states.
 */
function BookingDetailPage() {
  const { id } = Route.useParams();
  const bookingId = Number(id);
  const validId = Number.isInteger(bookingId) && bookingId > 0;

  const query = useAdminBooking(validId ? bookingId : null);

  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus();
  }, [id]);

  const is404 = query.isError && query.error instanceof ApiError && query.error.status === 404;

  let body: React.ReactNode;
  let crumbLabel: string;
  if (!validId || is404) {
    body = <BookingNotFound headingRef={headingRef} />;
    crumbLabel = "Not found";
  } else if (query.isLoading || !query.data) {
    body = <BookingLoading headingRef={headingRef} />;
    crumbLabel = "Loading…";
  } else if (query.isError) {
    body = <BookingLoadError headingRef={headingRef} onRetry={() => void query.refetch()} />;
    crumbLabel = "Booking detail";
  } else {
    body = <BookingDetailView booking={query.data} headingRef={headingRef} />;
    crumbLabel = query.data.fullName || `Booking #${query.data.id}`;
  }

  return (
    <div className="px-6 py-8">
      <nav aria-label="Breadcrumb" className="mb-6">
        <ol className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <li>
            <Link
              to="/admin/walkthroughs"
              className="inline-flex items-center gap-1 font-medium text-muted-foreground hover:text-foreground focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <ArrowLeft className="size-3.5" aria-hidden="true" />
              Walk-throughs
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

function BookingLoading({ headingRef }: { headingRef: HeadingRef }) {
  return (
    <div className="py-16">
      <h1 ref={headingRef} tabIndex={-1} className="sr-only outline-none">
        Loading booking
      </h1>
      <PanelLoading label="Loading booking." className="justify-center" />
    </div>
  );
}

function BookingLoadError({
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
        We couldn't load this booking.
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">Check your connection and try again.</p>
      <div className="mt-8 flex flex-wrap justify-center gap-2">
        <Button onClick={onRetry}>Try again</Button>
        <Button variant="outline" asChild>
          <Link to="/admin/walkthroughs">
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to walk-throughs
          </Link>
        </Button>
      </div>
    </div>
  );
}

function BookingNotFound({ headingRef }: { headingRef: HeadingRef }) {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Not found</p>
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="mt-4 rounded-md font-display text-3xl font-semibold text-primary outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        No such booking
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        We couldn't find that walk-through booking. It may have been removed or the link may be
        incorrect.
      </p>
      <div className="mt-8">
        <Button asChild>
          <Link to="/admin/walkthroughs">
            <ArrowLeft className="size-4" aria-hidden="true" />
            Back to walk-throughs
          </Link>
        </Button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Loaded detail
// ---------------------------------------------------------------------------

function BookingDetailView({
  booking,
  headingRef,
}: {
  booking: AdminBookingDetail;
  headingRef: HeadingRef;
}) {
  const hasName = booking.fullName.trim().length > 0;
  const title = hasName ? booking.fullName : `Booking #${booking.id}`;

  return (
    <>
      <header>
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="rounded-md font-display text-2xl font-extrabold tracking-tight outline-none focus-visible:ring-2 focus-visible:ring-ring md:text-3xl"
        >
          {title}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">Booking #{booking.id}</p>

        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm">
          {booking.email && (
            <a
              href={`mailto:${booking.email}`}
              className="text-primary underline-offset-2 hover:underline"
            >
              {booking.email}
            </a>
          )}
          {booking.phone && (
            <a
              href={`tel:${booking.phone}`}
              className="text-primary underline-offset-2 hover:underline"
            >
              {booking.phone}
            </a>
          )}
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span
            className={cn(
              "rounded-full px-2 py-0.5 text-xs font-medium",
              BOOKING_STATUS_TONE[booking.status] ?? "bg-muted text-muted-foreground",
            )}
          >
            {BOOKING_STATUS_LABEL[booking.status] ?? booking.status}
          </span>
        </div>
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="min-w-0 space-y-6">
          <PipelineSection booking={booking} />
          <PropertySection booking={booking} />
          <SchedulingSection booking={booking} />
          <NotesSection booking={booking} />
        </div>

        <div className="space-y-6">
          <LeadSection booking={booking} />
          <ConsentSection booking={booking} />
        </div>
      </div>
    </>
  );
}

/** One card/section shell shared by every section of the booking detail page. */
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

/**
 * The pipeline's status-change actions, reused verbatim from the list
 * (`@/components/admin/BookingPipelineActions`) — an operator who opened a booking to
 * read it can act on it right here instead of going back to the list. Hidden entirely
 * for a closed booking (CONVERTED/LOST/NO_SHOW, per `isBookingOpen`): there's nothing
 * left to do to one of those, so an empty "Pipeline" card would be worse than none.
 */
function PipelineSection({ booking }: { booking: AdminBookingDetail }) {
  if (!isBookingOpen(booking.status)) return null;
  return (
    <Section title="Pipeline">
      <div className="flex justify-end">
        <PipelineActionsFor booking={booking} />
      </div>
    </Section>
  );
}

function PropertySection({ booking }: { booking: AdminBookingDetail }) {
  return (
    <Section title="Property">
      <dl className="divide-y divide-border">
        <Row
          label="Address"
          value={`${booking.streetAddress}, ${booking.city} ${booking.postalCode}`}
        />
        <Row
          label="Type"
          value={BOOKING_PROPERTY_TYPE_LABEL[booking.propertyType] ?? booking.propertyType}
        />
        <Row label="Year built" value={booking.yearBuilt ?? "Not provided"} />
        <Row
          label="Square footage"
          value={
            booking.squareFootageRange
              ? (BOOKING_SQFT_LABEL[booking.squareFootageRange] ?? booking.squareFootageRange)
              : "Not provided"
          }
        />
      </dl>
    </Section>
  );
}

function SchedulingSection({ booking }: { booking: AdminBookingDetail }) {
  return (
    <Section title="Scheduling">
      <dl className="divide-y divide-border">
        <Row label="Preferred week" value={`Week of ${formatWeekOf(booking.preferredWeek)}`} />
        <Row
          label="Time of day"
          value={BOOKING_TIME_OF_DAY_LABEL[booking.timeOfDay] ?? booking.timeOfDay}
        />
        <Row
          label="Day preferences"
          value={
            booking.dayPreferences.length > 0
              ? booking.dayPreferences.map((d) => BOOKING_DAY_LABEL[d] ?? d).join(", ")
              : "No preference"
          }
        />
        <Row
          label="Scheduled for"
          value={booking.scheduledFor ? formatDateTime(booking.scheduledFor) : "Not yet scheduled"}
        />
        <Row
          label="Performed"
          value={booking.performedAt ? formatDateTime(booking.performedAt) : "Not yet performed"}
        />
      </dl>
    </Section>
  );
}

function NotesSection({ booking }: { booking: AdminBookingDetail }) {
  return (
    <Section title="Notes">
      {booking.notes ? (
        <p className="whitespace-pre-wrap text-sm text-foreground">{booking.notes}</p>
      ) : (
        <p className="text-xs text-muted-foreground">No notes on file.</p>
      )}
    </Section>
  );
}

function LeadSection({ booking }: { booking: AdminBookingDetail }) {
  return (
    <Section title="Lead">
      <dl className="divide-y divide-border">
        <Row
          label="Source"
          value={BOOKING_LEAD_SOURCE_LABEL[booking.leadSource] ?? booking.leadSource}
        />
        <Row label="Booked" value={formatDateTime(booking.createdAt)} />
      </dl>
    </Section>
  );
}

function ConsentSection({ booking }: { booking: AdminBookingDetail }) {
  return (
    <Section title="Consent & invite">
      <dl className="divide-y divide-border">
        <Row
          label="Contact consent"
          value={
            booking.contactConsentAt ? formatDateTime(booking.contactConsentAt) : "Not on file"
          }
        />
        <Row
          label="Activation invite"
          value={booking.invitedAt ? `Sent ${formatDateTime(booking.invitedAt)}` : "Not sent yet"}
        />
      </dl>
    </Section>
  );
}
