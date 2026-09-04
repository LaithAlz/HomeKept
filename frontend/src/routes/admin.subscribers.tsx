import { useId, useMemo, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Search, Loader2, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { formatCentsCad, formatDateShort, formatDateTime } from "@/lib/format";
import { ApiError } from "@/lib/api";
import {
  useAdminCancelSubscription,
  useAdminPauseSubscription,
  useAdminResumeSubscription,
  useAdminSubscriber,
  useAdminSubscriberEvents,
  useAdminSubscribers,
  useUpdatePropertySku,
  STATUS_LABEL,
  STATUS_TONE,
  PLAN_LABEL,
  type AdminSubscriberDetail,
  type AdminSubscriberPropertySummary,
  type AdminUpdateSkuRequest,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

const searchSchema = z.object({
  id: z.coerce.number().int().positive().optional(),
});

export const Route = createFileRoute("/admin/subscribers")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [{ title: "Subscribers — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: SubscribersPage,
});

function SubscribersPage() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 100 });
  const { id: deepLinkedId } = Route.useSearch();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [plan, setPlan] = useState<string>("all");
  // Deep-linked from the dashboard (`/admin/subscribers?id=N`, see admin.index.tsx):
  // opens that subscriber's detail sheet on load. Read once at mount — the sheet's
  // own onOpenChange handles closing, and re-syncing on every search change would
  // reopen a sheet the admin just dismissed if the URL still carries the id.
  const [openId, setOpenId] = useState<number | null>(deepLinkedId ?? null);

  const rows = useMemo(() => {
    if (!subscribers) return [];
    return subscribers.filter((s) => {
      if (status !== "all" && s.status !== status) return false;
      if (plan !== "all" && s.planCode !== plan) return false;
      if (q && !String(s.id).includes(q.trim())) return false;
      return true;
    });
  }, [subscribers, q, status, plan]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Subscribers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {subscribers
              ? `${rows.length} of ${subscribers.length} households`
              : "Loading households…"}
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-56">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="subscriber-search" className="sr-only">
            Search by subscriber ID
          </label>
          <Input
            id="subscriber-search"
            placeholder="Search by ID"
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
            {Object.entries(STATUS_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={plan} onValueChange={setPlan}>
          <SelectTrigger className="w-40" aria-label="Filter by plan">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All plans</SelectItem>
            {Object.entries(PLAN_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && <PanelLoading label="Loading subscribers." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load subscribers."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {subscribers && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-2 py-3">Plan</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3 text-right">MRR</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id} className="border-t border-border hover:bg-muted/30">
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setOpenId(s.id)}
                      className="font-medium text-foreground hover:underline"
                    >
                      #{s.id}
                    </button>
                  </td>
                  <td className="px-2 py-3">
                    {s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "—"}
                  </td>
                  <td className="px-2 py-3">
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-xs font-medium",
                        STATUS_TONE[s.status] ?? "bg-muted text-muted-foreground",
                      )}
                    >
                      {STATUS_LABEL[s.status] ?? s.status}
                    </span>
                  </td>
                  <td className="px-2 py-3 text-right tabular-nums">
                    {formatCentsCad(s.mrrCents)}
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No subscribers match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <SubscriberDetailSheet id={openId} onOpenChange={(open) => !open && setOpenId(null)} />
    </div>
  );
}

