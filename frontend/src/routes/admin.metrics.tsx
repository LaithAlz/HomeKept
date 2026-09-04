import { createFileRoute } from "@tanstack/react-router";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { useAdminDashboard } from "@/lib/admin";
import { formatCentsCad } from "@/lib/format";

export const Route = createFileRoute("/admin/metrics")({
  head: () => ({
    meta: [{ title: "Metrics — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: MetricsPage,
});

/**
 * This page renders only what `GET /api/admin/dashboard` actually returns
 * (`AdminDashboardResponse`): point-in-time aggregates, no history. There
 * is deliberately no cohort retention, technician utilization, churn, tenure,
 * revenue-by-city, or funnel data here, because the backend has no endpoint
 * that produces any of it yet. Do not add deltas or trend arrows: the
 * endpoint has no time-series data to compute them from.
 */
function MetricsPage() {
  const { data: dashboard, isLoading, isError, refetch } = useAdminDashboard();

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
        <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4">
          <Stat label="Active subscribers" value={String(dashboard.activeSubscribers)} />
          <Stat label="MRR" value={formatCentsCad(dashboard.mrrCents)} />
          <Stat label="Pending walk-throughs" value={String(dashboard.pendingWalkthroughs)} />
          <Stat label="Upcoming visits" value={String(dashboard.upcomingVisits)} />
        </div>
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
