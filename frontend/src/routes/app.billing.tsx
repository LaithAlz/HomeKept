import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { useSubscription, type AppSubscription, type SubscriberStatus } from "@/lib/account";
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
          <PlanCard subscription={query.data} />
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
