import { createFileRoute } from "@tanstack/react-router";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { useAdminDashboard } from "@/lib/admin";
import { formatCentsCad } from "@/lib/format";
import { FOUNDING_RATE_CAP } from "@/lib/plans";

export const Route = createFileRoute("/admin/metrics")({
  head: () => ({
    meta: [{ title: "Metrics — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: MetricsPage,
});

/**
 * This page renders only what `GET /api/admin/dashboard` actually returns
 * (`AdminDashboardResponse`): five point-in-time aggregates, no history. There
 * is deliberately no cohort retention, technician utilization, churn, tenure,
 * revenue-by-city, or funnel data here, because the backend has no endpoint
 * that produces any of it yet. Do not add deltas or trend arrows: the
 * endpoint has no time-series data to compute them from.
 */
function MetricsPage() {
  const { data: dashboard, isLoading, isError, refetch } = useAdminDashboard();

  const foundingUsed = dashboard
    ? Math.max(0, FOUNDING_RATE_CAP - dashboard.foundingRateSlotsRemaining)
    : null;
  const foundingPct =
    foundingUsed !== null ? Math.round((foundingUsed / FOUNDING_RATE_CAP) * 100) : 0;

  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Metrics</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        An operational snapshot: the numbers we currently track, updated live.
      </p>

      {isLoading && <PanelLoading label="Loading metrics." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load metrics."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {!isLoading && !isError && !dashboard && (
        <p className="mt-6 text-sm text-muted-foreground">No metrics are available right now.</p>
      )}

      {dashboard && (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            <Stat label="Active subscribers" value={String(dashboard.activeSubscribers)} />
            <Stat label="MRR" value={formatCentsCad(dashboard.mrrCents)} />
            <Stat label="Pending walk-throughs" value={String(dashboard.pendingWalkthroughs)} />
            <Stat label="Upcoming visits" value={String(dashboard.upcomingVisits)} />
            <Stat
              label="Founding-rate slots remaining"
              value={String(dashboard.foundingRateSlotsRemaining)}
            />
          </div>

          <div className="mt-8 grid gap-6 lg:grid-cols-2">
            <Panel
              title="Founding-rate seats"
              subtitle={`First ${FOUNDING_RATE_CAP} subscribers lock in founding pricing.`}
            >
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium">
                  {foundingUsed} of {FOUNDING_RATE_CAP} filled
                </span>
                <span className="tabular-nums text-muted-foreground">
                  {dashboard.foundingRateSlotsRemaining} remaining
                </span>
              </div>
              <div
                role="progressbar"
                aria-label="Founding-rate seats filled"
                aria-valuenow={foundingPct}
                aria-valuemin={0}
                aria-valuemax={100}
                className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted"
              >
                <div className="h-full bg-primary" style={{ width: `${foundingPct}%` }} />
              </div>
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 font-display text-2xl font-extrabold tabular-nums">{value}</div>
    </div>
  );
}

function Panel({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </div>
  );
}
