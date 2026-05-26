import { createFileRoute } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { formatCAD, subscribers } from "@/lib/mock-admin";

export const Route = createFileRoute("/admin/plans")({
  head: () => ({
    meta: [
      { title: "Plans — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: PlansAdminPage,
});

const plans = [
  {
    name: "Essential",
    monthly: 129,
    annual: 1284,
    visits: 4,
    services: 8,
    summary: "Quarterly visits, safety checks, seasonal switchovers.",
  },
  {
    name: "Complete",
    monthly: 189,
    annual: 1884,
    visits: 6,
    services: 14,
    summary: "Bi-monthly visits, gutter clearing, exterior touch-ups.",
  },
  {
    name: "Premier",
    monthly: 289,
    annual: 2868,
    visits: 8,
    services: 22,
    summary: "Every 6 weeks, full home health, priority response.",
  },
];

function PlansAdminPage() {
  const count = (name: string) => subscribers.filter((s) => s.plan === name).length;

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Plans</h1>
          <p className="mt-1 text-sm text-muted-foreground">Pricing, visit cadence, and service inclusions for each tier.</p>
        </div>
        <Button size="sm" variant="outline">Edit pricing</Button>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        {plans.map((p) => (
          <div key={p.name} className="rounded-2xl border border-border bg-card p-6">
            <div className="flex items-center justify-between">
              <h2 className="font-display text-xl font-bold">{p.name}</h2>
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{count(p.name)} subscribers</span>
            </div>
            <p className="mt-2 text-sm text-muted-foreground">{p.summary}</p>

            <div className="mt-5 grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Monthly</div>
                <div className="mt-0.5 font-display text-xl font-extrabold tabular-nums">{formatCAD(p.monthly)}</div>
              </div>
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Annual</div>
                <div className="mt-0.5 font-display text-xl font-extrabold tabular-nums">{formatCAD(p.annual)}</div>
              </div>
            </div>

            <dl className="mt-4 space-y-2 text-sm">
              <Row k="Visits / year" v={p.visits} />
              <Row k="Services included" v={p.services} />
              <Row k="Annual MRR" v={formatCAD(p.monthly * count(p.name))} />
            </dl>

            <div className="mt-5 flex gap-2">
              <Button size="sm" variant="outline" className="flex-1">Manage services</Button>
              <Button size="sm" variant="ghost">…</Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between border-t border-border pt-2">
      <dt className="text-muted-foreground">{k}</dt>
      <dd className="font-medium tabular-nums">{v}</dd>
    </div>
  );
}