function SubscriberDetailSheet({
  id,
  onOpenChange,
}: {
  id: number | null;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: detail, isLoading, isError, refetch } = useAdminSubscriber(id);

  return (
    <Sheet open={id !== null} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-md">
        {/* SheetTitle is always rendered (Radix requires an accessible name for the
            dialog) — it just doesn't have a subscriber id to show until `detail` loads. */}
        <SheetHeader>
          <SheetTitle>{detail ? `Subscriber #${detail.id}` : "Subscriber detail"}</SheetTitle>
          <SheetDescription>
            {detail
              ? detail.property
                ? `${detail.property.streetAddress}, ${detail.property.city}`
                : "No property linked yet."
              : "Loading subscriber detail."}
          </SheetDescription>
        </SheetHeader>

        {isLoading && <PanelLoading label="Loading subscriber." className="mt-6" />}

        {isError && !isLoading && (
          <div
            role="alert"
            className="mt-6 flex flex-col items-start gap-2 text-sm text-destructive"
          >
            <span>We couldn't load this subscriber.</span>
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              Try again
            </Button>
          </div>
        )}

        {detail && (
          <div className="mt-6 space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-3">
              <DetailTile label="Plan">
                {detail.planCode
                  ? (PLAN_LABEL[detail.planCode] ?? detail.planCode)
                  : "Not chosen yet"}
              </DetailTile>
              <DetailTile label="MRR">{formatCentsCad(detail.mrrCents)}</DetailTile>
              <DetailTile label="Status">{STATUS_LABEL[detail.status] ?? detail.status}</DetailTile>
              <DetailTile label="Billing cycle">
                {detail.billingCycle === "ANNUAL" ? "Annual" : "Monthly"}
              </DetailTile>
              <DetailTile label="Started">
                {detail.startedAt ? formatDateShort(detail.startedAt) : "—"}
              </DetailTile>
              {detail.pausedAt && (
                <DetailTile label="Paused">{formatDateShort(detail.pausedAt)}</DetailTile>
              )}
              {detail.cancelledAt && (
                <DetailTile label="Cancelled">{formatDateShort(detail.cancelledAt)}</DetailTile>
              )}
              {detail.currentPeriodStart && (
                <DetailTile label="Current period started">
                  {formatDateShort(detail.currentPeriodStart)}
                </DetailTile>
              )}
              {detail.currentPeriodEnd && (
                <DetailTile label="Current period ends">
                  {formatDateShort(detail.currentPeriodEnd)}
                </DetailTile>
              )}
              {detail.stripeCustomerId && (
                <DetailTile label="Stripe customer">
                  <StripeLink
                    href={`https://dashboard.stripe.com/customers/${detail.stripeCustomerId}`}
                    id={detail.stripeCustomerId}
                  />
                </DetailTile>
              )}
              {detail.stripeSubscriptionId && (
                <DetailTile label="Stripe subscription">
                  <StripeLink
                    href={`https://dashboard.stripe.com/subscriptions/${detail.stripeSubscriptionId}`}
                    id={detail.stripeSubscriptionId}
                  />
                </DetailTile>
              )}
            </div>

            <SubscriptionSection detail={detail} />

            <ActivitySection subscriberId={detail.id} />

            {detail.property && (
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Property</div>
                <div className="font-medium">
                  {detail.property.streetAddress}, {detail.property.city}{" "}
                  {detail.property.postalCode}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  {detail.property.propertyType ?? "Type not set"} ·{" "}
                  {detail.property.hasAccessNotes
                    ? "Access notes on file"
                    : "No access notes on file"}
                </div>
              </div>
            )}

            {detail.property?.propertyId && (
              <PropertySkuForm key={detail.property.propertyId} property={detail.property} />
            )}

            <div className="rounded-xl border border-dashed border-border p-3 text-xs text-muted-foreground">
              Visit history isn't available yet.
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

/**
 * Maps a subscription-action failure to a safe, pre-written sentence — never the raw
 * backend message. `NO_BILLING_ACCOUNT` (no Stripe subscription yet) and
 * `ILLEGAL_STATE_TRANSITION` (the subscription changed since this sheet loaded) are
 * both expected, recoverable 409s; anything else falls back to a generic retry,
 * mirroring `billingErrorMessage` in `routes/app.billing.tsx`.
 */
function describeSubscriptionActionError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.code === "NO_BILLING_ACCOUNT") {
      return "This subscriber has no Stripe subscription yet.";
    }
    if (err.code === "ILLEGAL_STATE_TRANSITION") {
      return "The subscription changed. Refresh and try again.";
    }
  }
  return "Something went wrong on our end. Please try again.";
}

const CANCEL_REASON_MAX_LENGTH = 500;

type SubscriptionDialogMode = "pause" | "resume" | "cancel";

/**
 * Staff-initiated pause/resume/cancel controls, gated by the subscriber's current
 * status per the state machine: ACTIVE can pause or cancel, PAUSED can resume or
 * cancel, PAYMENT_ISSUE can only cancel (no pause-from-payment-issue transition),
 * CANCELLED and PENDING_ACTIVATION show no controls.
 */
function SubscriptionSection({ detail }: { detail: AdminSubscriberDetail }) {
  const queryClient = useQueryClient();
  const [dialogMode, setDialogMode] = useState<SubscriptionDialogMode | null>(null);
  const [reason, setReason] = useState("");
  const [immediately, setImmediately] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pauseMutation = useAdminPauseSubscription(detail.id);
  const resumeMutation = useAdminResumeSubscription(detail.id);
  const cancelMutation = useAdminCancelSubscription(detail.id);

  function handleError(err: unknown) {
    // A changed-elsewhere 409 means the status shown here is already stale —
    // refetch so the controls reflect reality.
    if (err instanceof ApiError && err.code === "ILLEGAL_STATE_TRANSITION") {
      void queryClient.invalidateQueries({ queryKey: ["admin", "subscriber", detail.id] });
    }
    setError(describeSubscriptionActionError(err));
  }

  function openDialog(mode: SubscriptionDialogMode) {
    pauseMutation.reset();
    resumeMutation.reset();
    cancelMutation.reset();
    setError(null);
    setReason("");
    setImmediately(false);
    setDialogMode(mode);
  }

  const pending = pauseMutation.isPending || resumeMutation.isPending || cancelMutation.isPending;

  function closeDialog() {
    if (pending) return;
    setDialogMode(null);
  }

  function confirmPause() {
    setError(null);
    pauseMutation.mutate(undefined, {
      onSuccess: () => {
        toast.success("Subscription paused.");
        setDialogMode(null);
      },
      onError: handleError,
    });
  }

  function confirmResume() {
    setError(null);
    resumeMutation.mutate(undefined, {
      onSuccess: () => {
        toast.success("Subscription resumed.");
        setDialogMode(null);
      },
      onError: handleError,
    });
  }

  function confirmCancel() {
    setError(null);
    cancelMutation.mutate(
      { reason: reason.trim(), immediately },
      {
        onSuccess: () => {
          toast.success("Cancellation requested.");
          setDialogMode(null);
          setReason("");
          setImmediately(false);
        },
        onError: handleError,
      },
    );
  }

  const showPause = detail.status === "ACTIVE";
  const showResume = detail.status === "PAUSED";
  const showCancel =
    detail.status === "ACTIVE" || detail.status === "PAUSED" || detail.status === "PAYMENT_ISSUE";
  const showNoBillingNote = detail.status === "PENDING_ACTIVATION";

  return (
    <div className="rounded-xl border border-border p-3">
      <h3 className="font-display text-sm font-bold">Subscription</h3>

      {(showPause || showResume || showCancel) && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {showPause && (
            <Button size="sm" variant="secondary" onClick={() => openDialog("pause")}>
              Pause
            </Button>
          )}
          {showResume && (
            <Button size="sm" onClick={() => openDialog("resume")}>
              Resume
            </Button>
          )}
          {showCancel && (
            <Button size="sm" variant="destructive" onClick={() => openDialog("cancel")}>
              Cancel
            </Button>
          )}
        </div>
      )}

      {showNoBillingNote && (
        <p className="mt-2 text-xs text-muted-foreground">
          No billing yet. The customer hasn't checked out.
        </p>
      )}

      {showCancel && (
        <p className="mt-2 text-xs text-muted-foreground">
          The customer gets the cancellation email when Stripe ends the subscription.
        </p>
      )}

      {error && !dialogMode && (
        <p role="alert" className="mt-2 text-xs text-destructive">
          {error}
        </p>
      )}

      <SubscriptionActionDialog
        mode={dialogMode}
        onOpenChange={(open) => !open && closeDialog()}
        currentPeriodEnd={detail.currentPeriodEnd}
        reason={reason}
        onReasonChange={setReason}
        immediately={immediately}
        onImmediatelyChange={setImmediately}
        onConfirm={
          dialogMode === "cancel"
            ? confirmCancel
            : dialogMode === "resume"
              ? confirmResume
              : confirmPause
        }
        pending={pending}
        error={error}
      />
    </div>
  );
}

