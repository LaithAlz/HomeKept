import { createFileRoute } from "@tanstack/react-router";
import { Check } from "lucide-react";
import { PLANS, formatCad } from "@/lib/plans";

export const Route = createFileRoute("/admin/plans")({
  head: () => ({
    meta: [{ title: "Plans — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: PlansAdminPage,
});

/**
 * Renders the three real plan tiers straight from `@/lib/plans` (the same
 * source the public `/plans` page and checkout use), so the numbers here can
 * never drift from what customers actually see. There is no endpoint for
 * per-plan subscriber counts or derived per-plan MRR, so neither appears
 * here; editing pricing is a founder-only, hand-write change, so no
 * "edit"-style controls are shown.
 */
function PlansAdminPage() {
  return (
    <div className="px-6 py-8">
      <div>
        <h1 className="font-display text-2xl font-extrabold tracking-tight">Plans</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Pricing, visit cadence, and service inclusions for each tier.
        </p>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        {PLANS.map((p) => (
          <div key={p.id} className="rounded-2xl border border-border bg-card p-6">
            <h2 className="font-display text-xl font-bold">{p.name}</h2>
            <p className="mt-2 text-sm text-muted-foreground">{p.tagline}</p>

            <div className="mt-5 grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Monthly</div>
                <div className="mt-0.5 font-display text-xl font-extrabold tabular-nums">
                  {formatCad(p.monthlyPriceCad)}
                </div>
              </div>
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Annual</div>
                <div className="mt-0.5 font-display text-xl font-extrabold tabular-nums">
                  {formatCad(p.annualPriceCad)}
                </div>
              </div>
            </div>

            <dl className="mt-4 space-y-2 text-sm">
              <Row k="Visit cadence" v={p.visitsDescription} />
            </dl>

            <ul className="mt-5 space-y-2 border-t border-border pt-4 text-sm">
              {p.features.map((f) => (
                <li key={f} className="flex items-start gap-2">
                  <Check className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
                  <span className="text-foreground/90">{f}</span>
                </li>
              ))}
            </ul>
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
      <dd className="font-medium">{v}</dd>
    </div>
  );
}
