import { createFileRoute } from "@tanstack/react-router";
import { ArrowUpRight, ArrowDownRight } from "lucide-react";
import { metrics, formatCAD, subscribers } from "@/lib/mock-admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/metrics")({
  head: () => ({
    meta: [
      { title: "Metrics — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: MetricsPage,
});

const cohorts = [
  { month: "Oct 2025", started: 18, retainedM1: 17, retainedM3: 16, retainedM6: 15 },
  { month: "Nov 2025", started: 22, retainedM1: 21, retainedM3: 20, retainedM6: 19 },
  { month: "Dec 2025", started: 26, retainedM1: 25, retainedM3: 24, retainedM6: 22 },
  { month: "Jan 2026", started: 31, retainedM1: 30, retainedM3: 28, retainedM6: null },
  { month: "Feb 2026", started: 34, retainedM1: 33, retainedM3: 31, retainedM6: null },
  { month: "Mar 2026", started: 41, retainedM1: 40, retainedM3: null, retainedM6: null },
];

const techUtilization = [
  { name: "Marcus T.", booked: 32, capacity: 36 },
  { name: "Sasha P.", booked: 28, capacity: 32 },
  { name: "Devon H.", booked: 24, capacity: 32 },
];

function MetricsPage() {
  const revenueByCity = subscribers.reduce<Record<string, number>>((acc, s) => {
    acc[s.city] = (acc[s.city] ?? 0) + s.mrr;
    return acc;
  }, {});
  const maxCity = Math.max(...Object.values(revenueByCity));

  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Metrics</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Cohort retention, technician utilization, visit completion, and revenue by city.
      </p>

      <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4">
        <BigStat label="MRR" value={formatCAD(metrics.mrr)} delta={`+${metrics.mrrDeltaPct}%`} up />
        <BigStat label="Visit completion" value="96.2%" delta="+1.4 pts" up />
        <BigStat label="Avg tenure" value="11.4 mo" delta="+0.3 mo" up />
        <BigStat label="Churn" value="2.1%" delta="-0.4 pts" up />
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <Panel title="Cohort retention" subtitle="Households remaining N months after signup">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="py-2 text-left">Cohort</th>
                  <th className="py-2 text-right">Start</th>
                  <th className="py-2 text-right">M1</th>
                  <th className="py-2 text-right">M3</th>
                  <th className="py-2 text-right">M6</th>
                </tr>
              </thead>
              <tbody>
                {cohorts.map((c) => (
                  <tr key={c.month} className="border-t border-border">
                    <td className="py-2">{c.month}</td>
                    <td className="py-2 text-right tabular-nums">{c.started}</td>
                    <td className="py-2 text-right tabular-nums">{c.retainedM1 ?? "—"}</td>
                    <td className="py-2 text-right tabular-nums">{c.retainedM3 ?? "—"}</td>
                    <td className="py-2 text-right tabular-nums">{c.retainedM6 ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title="Technician utilization" subtitle="Visits booked vs weekly capacity">
          <div className="space-y-4">
            {techUtilization.map((t) => {
              const pct = Math.round((t.booked / t.capacity) * 100);
              return (
                <div key={t.name}>
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium">{t.name}</span>
                    <span className="text-muted-foreground">{t.booked} / {t.capacity} <span className="ml-2 tabular-nums text-foreground">{pct}%</span></span>
                  </div>
                  <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
                    <div className="h-full bg-primary" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </Panel>

        <Panel title="Revenue by city" subtitle="Current monthly recurring revenue">
          <div className="space-y-4">
            {Object.entries(revenueByCity).map(([city, rev]) => (
              <div key={city}>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium">{city}</span>
                  <span className="tabular-nums">{formatCAD(rev)}</span>
                </div>
                <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
                  <div className="h-full bg-foreground" style={{ width: `${(rev / maxCity) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </Panel>

        <Panel title="Lead → subscriber funnel" subtitle="Last 90 days">
          <FunnelRow label="Walk-throughs booked" value={42} pct={100} />
          <FunnelRow label="Walk-throughs completed" value={38} pct={90} />
          <FunnelRow label="Plans delivered" value={36} pct={86} />
          <FunnelRow label="Subscribers started" value={29} pct={69} />
        </Panel>
      </div>
    </div>
  );
}

function BigStat({ label, value, delta, up }: { label: string; value: string; delta: string; up: boolean }) {
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 font-display text-2xl font-extrabold tabular-nums">{value}</div>
      <div className={cn("mt-1 flex items-center gap-1 text-xs", up ? "text-emerald-600" : "text-rose-600")}>
        <Icon className="h-3.5 w-3.5" /> {delta}
      </div>
    </div>
  );
}

function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </div>
  );
}

function FunnelRow({ label, value, pct }: { label: string; value: number; pct: number }) {
  return (
    <div className="mb-3">
      <div className="flex items-center justify-between text-sm">
        <span>{label}</span>
        <span className="tabular-nums text-muted-foreground">{value} · {pct}%</span>
      </div>
      <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
        <div className="h-full bg-primary" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
