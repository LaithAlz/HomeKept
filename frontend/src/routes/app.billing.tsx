import { useId, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import {
  useSubscription,
  usePortalSession,
  usePauseSubscription,
  useResumeSubscription,
  useCancelSubscription,
  type AppSubscription,
  type SubscriberStatus,
} from "@/lib/account";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { formatCentsCad, formatFullDate } from "@/lib/format";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/billing")({
  head: () => ({
    meta: [{ title: "Billing — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: BillingPage,
});

function BillingPage() {
  const query = useSubscription();
  useSessionExpiredRedirect(query.error);

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Billing</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Your plan, billing cycle, and renewal dates.
      </p>

      <section className="mt-8">
        {query.isLoading ? (
          <div
            className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your billing details.
          </div>
        ) : query.isError ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
            We couldn't load your billing details. Try refreshing the page.
          </p>
        ) : !query.data ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
            No billing information yet.
          </p>
        ) : (
          <>
            <PlanCard subscription={query.data} />
            <ManageSection subscription={query.data} />
          </>
        )}
      </section>
    </div>
  );
}

function PlanCard({ subscription }: { subscription: AppSubscription }) {
  const {
    status,
    planDisplayName,
    billingCycle,
    priceCents,
    foundingRate,
    foundingRateExpiresAt,
    currentPeriodStart,
    currentPeriodEnd,
  } = subscription;

  const hasPrice = typeof priceCents === "number";

  return (
    <div className="rounded-3xl border border-border bg-card p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="text-xs uppercase tracking-wide text-muted-foreground">Current plan</div>
        <StatusBadge status={status} />
      </div>

      <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="font-display text-3xl font-extrabold">
            {planDisplayName ?? "No plan selected yet"}
          </div>
          <div className="mt-1 text-sm text-muted-foreground">
            Billed {billingCycle === "ANNUAL" ? "annually" : "monthly"}
          </div>
        </div>
        {hasPrice && (
          <div className="text-right">
            <div className="font-display text-3xl font-extrabold tabular-nums">
              {formatCentsCad(priceCents)}
            </div>
            <div className="text-xs text-muted-foreground">
              per {billingCycle === "ANNUAL" ? "year" : "month"}
            </div>
          </div>
        )}
      </div>

      {!planDisplayName && (
        <p className="mt-3 text-sm text-muted-foreground">
          Your plan will appear here once checkout is complete.
        </p>
      )}

      {foundingRate && (
        <div className="mt-4 inline-flex flex-wrap items-center gap-2 rounded-full bg-accent/15 px-3 py-1 text-xs font-semibold text-accent">
          Founding rate
          {foundingRateExpiresAt && (
            <span className="font-normal text-muted-foreground">
              locked in through {formatFullDate(foundingRateExpiresAt)}
            </span>
          )}
        </div>
      )}

      {(currentPeriodStart || currentPeriodEnd) && (
        <div className="mt-5 grid gap-4 border-t border-border pt-5 sm:grid-cols-2">
          {currentPeriodStart && (
            <div>
              <div className="text-xs uppercase tracking-wide text-muted-foreground">
                Current period started
              </div>
              <div className="mt-1 text-sm font-medium">{formatFullDate(currentPeriodStart)}</div>
            </div>
          )}
          {currentPeriodEnd && (
            <div>
              <div className="text-xs uppercase tracking-wide text-muted-foreground">
                {status === "CANCELLED" ? "Access ends" : "Renews"}
              </div>
              <div className="mt-1 text-sm font-medium">{formatFullDate(currentPeriodEnd)}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Manage: portal handoff + pause/resume/cancel
// ---------------------------------------------------------------------------

const REASON_MAX_LENGTH = 500;

/**
 * Maps a self-serve billing failure to a safe, pre-written sentence — never the raw
 * backend message. `NO_BILLING_ACCOUNT` (no Stripe subscription yet, e.g. still
 * PENDING_ACTIVATION) and `ILLEGAL_STATE_TRANSITION` (the plan changed in another
 * tab/window since this page loaded) are both expected, recoverable 409s; anything
 * else — network errors, 5xx, an unrecognized code — falls back to a generic retry.
 */
function billingErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.code === "NO_BILLING_ACCOUNT") {
      return "Your billing account isn't set up yet. Contact us and we'll sort it out.";
    }
    if (err.code === "ILLEGAL_STATE_TRANSITION") {
      return "Your plan changed in another window. Refresh to see the latest.";
    }
  }
  return "Something went wrong on our end. Please try again.";
}

function ManageSection({ subscription }: { subscription: AppSubscription }) {
  const { status, currentPeriodEnd } = subscription;
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<"pause" | "cancel">("pause");
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const portalMutation = usePortalSession();
  const pauseMutation = usePauseSubscription();
  const resumeMutation = useResumeSubscription();
  const cancelMutation = useCancelSubscription();

  function handleError(err: unknown) {
    // A changed-elsewhere 409 means the status shown here is already stale —
    // refetch so the controls (and the plan card above) reflect reality.
    if (err instanceof ApiError && err.code === "ILLEGAL_STATE_TRANSITION") {
      void queryClient.invalidateQueries({ queryKey: ["app-subscription"] });
    }
    setActionError(billingErrorMessage(err));
  }

  function handlePortal() {
    setActionError(null);
    portalMutation.mutate(undefined, { onError: handleError });
  }

  function openPauseDialog() {
    pauseMutation.reset();
    setActionError(null);
    setDialogMode("pause");
    setDialogOpen(true);
  }

  function openCancelDialog() {
    cancelMutation.reset();
    setActionError(null);
    setReason("");
    setDialogMode("cancel");
    setDialogOpen(true);
  }

  function closeDialog() {
    if (pauseMutation.isPending || cancelMutation.isPending) return;
    setDialogOpen(false);
  }

  function handleResume() {
    setActionError(null);
    resumeMutation.mutate(undefined, {
      onSuccess: () => toast.success("Subscription resumed."),
      onError: handleError,
    });
  }

  function confirmPause() {
    setActionError(null);
    pauseMutation.mutate(undefined, {
      onSuccess: () => {
        toast.success("Subscription paused.");
        setDialogOpen(false);
      },
      onError: handleError,
    });
  }

  function confirmCancel() {
    setActionError(null);
    cancelMutation.mutate(reason.trim(), {
      onSuccess: () => {
        toast.success("Cancellation scheduled.");
        setDialogOpen(false);
        setReason("");
      },
      onError: handleError,
    });
  }

  // Per the contract: pause requires ACTIVE; cancel is blocked only once already
  // CANCELLED. PAYMENT_ISSUE isn't pause-eligible (the backend 409s on anything but
  // ACTIVE), but a customer with a payment problem still needs a way out, so Cancel
  // (not Pause) is offered there alongside the always-available portal link.
  const canPause = status === "ACTIVE";
  const canCancel = status === "ACTIVE" || status === "PAYMENT_ISSUE";
  const canResume = status === "PAUSED";
  const showsEndDate =
    status === "CANCELLED" &&
    !!currentPeriodEnd &&
    new Date(currentPeriodEnd).getTime() > Date.now();

  return (
    <div className="mt-6 rounded-3xl border border-border bg-card p-6">
      <h2 className="font-display text-lg font-bold">Manage your subscription</h2>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button
          variant="outline"
          size="sm"
          onClick={handlePortal}
          disabled={portalMutation.isPending}
          aria-busy={portalMutation.isPending}
        >
          {portalMutation.isPending && (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          )}
          Update payment method or change plan
        </Button>

        {canPause && (
          <Button variant="secondary" size="sm" onClick={openPauseDialog}>
            Pause subscription
          </Button>
        )}

        {canCancel && (
          <Button variant="destructive" size="sm" onClick={openCancelDialog}>
            Cancel subscription
          </Button>
        )}

        {canResume && (
          <Button
            size="sm"
            onClick={handleResume}
            disabled={resumeMutation.isPending}
            aria-busy={resumeMutation.isPending}
          >
            {resumeMutation.isPending && (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            )}
            Resume subscription
          </Button>
        )}
      </div>

      {showsEndDate && currentPeriodEnd && (
        <p className="mt-4 text-sm text-muted-foreground">
          Ends {formatFullDate(currentPeriodEnd)}
        </p>
      )}

      {actionError && (
        <p role="alert" className="mt-4 text-sm text-destructive">
          {actionError}
        </p>
      )}

      <ManageDialog
        mode={dialogMode}
        open={dialogOpen}
        onOpenChange={(next) => (next ? setDialogOpen(true) : closeDialog())}
        currentPeriodEnd={currentPeriodEnd}
        reason={reason}
        onReasonChange={setReason}
        onConfirm={dialogMode === "cancel" ? confirmCancel : confirmPause}
        pending={dialogMode === "cancel" ? cancelMutation.isPending : pauseMutation.isPending}
        error={actionError}
      />
    </div>
  );
}

interface ManageDialogProps {
  mode: "pause" | "cancel";
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentPeriodEnd?: string;
  reason: string;
  onReasonChange: (value: string) => void;
  onConfirm: () => void;
  pending: boolean;
  error: string | null;
}

/**
 * Shared confirm dialog for both self-serve actions: a plain confirmation for
 * Pause, a confirmation plus a required reason textarea for Cancel. One dialog,
 * switched by `mode`, rather than two near-identical components.
 */
function ManageDialog({
  mode,
  open,
  onOpenChange,
  currentPeriodEnd,
  reason,
  onReasonChange,
  onConfirm,
  pending,
  error,
}: ManageDialogProps) {
  const baseId = useId();
  const titleId = `${baseId}-title`;
  const descId = `${baseId}-desc`;
  const errorId = `${baseId}-error`;
  const reasonId = `${baseId}-reason`;
  const isCancel = mode === "cancel";

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onConfirm();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (pending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent aria-labelledby={titleId} aria-describedby={descId} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle id={titleId}>
            {isCancel ? "Cancel your subscription?" : "Pause your subscription?"}
          </DialogTitle>
          <DialogDescription id={descId}>
            {isCancel
              ? currentPeriodEnd
                ? `Your access continues until your current period ends on ${formatFullDate(currentPeriodEnd)}.`
                : "Your access continues until your current billing period ends."
              : "Visits stop and billing pauses while your subscription is paused. You can resume anytime."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit}>
          {isCancel && (
            <div>
              <Label htmlFor={reasonId}>Why are you cancelling?</Label>
              <Textarea
                id={reasonId}
                value={reason}
                onChange={(e) => onReasonChange(e.target.value.slice(0, REASON_MAX_LENGTH))}
                maxLength={REASON_MAX_LENGTH}
                required
                disabled={pending}
                rows={3}
                aria-describedby={error ? errorId : undefined}
                className="mt-1"
              />
              <p className="mt-1 text-right text-xs text-muted-foreground">
                {reason.length}/{REASON_MAX_LENGTH}
              </p>
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
              disabled={pending}
              aria-busy={pending}
            >
              {pending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              {isCancel ? "Cancel subscription" : "Pause subscription"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const STATUS_STYLES: Record<SubscriberStatus, { className: string; label: string }> = {
  PENDING_ACTIVATION: { className: "bg-muted text-muted-foreground", label: "Pending activation" },
  ACTIVE: { className: "bg-emerald-100 text-emerald-800", label: "Active" },
  PAUSED: { className: "bg-amber-100 text-amber-800", label: "Paused" },
  PAYMENT_ISSUE: { className: "bg-destructive/15 text-destructive", label: "Payment issue" },
  CANCELLED: { className: "bg-muted text-muted-foreground", label: "Cancelled" },
};

function StatusBadge({ status }: { status: SubscriberStatus }) {
  const style = STATUS_STYLES[status];
  return (
    <span className={cn("rounded-full px-2.5 py-0.5 text-xs font-semibold", style.className)}>
      {style.label}
    </span>
  );
}
