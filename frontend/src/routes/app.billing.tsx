import { useId, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, CreditCard, ExternalLink, Loader2 } from "lucide-react";
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
import { StatusPanel } from "@/components/app/StatusPanel";
import { ApiError } from "@/lib/api";
import {
  useSubscription,
  usePortalSession,
  useCancelSubscription,
  usePaymentMethod,
  useInvoices,
  type AppSubscription,
  type AppPaymentMethod,
  type AppInvoice,
  type SubscriberStatus,
} from "@/lib/account";
import { useCatalogPlans } from "@/lib/catalog";
import { PLANS } from "@/lib/plans";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { formatCentsExact, formatFullDate } from "@/lib/format";
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
        What you're paying for, what's included, and how to change or cancel it.
      </p>

      <section className="mt-8">
        {query.isLoading ? (
          <StatusPanel>
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your billing details.
          </StatusPanel>
        ) : query.isError ? (
          <StatusPanel>We couldn't load your billing details. Try refreshing the page.</StatusPanel>
        ) : !query.data ? (
          <StatusPanel>No billing information yet.</StatusPanel>
        ) : (
          <div className="space-y-10">
            <MembershipSection subscription={query.data} />
            <NextChargeSection subscription={query.data} />
            <PaymentMethodSection />
            <InvoicesSection />
            <ManageSection subscription={query.data} />
          </div>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 1. Your membership: plan, what it includes, status
// ---------------------------------------------------------------------------

function MembershipSection({ subscription }: { subscription: AppSubscription }) {
  const { status, planCode, planDisplayName, billingCycle, currentPeriodEnd } = subscription;
  const catalogQuery = useCatalogPlans();
  const catalogPlan = catalogQuery.data?.find((p) => p.code === planCode);
  // yourListTime isn't on the catalog endpoint (visits/picks allowances only) — it's
  // sourced from the same vetted plan copy (docs/pricing-and-visits.md) that backs the
  // public landing/plans pages, matched by plan code.
  const marketingPlan = PLANS.find((p) => p.id === planCode?.toLowerCase());

  return (
    <section aria-labelledby="membership-heading">
      <h2
        id="membership-heading"
        className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
      >
        Your membership
      </h2>
      <div className="mt-3 rounded-3xl border border-border bg-card p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="font-display text-3xl font-extrabold">
              {planDisplayName ?? "No plan selected yet"}
            </div>
            {planDisplayName && (
              <div className="mt-1 text-sm text-muted-foreground">
                Billed {billingCycle === "ANNUAL" ? "annually" : "monthly"}
              </div>
            )}
          </div>
          <StatusBadge status={status} />
        </div>

        {!planDisplayName && (
          <p className="mt-3 text-sm text-muted-foreground">
            Your plan will appear here once checkout is complete.
          </p>
        )}

        {status === "CANCELLED" && (
          <p className="mt-4 rounded-2xl bg-muted/40 px-4 py-3 text-sm text-foreground">
            Your membership is cancelled.{" "}
            {currentPeriodEnd
              ? `Your access ends ${formatFullDate(currentPeriodEnd)}.`
              : "Your access has already ended."}
          </p>
        )}

        {catalogPlan && (
          <ul
            className="mt-5 space-y-2 border-t border-border pt-5 text-sm text-foreground/90"
            role="list"
            aria-label="What's included in your plan"
          >
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
              {catalogPlan.visitsPerYear} visits a year
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
              {catalogPlan.includedPicksPerYear} included picks a year (up to{" "}
              {catalogPlan.maxPremiumPicksPerYear} Premium)
            </li>
            {marketingPlan && (
              <li className="flex items-start gap-2">
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                {marketingPlan.yourListTime}
              </li>
            )}
          </ul>
        )}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// 2. Next charge: one sentence, exact amount + date
// ---------------------------------------------------------------------------

function NextChargeSection({ subscription }: { subscription: AppSubscription }) {
  const { status, priceCents, currentPeriodEnd } = subscription;
  const hasPrice = typeof priceCents === "number";

  let message: string;
  if (status === "CANCELLED") {
    message = currentPeriodEnd
      ? `Your access ends on ${formatFullDate(currentPeriodEnd)}. You won't be charged again.`
      : "You won't be charged again.";
  } else if (status === "PAUSED") {
    message = "Your membership is paused, so you won't be charged. Contact us to pick it back up.";
  } else if (hasPrice && currentPeriodEnd) {
    // Prices are quoted before tax and Stripe adds HST at invoice time, so the plan
    // price alone is not the total. Say so rather than implying it is.
    message = `Your next charge is ${formatCentsExact(priceCents)} plus HST on ${formatFullDate(currentPeriodEnd)}.`;
  } else {
    message = "Your next charge will appear here once your first billing cycle begins.";
  }

  return (
    <section aria-labelledby="next-charge-heading">
      <h2
        id="next-charge-heading"
        className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
      >
        Next charge
      </h2>
      <div className="mt-3 rounded-3xl border border-border bg-card p-6">
        <p className="text-sm text-foreground">{message}</p>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// 3. Payment method: brand/last4/expiry, expiry warning, portal handoff
// ---------------------------------------------------------------------------

/** True once a card is within 2 calendar months of its printed expiry (or already past it). */
function isCardExpiringSoon(expMonth: number, expYear: number, now: Date = new Date()): boolean {
  const monthsUntilExpiry = (expYear - now.getFullYear()) * 12 + (expMonth - (now.getMonth() + 1));
  return monthsUntilExpiry <= 2;
}

function PaymentMethodSection() {
  const query = usePaymentMethod();
  const portalMutation = usePortalSession();
  const [error, setError] = useState<string | null>(null);

  function handleOpenPortal() {
    setError(null);
    portalMutation.mutate(undefined, { onError: (err) => setError(billingErrorMessage(err)) });
  }

  return (
    <section aria-labelledby="payment-method-heading">
      <h2
        id="payment-method-heading"
        className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
      >
        Payment method
      </h2>
      <div className="mt-3 rounded-3xl border border-border bg-card p-6">
        {query.isLoading ? (
          <p
            className="flex items-center gap-3 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your payment method.
          </p>
        ) : query.isError ? (
          <p className="text-sm text-muted-foreground">We couldn't load your payment method.</p>
        ) : !query.data ? (
          <p className="text-sm text-muted-foreground">No payment method on file.</p>
        ) : (
          <PaymentMethodDetails method={query.data} />
        )}

        <Button
          variant="outline"
          size="sm"
          className="mt-4"
          onClick={handleOpenPortal}
          disabled={portalMutation.isPending || query.isLoading}
          aria-busy={portalMutation.isPending}
        >
          {portalMutation.isPending && (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          )}
          {query.data ? "Update payment method" : "Add a payment method"}
        </Button>

        {error && (
          <p role="alert" className="mt-3 text-sm text-destructive">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}

function PaymentMethodDetails({ method }: { method: AppPaymentMethod }) {
  const expiringSoon = isCardExpiringSoon(method.expMonth, method.expYear);
  const brand = method.brand
    ? method.brand.charAt(0).toUpperCase() + method.brand.slice(1)
    : "Card";
  const expLabel = `${String(method.expMonth).padStart(2, "0")}/${method.expYear}`;

  return (
    <div className="flex items-start gap-3">
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
      >
        <CreditCard className="size-5" />
      </span>
      <div>
        <p className="text-sm font-semibold text-foreground">
          {brand} ending in {method.last4}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">Expires {expLabel}</p>
        {expiringSoon && (
          <p className="mt-2 flex items-center gap-1.5 text-xs font-medium text-warning">
            <AlertTriangle className="size-3.5 shrink-0" aria-hidden="true" />
            This card expires soon. Update it to avoid a missed payment.
          </p>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 4. Invoices: compact table, newest first
// ---------------------------------------------------------------------------

const INVOICE_STATUS_STYLES: Record<string, string> = {
  paid: "bg-success/15 text-success",
  open: "bg-warning/15 text-warning",
  uncollectible: "bg-destructive/15 text-destructive",
  void: "bg-muted text-muted-foreground",
  draft: "bg-muted text-muted-foreground",
};

function InvoicesSection() {
  const query = useInvoices();
  const invoices = query.data ?? [];

  return (
    <section aria-labelledby="invoices-heading">
      <h2
        id="invoices-heading"
        className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
      >
        Invoices
      </h2>
      <div className="mt-3">
        {query.isLoading ? (
          <StatusPanel>
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your invoices.
          </StatusPanel>
        ) : query.isError ? (
          <StatusPanel>We couldn't load your invoices. Try refreshing the page.</StatusPanel>
        ) : invoices.length === 0 ? (
          <StatusPanel>No invoices yet.</StatusPanel>
        ) : (
          <div className="overflow-hidden rounded-3xl border border-border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm" aria-label="Invoices">
                <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Date</th>
                    <th className="px-2 py-3">Amount</th>
                    <th className="px-2 py-3">Status</th>
                    <th className="px-2 py-3 text-right">Receipt</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((invoice) => (
                    <InvoiceRow key={invoice.id} invoice={invoice} />
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function InvoiceRow({ invoice }: { invoice: AppInvoice }) {
  const receiptUrl = invoice.hostedInvoiceUrl ?? invoice.invoicePdf;
  const statusClass = INVOICE_STATUS_STYLES[invoice.status] ?? "bg-muted text-muted-foreground";
  const statusLabel = invoice.status.charAt(0).toUpperCase() + invoice.status.slice(1);

  return (
    <tr className="border-t border-border">
      <td className="px-4 py-3">{formatFullDate(invoice.createdAt)}</td>
      <td className="px-2 py-3 tabular-nums">
        {formatCentsExact(invoice.amountPaidCents, invoice.currency)}
      </td>
      <td className="px-2 py-3">
        <span className={cn("rounded-full px-2.5 py-0.5 text-xs font-semibold", statusClass)}>
          {statusLabel}
        </span>
      </td>
      <td className="px-2 py-3 text-right">
        {receiptUrl ? (
          <a
            href={receiptUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 rounded font-semibold text-foreground/80 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Receipt
            <ExternalLink className="size-3.5" aria-hidden="true" />
          </a>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </td>
    </tr>
  );
}

// ---------------------------------------------------------------------------
// 5. Manage: portal handoff + cancel
// ---------------------------------------------------------------------------

const REASON_MAX_LENGTH = 500;

/**
 * Maps a self-serve billing failure to a safe, pre-written sentence, never the raw
 * backend message. `NO_BILLING_ACCOUNT` (no Stripe subscription yet, e.g. still
 * PENDING_ACTIVATION) and `ILLEGAL_STATE_TRANSITION` (the plan changed in another
 * tab since this page loaded) are both expected, recoverable 409s; anything else
 * falls back to a generic retry.
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

/**
 * Portal handoff plus cancellation. There is deliberately no pause: HomeKept does
 * not offer pausing, so the only self-serve exit is cancelling, after which access
 * runs to the end of the period already paid for. A subscription paused directly in
 * Stripe still shows its PAUSED status above, but nothing here offers to pause or
 * resume one.
 */
function ManageSection({ subscription }: { subscription: AppSubscription }) {
  const { status, currentPeriodEnd } = subscription;
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const portalMutation = usePortalSession();
  const cancelMutation = useCancelSubscription();

  function handleError(err: unknown) {
    // A changed-elsewhere 409 means the status shown here is already stale, so
    // refetch and let the page above reflect reality.
    if (err instanceof ApiError && err.code === "ILLEGAL_STATE_TRANSITION") {
      void queryClient.invalidateQueries({ queryKey: ["app-subscription"] });
    }
    setActionError(billingErrorMessage(err));
  }

  function handlePortal() {
    setActionError(null);
    portalMutation.mutate(undefined, { onError: handleError });
  }

  function openCancelDialog() {
    cancelMutation.reset();
    setActionError(null);
    setReason("");
    setDialogOpen(true);
  }

  function closeDialog() {
    if (cancelMutation.isPending) return;
    setDialogOpen(false);
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

  // Cancel is offered until the subscription is already cancelled. A customer with a
  // payment problem still needs a way out, so PAYMENT_ISSUE keeps it too.
  const canCancel = status === "ACTIVE" || status === "PAUSED" || status === "PAYMENT_ISSUE";
  const showsEndDate =
    status === "CANCELLED" &&
    !!currentPeriodEnd &&
    new Date(currentPeriodEnd).getTime() > Date.now();

  return (
    <div className="rounded-3xl border border-border bg-card p-6">
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

        {canCancel && (
          <Button variant="destructive" size="sm" onClick={openCancelDialog}>
            Cancel subscription
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

      <CancelDialog
        open={dialogOpen}
        onOpenChange={(next) => (next ? setDialogOpen(true) : closeDialog())}
        currentPeriodEnd={currentPeriodEnd}
        reason={reason}
        onReasonChange={setReason}
        onConfirm={confirmCancel}
        pending={cancelMutation.isPending}
        error={actionError}
      />
    </div>
  );
}

interface CancelDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentPeriodEnd?: string;
  reason: string;
  onReasonChange: (value: string) => void;
  onConfirm: () => void;
  pending: boolean;
  error: string | null;
}

function CancelDialog({
  open,
  onOpenChange,
  currentPeriodEnd,
  reason,
  onReasonChange,
  onConfirm,
  pending,
  error,
}: CancelDialogProps) {
  const baseId = useId();
  const titleId = `${baseId}-title`;
  const descId = `${baseId}-desc`;
  const errorId = `${baseId}-error`;
  const reasonId = `${baseId}-reason`;

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
          <DialogTitle id={titleId}>Cancel your subscription?</DialogTitle>
          <DialogDescription id={descId}>
            {currentPeriodEnd
              ? `Your access continues until your current period ends on ${formatFullDate(currentPeriodEnd)}.`
              : "Your access continues until your current billing period ends."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit}>
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
            <Button type="submit" variant="destructive" disabled={pending} aria-busy={pending}>
              {pending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              Cancel subscription
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
