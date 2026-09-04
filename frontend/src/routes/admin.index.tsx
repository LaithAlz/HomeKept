import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Plus, AlertTriangle, CreditCard, CalendarClock, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NewBookingSheet } from "@/components/admin/NewBookingSheet";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { cn } from "@/lib/utils";
import {
  formatCentsCad,
  formatDateShort,
  formatDateTime,
  formatTime,
  formatTodayLong,
} from "@/lib/format";
import {
  useAdminDashboard,
  useAdminSubscribers,
  useAdminBookings,
  useAdminRescheduleRequests,
  STATUS_LABEL,
  STATUS_TONE,
  PLAN_LABEL,
  formatWeekOf,
} from "@/lib/admin";

export const Route = createFileRoute("/admin/")({
  head: () => ({
    meta: [{ title: "Dashboard — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: AdminDashboard,
});

function AdminDashboard() {
  const [newBookingOpen, setNewBookingOpen] = useState(false);
  const {
    data: dashboard,
    dataUpdatedAt,
    isLoading: dashboardLoading,
    isError: dashboardError,
    refetch: refetchDashboard,
  } = useAdminDashboard();

  return (
    <>
      {/* Top bar */}
      <div className="sticky top-0 z-20 border-b border-border bg-card/95 backdrop-blur">
        <div className="flex flex-col gap-3 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs text-muted-foreground">
              {formatTodayLong()}
              {/* Derived from the real dashboard query state, not an unverified
                  system-health claim: last-updated time on success, an honest
                  error note on failure, nothing while the first load is in flight. */}
              {dashboard && !dashboardError && (
                <>
                  {" "}
                  ·{" "}
                  <span className="font-semibold text-foreground">
                    Updated {formatTime(new Date(dataUpdatedAt).toISOString())}
                  </span>
                </>
              )}
              {dashboardError && (
                <>
                  {" "}
                  ·{" "}
                  <span className="font-semibold text-destructive">Dashboard data unavailable</span>
                </>
              )}
            </p>
            <h1 className="mt-0.5 font-display text-2xl font-extrabold tracking-tight">
              Dashboard
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <Button size="sm" onClick={() => setNewBookingOpen(true)}>
              <Plus className="size-4" />
              New booking
            </Button>
          </div>
        </div>
      </div>

      <div className="px-6 py-6 space-y-6">
        {/* Metric strip */}
        {dashboardLoading && <PanelLoading label="Loading dashboard metrics." className="" />}

        {dashboardError && !dashboardLoading && (
          <PanelError
            label="We couldn't load dashboard metrics."
            onRetry={() => void refetchDashboard()}
            className="rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
          />
        )}

        <section aria-label="Key metrics" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard label="MRR" value={dashboard ? formatCentsCad(dashboard.mrrCents) : "—"} />
          <MetricCard
            label="Active subscribers"
            value={dashboard ? String(dashboard.activeSubscribers) : "—"}
          />
          <MetricCard
            label="Pending walk-throughs"
            value={dashboard ? String(dashboard.pendingWalkthroughs) : "—"}
          />
          <MetricCard
            label="Upcoming visits"
            value={dashboard ? String(dashboard.upcomingVisits) : "—"}
          />
        </section>

        {/* Recent subscribers */}
        <RecentSubscribersPanel />

        {/* Two-column section */}
        <section className="grid gap-6 xl:grid-cols-2">
          <PendingWalkthroughsPanel />
          <NeedsAttentionPanel />
        </section>
      </div>

      <NewBookingSheet open={newBookingOpen} onOpenChange={setNewBookingOpen} />
    </>
  );
}

// ---------------------------------------------------------------------------
// Metric cards
// ---------------------------------------------------------------------------

function MetricCard({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: string;
  sub?: React.ReactNode;
  tone?: "warn";
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border bg-card p-4 shadow-sm",
        tone === "warn" ? "border-destructive/30" : "border-border",
      )}
    >
      <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 font-display text-3xl font-extrabold tracking-tight">{value}</p>
      {sub && <p className="mt-2 text-xs text-muted-foreground">{sub}</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Recent subscribers
// ---------------------------------------------------------------------------

function RecentSubscribersPanel() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 6 });

  return (
    <section
      aria-labelledby="subs-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2 id="subs-h" className="font-display text-lg font-bold tracking-tight">
            Recent subscribers
          </h2>
          <p className="text-xs text-muted-foreground">
            {subscribers ? `${subscribers.length} most recent` : "Loading subscribers."}
          </p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/admin/subscribers">See all</Link>
        </Button>
      </header>

      {isLoading && <PanelLoading label="Loading subscribers." />}
      {isError && !isLoading && (
        <PanelError label="We couldn't load subscribers." onRetry={() => void refetch()} />
      )}

      {subscribers && subscribers.length === 0 && (
        <p className="p-6 text-sm text-muted-foreground">No subscribers yet.</p>
      )}

      {subscribers && subscribers.length > 0 && (
        <ul className="divide-y divide-border">
          {subscribers.map((s) => (
            <li key={s.id} className="flex items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                <Link
                  to="/admin/subscribers"
                  search={{ id: s.id }}
                  className="font-semibold text-foreground hover:underline"
                >
                  Subscriber #{s.id}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "No plan yet"}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-xs font-medium",
                    STATUS_TONE[s.status] ?? "bg-muted text-muted-foreground",
                  )}
                >
                  {STATUS_LABEL[s.status] ?? s.status}
                </span>
                <span className="text-sm font-semibold tabular-nums">
                  {formatCentsCad(s.mrrCents)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Pending walk-throughs
// ---------------------------------------------------------------------------

function PendingWalkthroughsPanel() {
  const {
    data: bookings,
    isLoading,
    isError,
    refetch,
  } = useAdminBookings({ status: "PENDING", limit: 6 });

  return (
    <article
      aria-labelledby="walk-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2 id="walk-h" className="font-display text-lg font-bold tracking-tight">
            Pending walk-throughs
          </h2>
          <p className="text-xs text-muted-foreground">
            {bookings ? `${bookings.length} awaiting confirmation` : "Loading walk-throughs."}
          </p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/admin/walkthroughs">See all</Link>
        </Button>
      </header>

      {isLoading && <PanelLoading label="Loading walk-throughs." />}
      {isError && !isLoading && (
        <PanelError
          label="We couldn't load the walk-through pipeline."
          onRetry={() => void refetch()}
        />
      )}

      {bookings && bookings.length === 0 && (
        <p className="p-6 text-sm text-muted-foreground">
          No walk-throughs are awaiting confirmation.
        </p>
      )}

      {bookings && bookings.length > 0 && (
        <ul className="divide-y divide-border">
          {bookings.map((b) => (
            <li key={b.id} className="flex items-start gap-3 p-4">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-muted-foreground">
                <CalendarClock className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-3">
                  <p className="truncate font-semibold text-foreground">{b.fullName}</p>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {b.scheduledFor
                      ? formatDateTime(b.scheduledFor)
                      : `Week of ${formatWeekOf(b.preferredWeek)}`}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  {b.city} ·{" "}
                  <span className="rounded-md bg-surface px-1.5 py-0.5 font-semibold text-foreground/80">
                    {b.leadSource}
                  </span>
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

// ---------------------------------------------------------------------------
// Needs attention — derived from two real signals: subscribers whose status
// is PAYMENT_ISSUE, and PENDING customer reschedule requests. No other field
// exposed by the admin endpoints maps cleanly to "needs attention" without
// inventing content, so those are the only two categories shown here.
// ---------------------------------------------------------------------------

function NeedsAttentionPanel() {
  const {
    data: subscribers,
    isLoading: subsLoading,
    isError: subsError,
    refetch: refetchSubs,
  } = useAdminSubscribers({ limit: 100 });
  const {
    data: rescheduleRequests,
    isLoading: rrLoading,
    isError: rrError,
    refetch: refetchRR,
  } = useAdminRescheduleRequests();

  const isLoading = subsLoading || rrLoading;
  const isError = subsError || rrError;
  const paymentIssues = (subscribers ?? []).filter((s) => s.status === "PAYMENT_ISSUE");
  const pendingReschedules = rescheduleRequests ?? [];
  const totalOpen = paymentIssues.length + pendingReschedules.length;

  return (
    <article aria-labelledby="att-h" className="rounded-2xl border border-border bg-card shadow-sm">
      <header className="flex items-center justify-between border-b border-border p-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="size-4 text-destructive" aria-hidden="true" />
          <h2 id="att-h" className="font-display text-lg font-bold tracking-tight">
            Needs attention
          </h2>
        </div>
        {!isLoading && !isError && (
          <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-[11px] font-bold text-destructive">
            {totalOpen} open
          </span>
        )}
      </header>

      {isLoading && <PanelLoading label="Loading." />}
      {isError && !isLoading && (
        <PanelError
          label="We couldn't load these signals."
          onRetry={() => {
            void refetchSubs();
            void refetchRR();
          }}
        />
      )}

      {!isLoading && !isError && totalOpen === 0 && (
        <p className="p-6 text-sm text-muted-foreground">Nothing needs attention right now.</p>
      )}

      {!isLoading && !isError && totalOpen > 0 && (
        <ul className="divide-y divide-border">
          {paymentIssues.map((s) => (
            <li key={`sub-${s.id}`} className="flex items-start gap-3 p-4">
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-destructive/15 text-destructive">
                <CreditCard className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-foreground">Subscriber #{s.id}: payment issue</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {s.planCode ? `${PLAN_LABEL[s.planCode] ?? s.planCode} plan` : "No plan on file"}{" "}
                  · {formatCentsCad(s.mrrCents)} MRR
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0" asChild>
                <Link to="/admin/subscribers" search={{ id: s.id }}>
                  View
                </Link>
              </Button>
            </li>
          ))}
          {pendingReschedules.map((r) => (
            <li key={`rr-${r.id}`} className="flex items-start gap-3 p-4">
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <CalendarClock className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-foreground">
                  Reschedule request: visit #{r.visitId}
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Subscriber #{r.subscriberId} · Requested {formatDateShort(r.createdAt)}
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0" asChild>
                <Link to="/admin/visits">View</Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
