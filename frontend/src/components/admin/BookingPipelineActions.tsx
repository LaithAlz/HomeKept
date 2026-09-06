/**
 * The walk-through pipeline's status-change actions (Confirm/Decline, Mark performed/
 * No-show, Send/Resend activation invite, Mark lost), extracted from
 * `routes/admin.walkthroughs.index.tsx` so the exact same behaviour renders in two
 * places: the pipeline list (grouped by status) and the standalone booking detail page
 * (`/admin/walkthroughs/$id`) — an operator who opened a booking to read it can act on
 * it right there instead of going back to the list. Both call sites share
 * `usePatchBooking`/`useSendActivationInvite` from `@/lib/admin`, so a status change or
 * invite made from either place invalidates the same queries and stays in sync.
 */
import { useState } from "react";
import { Check, X, Send, Loader2, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/format";
import { ApiError } from "@/lib/api";
import {
  usePatchBooking,
  useSendActivationInvite,
  type AdminBookingListItem,
  type BookingStatus,
} from "@/lib/admin";

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

export function PendingActions({ booking }: { booking: AdminBookingListItem }) {
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

export function ConfirmedActions({ booking }: { booking: AdminBookingListItem }) {
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

export function PerformedActions({ booking }: { booking: AdminBookingListItem }) {
  const { change, pending: statusPending, error: statusError } = useStatusChange(booking.id);
  const invite = useSendActivationInvite();
  // Optimistic "just sent" flag — only needed for the moment between the mutation
  // resolving and the `["admin", "bookings"]` refetch (triggered by the mutation's
  // own `onSuccess`) landing with the real `invitedAt` from the server. Once that
  // refetch lands, `booking.invitedAt` takes over as the source of truth.
  const [justSent, setJustSent] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);

  function sendInvite() {
    setInviteError(null);
    invite.mutate(booking.id, {
      onSuccess: () => setJustSent(true),
      onError: (err) => {
        setInviteError(
          err instanceof ApiError
            ? "We couldn't send the invite. Please try again."
            : "Something went wrong. Please try again.",
        );
      },
    });
  }

  const invitedAt = booking.invitedAt ?? null;
  const sent = justSent || invitedAt !== null;

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex gap-2">
        {sent ? (
          <div className="flex items-center gap-2">
            <span
              role="status"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-moss"
            >
              <Check className="h-3.5 w-3.5" aria-hidden="true" />
              {invitedAt ? `Invite sent ${formatDateTime(invitedAt)}` : "Invite sent"}
            </span>
            <Button size="sm" variant="outline" disabled={invite.isPending} onClick={sendInvite}>
              {invite.isPending && (
                <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              )}
              Resend
            </Button>
          </div>
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

/**
 * Picks the right action set for a booking's current status, or `null` for a closed
 * booking (CONVERTED/LOST/NO_SHOW) — there is nothing left to do to one of those from
 * here. Used by the detail page so it doesn't need to duplicate this status switch;
 * the pipeline list groups by status itself instead (see `PipelineView`) and calls the
 * three exports above directly.
 */
export function PipelineActionsFor({ booking }: { booking: AdminBookingListItem }) {
  switch (booking.status) {
    case "PENDING":
      return <PendingActions booking={booking} />;
    case "CONFIRMED":
      return <ConfirmedActions booking={booking} />;
    case "PERFORMED":
      return <PerformedActions booking={booking} />;
    default:
      return null;
  }
}
