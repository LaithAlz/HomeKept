import { useEffect, useId, useMemo, useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { CalendarClock, Loader2, Search } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { CalendarGrid, toMonthKey } from "@/components/admin/MonthLoadCalendar";
import {
  dayKey,
  formatCentsCad,
  formatDateShort,
  formatDateTime,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from "@/lib/format";
import { ApiError } from "@/lib/api";
import {
  useAdminVisits,
  useAdminRescheduleRequests,
  useAdminTechnicians,
  useConfirmRescheduleRequest,
  useDeclineRescheduleRequest,
  usePatchAdminVisit,
  VISIT_STATUS_LABEL,
  VISIT_STATUS_TONE,
  VISIT_TYPE_LABEL,
  type AdminVisitListItem,
  type AdminRescheduleRequestListItem,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/visits/")({
  head: () => ({
    meta: [{ title: "Visits — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: VisitsPage,
});

/**
 * A 400/409 from the visit-patch or reschedule-request endpoints usually carries a
 * pre-canned, safe-to-show message (ambiguous patch, illegal state transition,
 * already-resolved request, no-longer-reschedulable visit — see
 * `VisitAdminService`/`RescheduleService`) directly on `err.message`. The one
 * exception is `VALIDATION_FAILED` (e.g. a past `scheduledFor`): the backend's
 * top-level message there is the generic "Validation failed", with the actual,
 * readable reason keyed by field name in `err.fields` instead (same shape
 * `describeSkuError` in `admin.subscribers.$id.tsx` already unpacks) — surface that
 * instead, so a rejected reschedule reads as a sentence, not a raw error code.
 */
function describeVisitError(err: unknown): string {
  if (err instanceof ApiError && err.status === 400 && err.code === "VALIDATION_FAILED") {
    const messages = Object.values(err.fields ?? {});
    if (messages.length > 0) return messages.join(" ");
  }
  if (err instanceof ApiError && (err.status === 400 || err.status === 409)) {
    return err.message;
  }
  return "Something went wrong on our end. Please try again.";
}

function VisitsPage() {
  const { data: visits, isLoading, isError, refetch } = useAdminVisits({ limit: 100 });
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [type, setType] = useState<string>("all");
  const [rescheduleTarget, setRescheduleTarget] = useState<AdminVisitListItem | null>(null);
  const [cancelTarget, setCancelTarget] = useState<AdminVisitListItem | null>(null);

  const rows = useMemo(() => {
    if (!visits) return [];
    return visits.filter((v) => {
      if (status !== "all" && v.status !== status) return false;
      if (type !== "all" && v.type !== type) return false;
      if (q && !String(v.subscriberId).includes(q.trim())) return false;
      return true;
    });
  }, [visits, q, status, type]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Visits</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {visits ? `${rows.length} of ${visits.length} visits` : "Loading visits…"}
          </p>
        </div>
      </div>

      <RescheduleRequestsSection />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-56">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="visit-search" className="sr-only">
            Search by subscriber ID
          </label>
          <Input
            id="visit-search"
            placeholder="Search by subscriber ID"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-44" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {Object.entries(VISIT_STATUS_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={type} onValueChange={setType}>
          <SelectTrigger className="w-40" aria-label="Filter by type">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All types</SelectItem>
            {Object.entries(VISIT_TYPE_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && <PanelLoading label="Loading visits." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load visits."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {visits && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Visit</th>
                <th className="px-2 py-3">Type</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3">Scheduled for</th>
                <th className="px-2 py-3">Technician</th>
                <th className="px-2 py-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <VisitRow
                  key={v.id}
                  visit={v}
                  onReschedule={() => setRescheduleTarget(v)}
                  onCancel={() => setCancelTarget(v)}
                />
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No visits match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <RescheduleVisitDialog
        visit={rescheduleTarget}
        open={rescheduleTarget !== null}
        onOpenChange={(open) => !open && setRescheduleTarget(null)}
      />
      <CancelVisitDialog
        visit={cancelTarget}
        open={cancelTarget !== null}
        onOpenChange={(open) => !open && setCancelTarget(null)}
      />
    </div>
  );
}

function VisitRow({
  visit: v,
  onReschedule,
  onCancel,
}: {
  visit: AdminVisitListItem;
  onReschedule: () => void;
  onCancel: () => void;
}) {
  const isScheduled = v.status === "SCHEDULED";
  const isCompleted = v.status === "COMPLETED";
  return (
    <tr className="border-t border-border hover:bg-muted/30">
      <td className="px-4 py-3">
        <Link
          to="/admin/visits/$id"
          params={{ id: String(v.id) }}
          className="rounded font-medium text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          Visit #{v.id}
        </Link>
        <div className="text-xs text-muted-foreground">
          Subscriber #{v.subscriberId} · Property #{v.propertyId}
          {isCompleted && v.materialsCostCents != null && (
            <> · Materials {formatCentsCad(v.materialsCostCents)}</>
          )}
        </div>
      </td>
      <td className="px-2 py-3">{VISIT_TYPE_LABEL[v.type] ?? v.type}</td>
      <td className="px-2 py-3">
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            VISIT_STATUS_TONE[v.status] ?? "bg-muted text-muted-foreground",
          )}
        >
          {VISIT_STATUS_LABEL[v.status] ?? v.status}
        </span>
      </td>
      <td className="px-2 py-3">{formatDateTime(v.scheduledFor)}</td>
      <td className="px-2 py-3">
        {isScheduled ? (
          <AssignTechnicianControl visit={v} />
        ) : v.technicianId ? (
          `Tech #${v.technicianId}`
        ) : (
          <span className="text-amber-700">Unassigned</span>
        )}
      </td>
      <td className="px-2 py-3">
        {isScheduled ? (
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onReschedule}>
              Reschedule
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </td>
    </tr>
  );
}

/**
 * Inline technician (re)assignment for a SCHEDULED visit. `technicianUserId`
 * is the technician's `userId` (see `useAdminTechnicians`/`AdminPatchVisitRequest`),
 * not a `technician_profile` id — the select's option values are userIds, and so
 * is `visit.technicianId` (set from the same field by the backend).
 */
function AssignTechnicianControl({ visit }: { visit: AdminVisitListItem }) {
  const { data: technicians } = useAdminTechnicians();
  const mutation = usePatchAdminVisit();
  const [error, setError] = useState<string | null>(null);

  function handleChange(value: string) {
    const technicianUserId = Number(value);
    if (!Number.isFinite(technicianUserId)) return;
    setError(null);
    mutation.mutate(
      { id: visit.id, request: { technicianUserId } },
      {
        onSuccess: () => toast.success("Technician assigned"),
        onError: (err) => setError(describeVisitError(err)),
      },
    );
  }

  return (
    <div>
      <Select
        value={visit.technicianId != null ? String(visit.technicianId) : ""}
        onValueChange={handleChange}
        disabled={mutation.isPending}
      >
        <SelectTrigger
          className="h-8 w-40 text-xs"
          aria-label={`Assign technician for visit #${visit.id}`}
        >
          <SelectValue placeholder="Unassigned" />
        </SelectTrigger>
        <SelectContent>
          {(technicians ?? []).map((t) => (
            <SelectItem key={t.userId} value={String(t.userId)}>
              {[t.firstName, t.lastName].filter(Boolean).join(" ") || `Tech #${t.userId}`}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && (
        <p role="alert" className="mt-1 text-[11px] text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

/**
 * Reschedule dialog for a SCHEDULED visit. Founder's reported problem: the old
 * `<input type="datetime-local">` rendered as a text field showing something like
 * "19-11-2026, 04:21 PM" with a small icon to click before any calendar appeared —
 * "i want it to be a calendar open." This renders `CalendarGrid` (the same grid the
 * Routes page uses, see `@/components/admin/MonthLoadCalendar`) inline and already open,
 * on the visit's own month, with its day preselected, plus a separately labelled time
 * input for the hour/minute — one glance, one click.
 *
 * `day`/`time`/`visibleMonth` are prefilled from the visit's current `scheduledFor`,
 * read as America/Toronto wall-clock parts via `toDatetimeLocalValue` (`@/lib/format`)
 * and split on "T" — deliberately not `new Date(value).toISOString()`, which the runtime
 * parses in the browser's OWN system timezone rather than America/Toronto and would
 * silently shift every visit by the UTC-offset difference for anyone not sitting in
 * Toronto. State starts at today/this month (never blank/`NaN`-producing) so the very
 * first render, before the prefill effect runs, can't hand `CalendarGrid` an invalid
 * month key.
 *
 * Past days are disabled in the grid (`isDayDisabled`) because `PATCH
 * /api/admin/visits/{id}` rejects a past `scheduledFor` with a 400 — the grid discourages
 * picking one, though the authoritative check still happens server-side (to the minute,
 * not just the day) and is surfaced via `describeVisitError` if e.g. today is picked with
 * a time that's already passed by submission.
 */
function RescheduleVisitDialog({
  visit,
  open,
  onOpenChange,
}: {
  visit: AdminVisitListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const todayKey = dayKey(new Date());
  const [day, setDay] = useState(todayKey);
  const [time, setTime] = useState("00:00");
  const [visibleMonth, setVisibleMonth] = useState(() => toMonthKey(todayKey));
  const [error, setError] = useState<string | null>(null);
  const mutation = usePatchAdminVisit();
  const baseId = useId();

  useEffect(() => {
    if (open && visit) {
      const [prefillDay, prefillTime] = toDatetimeLocalValue(visit.scheduledFor).split("T");
      setDay(prefillDay);
      setTime(prefillTime);
      setVisibleMonth(toMonthKey(prefillDay));
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, visit?.id]);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!visit) return;
    setError(null);

    if (!day || !time) {
      setError("Pick a new date and time.");
      return;
    }
    let scheduledFor: string;
    try {
      scheduledFor = fromDatetimeLocalValue(`${day}T${time}`);
    } catch {
      setError("Pick a valid date and time.");
      return;
    }

    mutation.mutate(
      { id: visit.id, request: { scheduledFor } },
      {
        onSuccess: () => {
          toast.success("Visit rescheduled");
          onOpenChange(false);
        },
        onError: (err) => setError(describeVisitError(err)),
      },
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Reschedule visit{visit ? ` #${visit.id}` : ""}</DialogTitle>
          <DialogDescription>
            Pick a new date and time. The visit's services carry over automatically.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} noValidate>
          <fieldset disabled={mutation.isPending} className="space-y-4">
            <legend className="sr-only">New date and time</legend>
            <CalendarGrid
              month={visibleMonth}
              selectedDay={day}
              onSelectDay={setDay}
              onMonthChange={setVisibleMonth}
              isDayDisabled={(d) => d < todayKey}
            />
            <div>
              <Label htmlFor={`${baseId}-time`}>Time</Label>
              <Input
                id={`${baseId}-time`}
                type="time"
                value={time}
                onChange={(e) => setTime(e.target.value)}
                required
                className="mt-1 w-36"
              />
            </div>
          </fieldset>

          {error && (
            <p role="alert" className="mt-3 text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter className="mt-6">
            <DialogClose asChild>
              <Button type="button" variant="outline" disabled={mutation.isPending}>
                Keep current time
              </Button>
            </DialogClose>
            <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              Reschedule
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CancelVisitDialog({
  visit,
  open,
  onOpenChange,
}: {
  visit: AdminVisitListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const mutation = usePatchAdminVisit();

  useEffect(() => {
    if (open) {
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, visit?.id]);

  function handleConfirm() {
    if (!visit) return;
    setError(null);
    mutation.mutate(
      { id: visit.id, request: { status: "CANCELLED" } },
      {
        onSuccess: () => {
          toast.success("Visit cancelled");
          onOpenChange(false);
        },
        onError: (err) => setError(describeVisitError(err)),
      },
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Cancel visit{visit ? ` #${visit.id}` : ""}?</DialogTitle>
          <DialogDescription>This can't be undone from here.</DialogDescription>
        </DialogHeader>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <DialogFooter className="mt-2">
          <DialogClose asChild>
            <Button type="button" variant="outline" disabled={mutation.isPending}>
              Keep visit
            </Button>
          </DialogClose>
          <Button
            type="button"
            variant="destructive"
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
            onClick={handleConfirm}
          >
            {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            Cancel visit
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* -------------------------------------------------------------------------- */
/* Reschedule requests                                                       */
/* -------------------------------------------------------------------------- */

/**
 * PENDING customer reschedule requests, oldest first — shown above the visit
 * filters/table only while there's at least one to work (an empty or still-
 * loading queue renders nothing here, so the page doesn't reserve space for a
 * section that has nothing to say). A load failure still surfaces, since a
 * silent failure here would hide actionable customer requests.
 */
function RescheduleRequestsSection() {
  const { data: requests, isLoading, isError, refetch } = useAdminRescheduleRequests();
  const [confirmTarget, setConfirmTarget] = useState<AdminRescheduleRequestListItem | null>(null);
  const [declineTarget, setDeclineTarget] = useState<AdminRescheduleRequestListItem | null>(null);

  if (isError && !isLoading) {
    return (
      <PanelError
        label="We couldn't load reschedule requests."
        onRetry={() => void refetch()}
        className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
      />
    );
  }

  if (!requests || requests.length === 0) return null;

  return (
    <section
      aria-labelledby="reschedule-requests-h"
      className="mt-6 overflow-hidden rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div className="flex items-center gap-2">
          <CalendarClock className="size-4 text-primary" aria-hidden="true" />
          <h2 id="reschedule-requests-h" className="font-display text-lg font-bold tracking-tight">
            Reschedule requests
          </h2>
        </div>
        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-bold text-primary">
          {requests.length} pending
        </span>
      </header>

      <ul className="divide-y divide-border">
        {requests.map((r) => (
          <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 p-4">
            <div className="min-w-0">
              <p className="font-semibold text-foreground">
                Visit #{r.visitId} · Subscriber #{r.subscriberId}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Requested {formatDateShort(r.createdAt)}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Preferred: {r.preferredDates.map((d) => formatDateTime(d)).join(", ")}
              </p>
            </div>
            <div className="flex shrink-0 gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setDeclineTarget(r)}>
                Decline
              </Button>
              <Button type="button" size="sm" onClick={() => setConfirmTarget(r)}>
                Confirm
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <ConfirmRescheduleDialog
        request={confirmTarget}
        open={confirmTarget !== null}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
      />
      <DeclineRescheduleDialog
        request={declineTarget}
        open={declineTarget !== null}
        onOpenChange={(open) => !open && setDeclineTarget(null)}
      />
    </section>
  );
}

function ConfirmRescheduleDialog({
  request,
  open,
  onOpenChange,
}: {
  request: AdminRescheduleRequestListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [choice, setChoice] = useState("0");
  const [customValue, setCustomValue] = useState("");
  const [adminNote, setAdminNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useConfirmRescheduleRequest();
  const baseId = useId();

  useEffect(() => {
    if (open) {
      setChoice(request && request.preferredDates.length > 0 ? "0" : "other");
      setCustomValue("");
      setAdminNote("");
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, request?.id]);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!request) return;
    setError(null);

    let scheduledFor: string;
    if (choice === "other") {
      if (!customValue) {
        setError("Pick a date and time.");
        return;
      }
      try {
        scheduledFor = fromDatetimeLocalValue(customValue);
      } catch {
        setError("Pick a valid date and time.");
        return;
      }
    } else {
      scheduledFor = request.preferredDates[Number(choice)];
    }

    mutation.mutate(
      {
        id: request.id,
        request: { scheduledFor, adminNote: adminNote.trim() || undefined },
      },
      {
        onSuccess: () => {
          toast.success("Reschedule confirmed");
          onOpenChange(false);
        },
        onError: (err) => setError(describeVisitError(err)),
      },
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            Confirm reschedule{request ? ` for visit #${request.visitId}` : ""}
          </DialogTitle>
          <DialogDescription>
            Pick one of the subscriber's preferred times, or set another.
          </DialogDescription>
        </DialogHeader>

        {request && (
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <fieldset disabled={mutation.isPending} className="space-y-3">
              <legend className="sr-only">New date and time</legend>
              <RadioGroup value={choice} onValueChange={setChoice}>
                {request.preferredDates.map((date, i) => (
                  <div key={date} className="flex items-center gap-2">
                    <RadioGroupItem value={String(i)} id={`${baseId}-opt-${i}`} />
                    <Label htmlFor={`${baseId}-opt-${i}`} className="font-normal">
                      {formatDateTime(date)}
                    </Label>
                  </div>
                ))}
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="other" id={`${baseId}-opt-other`} />
                  <Label htmlFor={`${baseId}-opt-other`} className="font-normal">
                    Another time
                  </Label>
                </div>
              </RadioGroup>

              {choice === "other" && (
                // Deliberately still a plain `<input type="datetime-local">`, not the
                // `CalendarGrid` used in `RescheduleVisitDialog` above. The founder's
                // complaint ("i want it to be a calendar open") was about that dialog
                // specifically — the single, prominent way to reschedule a visit. This
                // field is a secondary, conditionally-revealed fallback inside a denser
                // form that's primarily a radio choice between the subscriber's own
                // preferred times; swapping in a full month grid here would push the
                // radio options and note field down every time it's shown, for a control
                // most admins won't touch (there's usually a preferred time to just pick).
                <div>
                  <Label htmlFor={`${baseId}-custom`} className="sr-only">
                    New date and time
                  </Label>
                  <Input
                    id={`${baseId}-custom`}
                    type="datetime-local"
                    value={customValue}
                    min={toDatetimeLocalValue(new Date().toISOString())}
                    onChange={(e) => setCustomValue(e.target.value)}
                  />
                </div>
              )}

              <div>
                <Label htmlFor={`${baseId}-note`}>Note to subscriber (optional)</Label>
                <Textarea
                  id={`${baseId}-note`}
                  rows={3}
                  value={adminNote}
                  onChange={(e) => setAdminNote(e.target.value)}
                  className="mt-1"
                />
              </div>
            </fieldset>

            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}

            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" disabled={mutation.isPending}>
                  Cancel
                </Button>
              </DialogClose>
              <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
                {mutation.isPending && (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                )}
                Confirm
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function DeclineRescheduleDialog({
  request,
  open,
  onOpenChange,
}: {
  request: AdminRescheduleRequestListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [adminNote, setAdminNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useDeclineRescheduleRequest();
  const baseId = useId();

  useEffect(() => {
    if (open) {
      setAdminNote("");
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, request?.id]);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!request) return;
    const trimmed = adminNote.trim();
    if (!trimmed) {
      setError("A note is required when declining.");
      return;
    }
    setError(null);
    mutation.mutate(
      { id: request.id, adminNote: trimmed },
      {
        onSuccess: () => {
          toast.success("Reschedule declined");
          onOpenChange(false);
        },
        onError: (err) => setError(describeVisitError(err)),
      },
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>
            Decline reschedule{request ? ` for visit #${request.visitId}` : ""}
          </DialogTitle>
          <DialogDescription>
            Let the subscriber know why this can't be accommodated.
          </DialogDescription>
        </DialogHeader>

        {request && (
          <form onSubmit={handleSubmit} noValidate>
            <Label htmlFor={`${baseId}-decline-note`}>Note to subscriber</Label>
            <Textarea
              id={`${baseId}-decline-note`}
              rows={3}
              value={adminNote}
              onChange={(e) => setAdminNote(e.target.value)}
              required
              disabled={mutation.isPending}
              className="mt-1"
            />

            {error && (
              <p role="alert" className="mt-2 text-sm text-destructive">
                {error}
              </p>
            )}

            <DialogFooter className="mt-4">
              <DialogClose asChild>
                <Button type="button" variant="outline" disabled={mutation.isPending}>
                  Cancel
                </Button>
              </DialogClose>
              <Button
                type="submit"
                variant="destructive"
                disabled={mutation.isPending}
                aria-busy={mutation.isPending}
              >
                {mutation.isPending && (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                )}
                Decline
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
