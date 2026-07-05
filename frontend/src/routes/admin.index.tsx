import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Download, Plus, AlertTriangle, CreditCard, CalendarClock, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { formatDateShort, formatDateTime, formatTodayLong, type Plan } from "@/lib/mock-admin";
import {
  useAdminDashboard,
  useAdminSubscribers,
  useAdminBookings,
  useAdminRescheduleRequests,
  formatCentsCAD,
} from "@/lib/admin";

export const Route = createFileRoute("/admin/")({
  head: () => ({
    meta: [{ title: "Dashboard — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: AdminDashboard,
});

const STATUS_LABEL: Record<string, string> = {
  PENDING_ACTIVATION: "Pending activation",
  ACTIVE: "Active",
  PAUSED: "Paused",
  PAYMENT_ISSUE: "Payment issue",
  CANCELLED: "Cancelled",
};

const STATUS_TONE: Record<string, string> = {
  PENDING_ACTIVATION: "bg-sky-500/10 text-sky-700",
  ACTIVE: "bg-emerald-500/10 text-emerald-700",
  PAUSED: "bg-muted text-muted-foreground",
  PAYMENT_ISSUE: "bg-rose-500/10 text-rose-700",
  CANCELLED: "bg-muted text-muted-foreground",
};

const PLAN_LABEL: Record<string, string> = {
  ESSENTIAL: "Essential",
  COMPLETE: "Complete",
  PREMIER: "Premier",
};

/**
 * `preferredWeek` is a LocalDate ("YYYY-MM-DD") with no time-of-day meaning.
 * Anchoring it to UTC noon before formatting with an explicit UTC timeZone
 * avoids an off-by-one day depending on the viewer's local timezone. Mirrors
 * the identical helper in `admin.walkthroughs.tsx`.
 */
function formatWeekOf(dateStr: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
  }).format(new Date(`${dateStr}T12:00:00Z`));
}

function AdminDashboard() {
  const [newBookingOpen, setNewBookingOpen] = useState(false);
  const {
    data: dashboard,
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
              {formatTodayLong()} ·{" "}
              <span className="font-semibold text-foreground">All systems normal</span>
            </p>
            <h1 className="mt-0.5 font-display text-2xl font-extrabold tracking-tight">
              Dashboard
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm">
              <Download className="size-4" />
              Export
            </Button>
            <Button size="sm" onClick={() => setNewBookingOpen(true)}>
              <Plus className="size-4" />
              New booking
            </Button>
          </div>
        </div>
      </div>

      <div className="px-6 py-6 space-y-6">
        {/* Metric strip */}
        {dashboardLoading && (
          <div
            role="status"
            aria-live="polite"
            className="flex items-center gap-2 text-sm text-muted-foreground"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading dashboard metrics.
          </div>
        )}

        {dashboardError && !dashboardLoading && (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
          >
            <span>We couldn't load dashboard metrics.</span>
            <Button size="sm" variant="outline" onClick={() => void refetchDashboard()}>
              Try again
            </Button>
          </div>
        )}

        <section
          aria-label="Key metrics"
          className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
        >
          <MetricCard label="MRR" value={dashboard ? formatCentsCAD(dashboard.mrrCents) : "—"} />
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
          <MetricCard
            label="Founding rate slots remaining"
            value={dashboard ? String(dashboard.foundingRateSlotsRemaining) : "—"}
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
// Panel loading / error helpers (shared shape across the three panels below)
// ---------------------------------------------------------------------------

function PanelLoading({ label }: { label: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-2 p-4 text-sm text-muted-foreground"
    >
      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
      {label}
    </div>
  );
}

function PanelError({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm text-destructive"
    >
      <span>{label}</span>
      <Button size="sm" variant="outline" onClick={onRetry}>
        Try again
      </Button>
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
                  className="font-semibold text-foreground hover:underline"
                >
                  Subscriber #{s.id}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "No plan yet"}
                  {s.foundingRate ? " · Founding rate" : ""}
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
                  {formatCentsCAD(s.mrrCents)}
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
                  · {formatCentsCAD(s.mrrCents)} MRR
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0" asChild>
                <Link to="/admin/subscribers">View</Link>
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

// ---------------------------------------------------------------------------
// New booking sheet
// ---------------------------------------------------------------------------

function NewBookingSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const [plan, setPlan] = useState<Plan>("Complete");
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <div className="flex items-center justify-between">
            <SheetTitle className="font-display text-2xl font-extrabold tracking-tight">
              New booking
            </SheetTitle>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              aria-label="Close"
              className="inline-flex size-8 items-center justify-center rounded-full hover:bg-surface"
            >
              <X className="size-4" />
            </button>
          </div>
          <SheetDescription>
            Create a subscriber booking manually. This is not a walk-through.
          </SheetDescription>
        </SheetHeader>

        <form
          className="mt-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            onOpenChange(false);
          }}
        >
          <Field label="Homeowner name">
            <Input placeholder="Jane Doe" />
          </Field>
          <Field label="Email">
            <Input type="email" placeholder="jane@example.com" />
          </Field>
          <Field label="Phone">
            <Input type="tel" placeholder="(905) 555-0123" />
          </Field>
          <Field label="Street address">
            <Input placeholder="123 Example Rd" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="City">
              <Select defaultValue="Mississauga">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="Mississauga">Mississauga</SelectItem>
                  <SelectItem value="Oakville">Oakville</SelectItem>
                  <SelectItem value="Milton">Milton</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Postal code">
              <Input placeholder="L5L 0A0" />
            </Field>
          </div>
          <Field label="Plan">
            <Select value={plan} onValueChange={(v) => setPlan(v as Plan)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Essential">Essential — $129/mo</SelectItem>
                <SelectItem value="Complete">Complete — $189/mo</SelectItem>
                <SelectItem value="Premier">Premier — $289/mo</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Billing">
            <Select defaultValue="monthly">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="monthly">Monthly</SelectItem>
                <SelectItem value="annual">Annual (saves ~17%)</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Internal note (optional)">
            <textarea
              rows={3}
              placeholder="Anything ops should know about this booking…"
              className="w-full resize-none rounded-md border border-input bg-background p-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </Field>

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit">Create booking</Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {children}
    </label>
  );
}
