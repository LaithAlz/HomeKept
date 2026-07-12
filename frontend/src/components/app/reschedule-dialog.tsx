/**
 * Reschedule-request dialog, shared by the visit detail page
 * (`routes/app.visits.$id.tsx`), the visits list's next-visit card
 * (`routes/app.visits.tsx`), and the dashboard's next-visit card
 * (`routes/app.index.tsx`) — every place a customer can ask to move a
 * scheduled visit funnels through the same form and the same mutation
 * (`useCreateRescheduleRequest` in `@/lib/visits`), so the request shape
 * never drifts between entry points.
 *
 * The customer proposes 1-3 preferred start times; the backend accepts up
 * to 5 (`CreateRescheduleRequest.java`), but three is already generous and
 * keeps the dialog short. An admin later confirms one of the proposed
 * times (or negotiates another) — this only records the request.
 *
 * This file also exports `PendingReschedulePill`, the "pending confirmation"
 * pill + its "Cancel request" affordance shown in place of the Reschedule
 * button once `hasPendingRescheduleRequest` is true. Same three call sites,
 * same shared piece, so the withdraw flow never drifts either.
 */

import { useId, useEffect, useState, type FormEvent } from "react";
import { Loader2, Plus, RefreshCcw, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api";
import { TZ } from "@/lib/format";
import { cn } from "@/lib/utils";
import { useCancelRescheduleRequest, useCreateRescheduleRequest } from "@/lib/visits";

const MAX_SLOTS = 3;

interface Slot {
  date: string; // yyyy-mm-dd, from <input type="date">
  time: string; // HH:mm, from <input type="time">
}

const emptySlot: Slot = { date: "", time: "" };

function todayInToronto(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

/**
 * Converts a filled-in slot to an ISO instant for the wire. Interprets the
 * date/time the customer typed as their local (browser) time, which for our
 * GTA West customers is America/Toronto — there's no reliable way to know
 * they mean Toronto time specifically if they're travelling, but that's an
 * edge case we accept for a v1 self-serve form.
 */
function slotToIso(slot: Slot): string | null {
  if (!slot.date || !slot.time) return null;
  const local = new Date(`${slot.date}T${slot.time}`);
  if (Number.isNaN(local.getTime())) return null;
  return local.toISOString();
}

interface RescheduleDialogProps {
  visitId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called once the request is recorded, before the dialog closes. */
  onRequested?: () => void;
}

export function RescheduleDialog({
  visitId,
  open,
  onOpenChange,
  onRequested,
}: RescheduleDialogProps) {
  const [slots, setSlots] = useState<Slot[]>([emptySlot]);
  const [error, setError] = useState<string | null>(null);
  const mutation = useCreateRescheduleRequest(visitId);
  const baseId = useId();
  const titleId = `${baseId}-title`;
  const descId = `${baseId}-desc`;
  const errorId = `${baseId}-error`;

  // Reset to a clean single-slot form every time the dialog is (re)opened.
  useEffect(() => {
    if (open) {
      setSlots([emptySlot]);
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, visitId]);

  function updateSlot(index: number, patch: Partial<Slot>) {
    setSlots((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  }

  function addSlot() {
    setSlots((prev) => (prev.length >= MAX_SLOTS ? prev : [...prev, emptySlot]));
  }

  function removeSlot(index: number) {
    setSlots((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== index)));
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const isos = slots.map(slotToIso);
    if (isos.some((iso) => iso === null)) {
      setError("Fill in a date and time for every preferred slot.");
      return;
    }
    const preferredDates = isos as string[];

    const now = Date.now();
    if (preferredDates.some((iso) => new Date(iso).getTime() <= now)) {
      setError("Preferred times must be in the future.");
      return;
    }

    mutation.mutate(preferredDates, {
      onSuccess: () => {
        toast.success("Reschedule requested", {
          description: "We'll confirm one of your preferred times and let you know.",
        });
        onRequested?.();
        onOpenChange(false);
      },
      onError: (err) => {
        // A 409 here means the visit is no longer schedulable or a pending request
        // already exists (see `RescheduleService.createRequest`); either way the
        // backend's message is a pre-canned, safe string, fine to show verbatim.
        setError(
          err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
        );
      },
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent aria-labelledby={titleId} aria-describedby={descId} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle id={titleId}>Request a reschedule</DialogTitle>
          <DialogDescription id={descId}>
            Propose up to {MAX_SLOTS} times that work for you. We'll confirm one and let you know.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} noValidate>
          <fieldset disabled={mutation.isPending} className="space-y-4">
            <legend className="sr-only">Preferred times</legend>
            {slots.map((slot, i) => (
              <div key={i} className="flex items-end gap-2">
                <div className="flex-1">
                  <Label htmlFor={`${baseId}-date-${i}`}>Preferred time {i + 1}</Label>
                  <Input
                    id={`${baseId}-date-${i}`}
                    type="date"
                    min={todayInToronto()}
                    value={slot.date}
                    onChange={(e) => updateSlot(i, { date: e.target.value })}
                    required
                    aria-describedby={error ? errorId : undefined}
                    className="mt-1"
                  />
                </div>
                <div className="w-32 shrink-0">
                  <Label htmlFor={`${baseId}-time-${i}`} className="sr-only">
                    Time for preferred time {i + 1}
                  </Label>
                  <Input
                    id={`${baseId}-time-${i}`}
                    type="time"
                    value={slot.time}
                    onChange={(e) => updateSlot(i, { time: e.target.value })}
                    required
                    aria-describedby={error ? errorId : undefined}
                    className="mt-1"
                  />
                </div>
                {slots.length > 1 && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="size-9 shrink-0"
                    onClick={() => removeSlot(i)}
                    aria-label={`Remove preferred time ${i + 1}`}
                  >
                    <X className="size-4" aria-hidden="true" />
                  </Button>
                )}
              </div>
            ))}

            {slots.length < MAX_SLOTS && (
              <Button type="button" variant="outline" size="sm" onClick={addSlot}>
                <Plus className="size-4" aria-hidden="true" />
                Add another time
              </Button>
            )}
          </fieldset>

          {error && (
            <p id={errorId} role="alert" className="mt-4 text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter className="mt-6">
            <DialogClose asChild>
              <Button type="button" variant="outline" disabled={mutation.isPending}>
                Cancel
              </Button>
            </DialogClose>
            <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              Send request
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// Pending pill + cancel affordance
// ---------------------------------------------------------------------------

interface PendingReschedulePillProps {
  visitId: number;
  /**
   * `"default"` for a light card surface (the visits list and dashboard
   * cards); `"inverse"` for the visits page's dark `bg-primary` next-visit
   * hero, so both the pill and the cancel control stay readable in place.
   */
  variant?: "default" | "inverse";
}

/**
 * Renders the "Reschedule requested, pending confirmation" pill (unchanged
 * copy) together with a "Cancel request" control that calls
 * `useCancelRescheduleRequest`. Success is implicit: the mutation's own
 * `onSuccess` invalidates the visit queries, so this pill unmounts and the
 * normal Reschedule button reappears once the parent re-renders with fresh
 * data. A 404 (already resolved or already withdrawn) is treated the same
 * way, no error shown, since the hook already invalidates on that path too.
 */
export function PendingReschedulePill({
  visitId,
  variant = "default",
}: PendingReschedulePillProps) {
  const mutation = useCancelRescheduleRequest(visitId);
  const [error, setError] = useState<string | null>(null);
  const baseId = useId();
  const errorId = `${baseId}-cancel-reschedule-error`;
  const isInverse = variant === "inverse";

  function handleCancel() {
    setError(null);
    mutation.mutate(undefined, {
      onError: (err) => {
        // A 404 means the request was already resolved or already withdrawn
        // elsewhere — the hook still invalidates on that path, so the pill
        // will unmount on refetch. Nothing actionable to show the customer.
        if (err instanceof ApiError && err.status === 404) return;
        setError(
          err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
        );
      },
    });
  }

  return (
    <div className="flex flex-col items-start gap-1.5">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={cn(
            "inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold",
            isInverse
              ? "bg-primary-foreground/10 text-primary-foreground/80"
              : "bg-muted text-muted-foreground",
          )}
        >
          <RefreshCcw className="size-3.5" aria-hidden="true" />
          Reschedule requested, pending confirmation
        </span>
        <button
          type="button"
          onClick={handleCancel}
          disabled={mutation.isPending}
          aria-busy={mutation.isPending}
          aria-describedby={error ? errorId : undefined}
          className={cn(
            "rounded text-xs font-semibold underline underline-offset-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60",
            isInverse
              ? "text-primary-foreground/70 hover:text-primary-foreground"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          {mutation.isPending ? "Cancelling request…" : "Cancel request"}
        </button>
      </div>
      {error && (
        <p
          id={errorId}
          role="alert"
          className={cn(
            "text-xs",
            isInverse
              ? "rounded-lg bg-destructive px-2 py-1 font-medium text-destructive-foreground"
              : "text-destructive",
          )}
        >
          {error}
        </p>
      )}
    </div>
  );
}
