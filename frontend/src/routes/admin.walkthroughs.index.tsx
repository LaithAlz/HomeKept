import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Calendar, MapPin } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { NewBookingDialog } from "@/components/admin/NewBookingDialog";
import {
  PendingActions,
  ConfirmedActions,
  PerformedActions,
} from "@/components/admin/BookingPipelineActions";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  useAdminBookings,
  formatWeekOf,
  isBookingOpen,
  BOOKING_STATUS_LABEL,
  BOOKING_STATUS_TONE,
  BOOKING_LEAD_SOURCE_LABEL,
  type AdminBookingListItem,
  type BookingStatus,
} from "@/lib/admin";

export const Route = createFileRoute("/admin/walkthroughs/")({
  head: () => ({
    meta: [{ title: "Walk-throughs — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: WalkthroughsPage,
});

function WalkthroughsPage() {
  const { data: bookings, isLoading, isError, refetch } = useAdminBookings({ limit: 100 });
  const [newBookingOpen, setNewBookingOpen] = useState(false);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Walk-throughs</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Free 90-minute in-home visits across Mississauga, Oakville, and Milton.
          </p>
        </div>
        <Button size="sm" onClick={() => setNewBookingOpen(true)}>
          Schedule walk-through
        </Button>
      </div>

      <NewBookingDialog open={newBookingOpen} onOpenChange={setNewBookingOpen} />

      {isLoading && <PanelLoading label="Loading the pipeline." className="mt-8" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load the walk-through pipeline."
          onRetry={() => void refetch()}
          className="mt-8 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {bookings && <PipelineView bookings={bookings} />}
    </div>
  );
}

function PipelineView({ bookings }: { bookings: AdminBookingListItem[] }) {
  const pending = bookings.filter((b) => b.status === "PENDING");
  const confirmed = bookings.filter((b) => b.status === "CONFIRMED");
  const performed = bookings.filter((b) => b.status === "PERFORMED");
  const closed = bookings.filter((b) => !isBookingOpen(b.status));

  return (
    <>
      <div className="mt-6 grid grid-cols-3 gap-3">
        <Stat
          label="Open pipeline"
          value={pending.length + confirmed.length + performed.length}
          hint="active leads"
        />
        <Stat label="Needs confirmation" value={pending.length} hint="awaiting reply" />
        <Stat label="Ready to invite" value={performed.length} hint="walked, not yet invited" />
      </div>

      <Section
        title="Needs confirmation"
        items={pending}
        highlight
        renderActions={(b) => <PendingActions booking={b} />}
      />
      <Section
        title="Confirmed"
        items={confirmed}
        renderActions={(b) => <ConfirmedActions booking={b} />}
      />
      <Section
        title="Walked, ready to invite"
        items={performed}
        renderActions={(b) => <PerformedActions booking={b} />}
      />
      <ClosedSection items={closed} />
    </>
  );
}

function Stat({ label, value, hint }: { label: string; value: number; hint: string }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 font-display text-2xl font-extrabold">{value}</div>
      <div className="text-xs text-muted-foreground">{hint}</div>
    </div>
  );
}

function Section({
  title,
  items,
  highlight,
  renderActions,
}: {
  title: string;
  items: AdminBookingListItem[];
  highlight?: boolean;
  renderActions: (booking: AdminBookingListItem) => React.ReactNode;
}) {
  if (items.length === 0) return null;
  return (
    <div className="mt-8">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      <div className="mt-3 overflow-hidden rounded-2xl border border-border">
        {items.map((b, i) => (
          <div
            key={b.id}
            className={cn(
              "flex flex-wrap items-center gap-4 px-4 py-4",
              i > 0 && "border-t border-border",
              highlight && "bg-amber-500/5",
            )}
          >
            <div className="min-w-[200px] flex-1">
              <Link
                to="/admin/walkthroughs/$id"
                params={{ id: String(b.id) }}
                className="rounded font-medium text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {b.fullName}
              </Link>
              <div className="mt-0.5 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <MapPin className="h-3.5 w-3.5" aria-hidden="true" /> {b.city}
                </span>
                <span>Source: {BOOKING_LEAD_SOURCE_LABEL[b.leadSource] ?? b.leadSource}</span>
              </div>
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Calendar className="h-4 w-4" aria-hidden="true" />
              {b.scheduledFor
                ? formatDateTime(b.scheduledFor)
                : `Week of ${formatWeekOf(b.preferredWeek)}`}
            </div>
            {renderActions(b)}
          </div>
        ))}
      </div>
    </div>
  );
}

function ClosedSection({ items }: { items: AdminBookingListItem[] }) {
  if (items.length === 0) return null;
  return (
    <details className="mt-8 rounded-2xl border border-border">
      <summary className="cursor-pointer px-4 py-3 font-display text-sm font-bold text-muted-foreground">
        Closed ({items.length})
      </summary>
      <div className="border-t border-border">
        {items.map((b, i) => (
          <div
            key={b.id}
            className={cn(
              "flex flex-wrap items-center gap-4 px-4 py-3 text-sm",
              i > 0 && "border-t border-border",
            )}
          >
            <div className="min-w-[200px] flex-1">
              <Link
                to="/admin/walkthroughs/$id"
                params={{ id: String(b.id) }}
                className="rounded font-medium text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {b.fullName}
              </Link>
              <div className="text-xs text-muted-foreground">{b.city}</div>
            </div>
            <StatusBadge status={b.status} />
          </div>
        ))}
      </div>
    </details>
  );
}

function StatusBadge({ status }: { status: BookingStatus }) {
  return (
    <span
      className={cn(
        "rounded-full px-2 py-0.5 text-xs font-medium",
        BOOKING_STATUS_TONE[status] ?? "bg-muted text-muted-foreground",
      )}
    >
      {BOOKING_STATUS_LABEL[status] ?? status}
    </span>
  );
}