interface SubscriptionActionDialogProps {
  mode: SubscriptionDialogMode | null;
  onOpenChange: (open: boolean) => void;
  currentPeriodEnd?: string;
  reason: string;
  onReasonChange: (value: string) => void;
  immediately: boolean;
  onImmediatelyChange: (value: boolean) => void;
  onConfirm: () => void;
  pending: boolean;
  error: string | null;
}

/** Shared confirm dialog for the pause/resume/cancel actions, switched by `mode`. */
function SubscriptionActionDialog({
  mode,
  onOpenChange,
  currentPeriodEnd,
  reason,
  onReasonChange,
  immediately,
  onImmediatelyChange,
  onConfirm,
  pending,
  error,
}: SubscriptionActionDialogProps) {
  const baseId = useId();
  const titleId = `${baseId}-title`;
  const descId = `${baseId}-desc`;
  const errorId = `${baseId}-error`;
  const reasonId = `${baseId}-reason`;
  const immediatelyId = `${baseId}-immediately`;

  const isCancel = mode === "cancel";
  const isPause = mode === "pause";

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onConfirm();
  }

  return (
    <Dialog
      open={mode !== null}
      onOpenChange={(next) => {
        if (pending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent aria-labelledby={titleId} aria-describedby={descId} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle id={titleId}>
            {isCancel
              ? "Cancel this subscription?"
              : isPause
                ? "Pause this subscription?"
                : "Resume this subscription?"}
          </DialogTitle>
          <DialogDescription id={descId}>
            {isCancel
              ? "This starts the cancellation. The subscriber keeps a record of who requested it and why."
              : isPause
                ? "Visits stop and billing pauses while the subscription is paused."
                : "Visits and billing resume for this subscriber."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit}>
          {isCancel && (
            <div className="space-y-3">
              <div>
                <Label htmlFor={reasonId}>Reason</Label>
                <Textarea
                  id={reasonId}
                  value={reason}
                  onChange={(e) =>
                    onReasonChange(e.target.value.slice(0, CANCEL_REASON_MAX_LENGTH))
                  }
                  maxLength={CANCEL_REASON_MAX_LENGTH}
                  required
                  disabled={pending}
                  rows={3}
                  aria-describedby={error ? errorId : undefined}
                  className="mt-1"
                />
                <p className="mt-1 text-right text-xs text-muted-foreground">
                  {reason.length}/{CANCEL_REASON_MAX_LENGTH}
                </p>
              </div>

              <div className="flex items-start gap-2">
                <Checkbox
                  id={immediatelyId}
                  checked={immediately}
                  onCheckedChange={(checked) => onImmediatelyChange(checked === true)}
                  disabled={pending}
                  className="mt-0.5"
                />
                <Label htmlFor={immediatelyId} className="text-sm font-normal">
                  Cancel immediately (ends access now; otherwise access runs to{" "}
                  {currentPeriodEnd
                    ? formatDateShort(currentPeriodEnd)
                    : "the end of the current period"}
                  )
                </Label>
              </div>
            </div>
          )}

          {error && (
            <p id={errorId} role="alert" className="mt-2 text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter className="mt-6">
            <DialogClose asChild>
              <Button type="button" variant="outline" disabled={pending}>
                Never mind
              </Button>
            </DialogClose>
            <Button
              type="submit"
              variant={isCancel ? "destructive" : "default"}
              disabled={pending || (isCancel && reason.trim().length === 0)}
              aria-busy={pending}
            >
              {pending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              {isCancel
                ? "Cancel subscription"
                : isPause
                  ? "Pause subscription"
                  : "Resume subscription"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const EVENT_SOURCE_LABEL: Record<string, string> = {
  STRIPE: "Stripe",
  MANUAL: "Manual",
};

/** `CANCELLATION_REQUESTED` -> "Cancellation requested": lowercase, underscores to spaces, capitalize first letter. */
function humanizeEventType(type: string): string {
  const lower = type.toLowerCase().replace(/_/g, " ");
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function ActivitySection({ subscriberId }: { subscriberId: number }) {
  const { data: events, isLoading, isError, refetch } = useAdminSubscriberEvents(subscriberId);

  return (
    <div className="rounded-xl border border-border p-3">
      <h3 className="font-display text-sm font-bold">Activity</h3>

      {isLoading && <PanelLoading label="Loading activity." className="mt-3 p-0" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load activity."
          onRetry={() => void refetch()}
          className="mt-3 p-0"
        />
      )}

      {events && events.length === 0 && (
        <p className="mt-2 text-xs text-muted-foreground">No activity yet.</p>
      )}

      {events && events.length > 0 && (
        <ul className="mt-3 space-y-3">
          {events.map((event) => (
            <li key={event.id} className="text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">{humanizeEventType(event.type)}</span>
                <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {EVENT_SOURCE_LABEL[event.source] ?? event.source}
                </span>
              </div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                {formatDateTime(event.occurredAt)}
              </div>
              {event.note && <p className="mt-1 text-xs text-muted-foreground">{event.note}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

type FlushEligibility = "unknown" | "yes" | "no";

function toFlushEligibility(value: boolean | null): FlushEligibility {
  if (value === null) return "unknown";
  return value ? "yes" : "no";
}

/** `null` = empty; `"invalid"` = present but not a whole number 0–100. */
function parseAgeYears(raw: string): number | null | "invalid" {
  const trimmed = raw.trim();
  if (trimmed === "") return null;
  const n = Number(trimmed);
  if (!Number.isInteger(n) || n < 0 || n > 100) return "invalid";
  return n;
}

/** True when `current` is non-empty and differs from the loaded value (both trimmed). */
function fieldChanged(current: string, original: string): boolean {
  const trimmed = current.trim();
  return trimmed !== "" && trimmed !== original;
}

/**
 * Surfaces the backend's error honestly rather than a generic fallback:
 * `VALIDATION_FAILED` carries per-field messages (only `waterHeaterAgeYears` can
 * fail validation here — see `AdminUpdateSkuRequest.java`), and `PropertyNotFoundException`
 * maps to a plain "Property not found" 404 message that's safe to show verbatim
 * (same "pre-canned backend message" pattern as `RescheduleDialog`).
 */
function describeSkuError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 400 && err.fields && Object.keys(err.fields).length > 0) {
      return Object.values(err.fields).join(" ");
    }
    return err.message;
  }
  return "That didn't go through. Please try again.";
}

/**
 * The property SKU sheet (#56): technician-prep data captured on the walk-through
 * and refined over subsequent visits (docs/pricing-and-visits.md §Materials). Keyed
 * by `property.propertyId` from the parent so switching between subscribers in the
 * sheet remounts this form with fresh local state instead of carrying over stale
 * edits from a previously viewed property.
 */
function PropertySkuForm({ property }: { property: AdminSubscriberPropertySummary }) {
  const [hvacFilterSizes, setHvacFilterSizes] = useState(property.hvacFilterSizes ?? "");
  const [smokeCoDetectorModels, setSmokeCoDetectorModels] = useState(
    property.smokeCoDetectorModels ?? "",
  );
  const [humidifierModel, setHumidifierModel] = useState(property.humidifierModel ?? "");
  const [waterHeaterAgeYears, setWaterHeaterAgeYears] = useState(
    property.waterHeaterAgeYears !== null ? String(property.waterHeaterAgeYears) : "",
  );
  const [flushEligible, setFlushEligible] = useState<FlushEligibility>(
    toFlushEligibility(property.waterHeaterFlushEligible),
  );
  const [error, setError] = useState<string | null>(null);

  const mutation = useUpdatePropertySku(property.propertyId);
  const baseId = useId();
  const errorId = `${baseId}-sku-error`;

  const originalAgeStr =
    property.waterHeaterAgeYears !== null ? String(property.waterHeaterAgeYears) : "";
  const originalFlush = toFlushEligibility(property.waterHeaterFlushEligible);

  // Whether the form has anything worth sending: a field is only "changed" if it's
  // non-empty and differs from the loaded value — the backend treats an omitted or
  // null field as "leave unchanged" (api-contract.md line 198), so an emptied field
  // is deliberately excluded rather than sent as a clear. Kept independent of the
  // water-heater-age range check so an invalid-but-edited age still enables Save and
  // surfaces the validation message on submit, instead of the button just doing nothing.
  const hasChanges =
    fieldChanged(hvacFilterSizes, property.hvacFilterSizes ?? "") ||
    fieldChanged(smokeCoDetectorModels, property.smokeCoDetectorModels ?? "") ||
    fieldChanged(humidifierModel, property.humidifierModel ?? "") ||
    fieldChanged(waterHeaterAgeYears, originalAgeStr) ||
    (flushEligible !== "unknown" && flushEligible !== originalFlush);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const ageYears = parseAgeYears(waterHeaterAgeYears);
    if (ageYears === "invalid") {
      setError("Water heater age must be a whole number of years, between 0 and 100.");
      return;
    }

    const request: AdminUpdateSkuRequest = {};
    if (fieldChanged(hvacFilterSizes, property.hvacFilterSizes ?? "")) {
      request.hvacFilterSizes = hvacFilterSizes.trim();
    }
    if (fieldChanged(smokeCoDetectorModels, property.smokeCoDetectorModels ?? "")) {
      request.smokeCoDetectorModels = smokeCoDetectorModels.trim();
    }
    if (fieldChanged(humidifierModel, property.humidifierModel ?? "")) {
      request.humidifierModel = humidifierModel.trim();
    }
    if (typeof ageYears === "number" && ageYears !== property.waterHeaterAgeYears) {
      request.waterHeaterAgeYears = ageYears;
    }
    if (flushEligible !== "unknown") {
      const value = flushEligible === "yes";
      if (value !== property.waterHeaterFlushEligible) {
        request.waterHeaterFlushEligible = value;
      }
    }

    // Nothing to send (e.g. every edit was cleared back to blank) — leave silently,
    // no toast, matching "blank fields are left unchanged".
    if (Object.keys(request).length === 0) return;

    mutation.mutate(request, {
      onSuccess: () => {
        toast.success("SKU sheet saved");
      },
      onError: (err) => setError(describeSkuError(err)),
    });
  }

  return (
    <div className="rounded-xl border border-border p-3">
      <h3 className="font-display text-sm font-bold">SKU sheet</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">
        Technician prep captured on the walk-through and refined over later visits. Blank fields are
        left unchanged.
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-3 space-y-3">
        <fieldset disabled={mutation.isPending} className="space-y-3">
          <legend className="sr-only">SKU sheet fields</legend>

          <div>
            <Label htmlFor={`${baseId}-hvac`}>HVAC filter sizes</Label>
            <Input
              id={`${baseId}-hvac`}
              value={hvacFilterSizes}
              onChange={(e) => setHvacFilterSizes(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!hvacFilterSizes && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div>
            <Label htmlFor={`${baseId}-detectors`}>Smoke/CO detector models</Label>
            <Input
              id={`${baseId}-detectors`}
              value={smokeCoDetectorModels}
              onChange={(e) => setSmokeCoDetectorModels(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!smokeCoDetectorModels && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div>
            <Label htmlFor={`${baseId}-humidifier`}>Humidifier model</Label>
            <Input
              id={`${baseId}-humidifier`}
              value={humidifierModel}
              onChange={(e) => setHumidifierModel(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!humidifierModel && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor={`${baseId}-wh-age`}>Water heater age (years)</Label>
              <Input
                id={`${baseId}-wh-age`}
                type="number"
                inputMode="numeric"
                min={0}
                max={100}
                step={1}
                value={waterHeaterAgeYears}
                onChange={(e) => setWaterHeaterAgeYears(e.target.value)}
                className="mt-1"
                aria-describedby={error ? errorId : undefined}
              />
              {!waterHeaterAgeYears && (
                <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
              )}
            </div>

            <div>
              <Label htmlFor={`${baseId}-wh-flush`}>Water heater flush eligible</Label>
              <Select
                value={flushEligible}
                onValueChange={(v) => setFlushEligible(v as FlushEligibility)}
                disabled={mutation.isPending}
              >
                <SelectTrigger id={`${baseId}-wh-flush`} className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="unknown">Not captured yet</SelectItem>
                  <SelectItem value="yes">Yes</SelectItem>
                  <SelectItem value="no">No</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </fieldset>

        {error && (
          <p id={errorId} role="alert" className="text-xs text-destructive">
            {error}
          </p>
        )}

        <div className="flex justify-end">
          <Button
            type="submit"
            size="sm"
            disabled={mutation.isPending || !hasChanges}
            aria-busy={mutation.isPending}
          >
            {mutation.isPending && <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />}
            Save SKU sheet
          </Button>
        </div>
      </form>
    </div>
  );
}

function DetailTile({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-border p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="font-medium">{children}</div>
    </div>
  );
}

/** Truncated Stripe object id that opens the matching Stripe Dashboard page in a new tab. */
function StripeLink({ href, id }: { href: string; id: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="inline-flex items-center gap-1 truncate text-primary underline-offset-2 hover:underline"
    >
      <span className="truncate">{id}</span>
      <ExternalLink className="h-3 w-3 shrink-0" aria-hidden="true" />
      <span className="sr-only">(opens in Stripe Dashboard, new tab)</span>
    </a>
  );
}
