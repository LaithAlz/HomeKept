import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Calendar, MapPin, Check, X, Send, Loader2, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api";
import {
  useAdminBookings,
  usePatchBooking,
  useSendActivationInvite,
  type AdminBookingListItem,
  type BookingStatus,
} from "@/lib/admin";

export const Route = createFileRoute("/admin/walkthroughs")({
  head: () => ({
    meta: [{ title: "Walk-throughs — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: WalkthroughsPage,
});

/**
 * `preferredWeek` is a LocalDate ("YYYY-MM-DD") — a calendar date with no
 * time-of-day meaning. Anchoring it to UTC noon and formatting with an
 * explicit UTC timeZone keeps the displayed date stable regardless of the
 * viewer's local timezone (avoids the off-by-one day you'd get parsing a
 * bare date string as local midnight and then rendering in another zone).
 */
function formatWeekOf(dateStr: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
  }).format(new Date(`${dateStr}T12:00:00Z`));
}

const NON_TERMINAL: BookingStatus[] = ["PENDING", "CONFIRMED", "PERFORMED"];

function WalkthroughsPage() {
  const { data: bookings, isLoading, isError, refetch } = useAdminBookings({ limit: 100 });

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Walk-throughs</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Free 90-minute in-home visits across Mississauga, Oakville, and Milton.
          </p>
        </div>
        <Button size="sm">Schedule walk-through</Button>
      </div>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="mt-8 flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading the pipeline.
        </div>
      )}

      {isError && !isLoading && (
        <div
          role="alert"
          className="mt-8 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>We couldn't load the walk-through pipeline.</span>
          <Button size="sm" variant="outline" onClick={() => void refetch()}>
            Try again
          </Button>
        </div>
      )}

      {bookings && <PipelineView bookings={bookings} />}
    </div>
  );
}

function PipelineView({ bookings }: { bookings: AdminBookingListItem[] }) {
  const pending = bookings.filter((b) => b.status === "PENDING");
  const confirmed = bookings.filter((b) => b.status === "CONFIRMED");
  const performed = bookings.filter((b) => b.status === "PERFORMED");
  const closed = bookings.filter((b) => !NON_TERMINAL.includes(b.status));

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
              <div className="font-medium">{b.fullName}</div>
              <div className="mt-0.5 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <MapPin className="h-3.5 w-3.5" aria-hidden="true" /> {b.city}
                </span>
                <span>Source: {b.leadSource}</span>
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
              <div className="font-medium">{b.fullName}</div>
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
  const label =
    status === "CONVERTED"
      ? "Converted"
      : status === "LOST"
        ? "Lost"
        : status === "NO_SHOW"
          ? "No-show"
          : status;
  const tone =
    status === "CONVERTED"
      ? "bg-emerald-500/10 text-emerald-700"
      : "bg-muted text-muted-foreground";
  return <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", tone)}>{label}</span>;
}

/* -------------------------------------------------------------------------- */
/* Per-status action rows                                                     */
/* -------------------------------------------------------------------------- */

function useStatusChange(bookingId: number) {
  const mutation = usePatchBooking();
  const [error, setError] = useState<string | null>(null);

  function change(status: BookingStatus) {
    setError(null);
    mutation.mutate(
      { id: bookingId, request: { status } },
      {
        onError: (err) => {
          setError(
            err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
          );
        },
      },
    );
  }

  return { change, pending: mutation.isPending, error };
}

function PendingActions({ booking }: { booking: AdminBookingListItem }) {
  const { change, pending, error } = useStatusChange(booking.id);
  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex gap-2">
        <Button size="sm" variant="outline" disabled={pending} onClick={() => change("CONFIRMED")}>
          <Check className="mr-1 h-3.5 w-3.5" aria-hidden="true" /> Confirm
        </Button>
        <Button size="sm" variant="ghost" disabled={pending} onClick={() => change("LOST")}>
          <X className="mr-1 h-3.5 w-3.5" aria-hidden="true" /> Decline
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function ConfirmedActions({ booking }: { booking: AdminBookingListItem }) {
  const { change, pending, error } = useStatusChange(booking.id);
  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex gap-2">
        <Button size="sm" variant="outline" disabled={pending} onClick={() => change("PERFORMED")}>
          Mark performed
        </Button>
        <Button size="sm" variant="ghost" disabled={pending} onClick={() => change("NO_SHOW")}>
          No-show
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function PerformedActions({ booking }: { booking: AdminBookingListItem }) {
  const { change, pending: statusPending, error: statusError } = useStatusChange(booking.id);
  const invite = useSendActivationInvite();
  const [inviteSent, setInviteSent] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);

  function sendInvite() {
    setInviteError(null);
    invite.mutate(booking.id, {
      onSuccess: () => setInviteSent(true),
      onError: (err) => {
        setInviteError(
          err instanceof ApiError
            ? "We couldn't send the invite. Please try again."
            : "Something went wrong. Please try again.",
        );
      },
    });
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex gap-2">
        {inviteSent ? (
          <span
            role="status"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-moss"
          >
            <Check className="h-3.5 w-3.5" aria-hidden="true" /> Invite sent
          </span>
        ) : (
          <Button size="sm" variant="accent" disabled={invite.isPending} onClick={sendInvite}>
            {invite.isPending ? (
              <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" aria-hidden="true" />
            ) : (
              <Send className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
            )}
            Send activation invite
          </Button>
        )}
        <Button size="sm" variant="ghost" disabled={statusPending} onClick={() => change("LOST")}>
          Mark lost
        </Button>
      </div>
      {inviteError && (
        <div role="alert" className="flex items-center gap-2">
          <p className="text-xs text-destructive">{inviteError}</p>
          <button
            type="button"
            onClick={sendInvite}
            aria-label="Retry sending the activation invite"
            className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
          >
            <RotateCcw className="h-3 w-3" aria-hidden="true" /> Retry
          </button>
        </div>
      )}
      {statusError && (
        <p role="alert" className="text-xs text-destructive">
          {statusError}
        </p>
      )}
    </div>
  );
}
