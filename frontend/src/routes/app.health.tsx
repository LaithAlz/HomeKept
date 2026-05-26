import { createFileRoute } from "@tanstack/react-router";
import { ArrowUpRight, AlertTriangle, CheckCircle2 } from "lucide-react";
import { subscriber } from "@/lib/mock-account";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/health")({
  head: () => ({
    meta: [
      { title: "Home health — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: HealthPage,
});

interface SystemScore {
  name: string;
  score: number;
  delta: number;
  note: string;
}

const systems: SystemScore[] = [
  { name: "HVAC", score: 92, delta: 4, note: "Filters fresh, AC tested, ducts clear." },
  { name: "Plumbing", score: 88, delta: 2, note: "No leaks detected. Water heater due for flush next quarter." },
  { name: "Exterior", score: 80, delta: 6, note: "Gutters clear, caulking holding. Driveway sealant aging." },
  { name: "Safety", score: 95, delta: 1, note: "All 4 smoke + 2 CO detectors tested and pass." },
  { name: "Seasonal", score: 78, delta: 10, note: "Hose bibs reconnected. Patio screen needs re-tensioning." },
  { name: "Air quality", score: 71, delta: -3, note: "Dryer vent flagged for cleaning — scheduled next visit." },
];

const movers = [
  { label: "AC startup & coil rinse passed", delta: 5 },
  { label: "Gutter clearing completed", delta: 4 },
  { label: "Dryer vent restriction noted", delta: -4 },
];

function HealthPage() {
  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Home health</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Your 0–100 score, broken down by system, with the items that moved it most this quarter.
      </p>

      <section className="mt-8 grid gap-6 lg:grid-cols-[280px_1fr]">
        <div className="rounded-3xl border border-border bg-card p-6 text-center">
          <div className="text-xs uppercase tracking-wide text-muted-foreground">Overall</div>
          <div className="mt-2 font-display text-6xl font-extrabold tabular-nums">{subscriber.health.score}</div>
          <div className="mt-1 inline-flex items-center gap-1 text-sm text-emerald-600">
            <ArrowUpRight className="h-4 w-4" /> +{subscriber.health.delta} this quarter
          </div>
          <p className="mt-4 text-xs text-muted-foreground">{subscriber.health.note}</p>
        </div>

        <div className="rounded-3xl border border-border bg-card p-6">
          <h2 className="font-display text-lg font-bold">Biggest movers</h2>
          <ul className="mt-4 space-y-3">
            {movers.map((m) => (
              <li key={m.label} className="flex items-center justify-between gap-4 border-t border-border pt-3 first:border-t-0 first:pt-0">
                <div className="flex items-center gap-2 text-sm">
                  {m.delta > 0 ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                  ) : (
                    <AlertTriangle className="h-4 w-4 text-amber-600" />
                  )}
                  <span>{m.label}</span>
                </div>
                <span className={cn("tabular-nums text-sm font-medium", m.delta > 0 ? "text-emerald-600" : "text-rose-600")}>
                  {m.delta > 0 ? "+" : ""}{m.delta}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="mt-8">
        <h2 className="text-xs uppercase tracking-wide text-muted-foreground">By system</h2>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          {systems.map((s) => (
            <div key={s.name} className="rounded-2xl border border-border bg-card p-5">
              <div className="flex items-center justify-between">
                <div className="font-medium">{s.name}</div>
                <div className="flex items-baseline gap-2">
                  <span className="font-display text-2xl font-extrabold tabular-nums">{s.score}</span>
                  <span className={cn("text-xs tabular-nums", s.delta >= 0 ? "text-emerald-600" : "text-rose-600")}>
                    {s.delta >= 0 ? "+" : ""}{s.delta}
                  </span>
                </div>
              </div>
              <div className="mt-3 h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className={cn("h-full", s.score >= 85 ? "bg-emerald-500" : s.score >= 70 ? "bg-amber-500" : "bg-rose-500")}
                  style={{ width: `${s.score}%` }}
                />
              </div>
              <p className="mt-3 text-xs text-muted-foreground">{s.note}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
