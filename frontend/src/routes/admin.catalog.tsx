import { createFileRoute } from "@tanstack/react-router";
import { Loader2, Wrench } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatCentsCAD } from "@/lib/admin";
import {
  useCatalogPlans,
  useCatalogPicks,
  type PlanCode,
  type TierClass,
  type ServiceCategory,
} from "@/lib/catalog";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/catalog")({
  head: () => ({
    meta: [{ title: "Service catalog — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: CatalogPage,
});

const PLAN_ORDER: PlanCode[] = ["ESSENTIAL", "COMPLETE", "PREMIER"];

const TIER_CLASS_LABEL: Record<TierClass, string> = {
  BASIC: "Basic",
  MEDIUM: "Medium",
  PREMIUM: "Premium",
};

const TIER_CLASS_TONE: Record<TierClass, string> = {
  BASIC: "bg-muted text-muted-foreground",
  MEDIUM: "bg-sky-500/10 text-sky-700",
  PREMIUM: "bg-accent/20 text-accent-foreground",
};

const CATEGORY_LABEL: Record<ServiceCategory, string> = {
  HVAC: "HVAC",
  PLUMBING: "Plumbing",
  EXTERIOR: "Exterior",
  SMART_HOME: "Smart home",
};

/**
 * This page renders only what the two public catalog endpoints actually return:
 * `GET /api/catalog/plans` (each tier's included, standing services with name,
 * tier class, and frequency per year) and `GET /api/catalog/picks` (the à la
 * carte menu, grouped by tier class, with real per-service category, duration,
 * and price). There is no service-editing endpoint, so there is no "new
 * service" affordance here — catalog changes are a founder/spec change, not an
 * admin-console action.
 */
function CatalogPage() {
  const plans = useCatalogPlans();
  const picks = useCatalogPicks();

  const isLoading = plans.isLoading || picks.isLoading;
  const isError = plans.isError || picks.isError;

  const orderedPlans = plans.data
    ? [...plans.data].sort((a, b) => PLAN_ORDER.indexOf(a.code) - PLAN_ORDER.indexOf(b.code))
    : undefined;

  return (
    <div className="px-6 py-8">
      <div>
        <h1 className="font-display text-2xl font-extrabold tracking-tight">Service catalog</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Standing services included with each plan, and the à la carte pick menu.
        </p>
      </div>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading the catalog.
        </div>
      )}

      {isError && !isLoading && (
        <div
          role="alert"
          className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>We couldn't load the catalog.</span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              void plans.refetch();
              void picks.refetch();
            }}
          >
            Try again
          </Button>
        </div>
      )}

      {orderedPlans && (
        <section className="mt-6">
          <h2 className="font-display text-lg font-bold">Included in each plan</h2>
          <div className="mt-3 grid gap-4 lg:grid-cols-3">
            {orderedPlans.map((plan) => (
              <div key={plan.code} className="rounded-2xl border border-border bg-card p-5">
                <div className="flex items-baseline justify-between gap-2">
                  <h3 className="font-display text-lg font-bold">{plan.displayName}</h3>
                  <span className="text-xs tabular-nums text-muted-foreground">
                    {formatCentsCAD(plan.monthlyPriceCents)}/mo
                  </span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {plan.services.length} standing services · {plan.visitsPerYear} visits/year
                </p>
                <ul className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
                  {plan.services.map((s) => (
                    <li key={s.name} className="flex items-center justify-between gap-2">
                      <span className="flex items-center gap-2">
                        <Wrench
                          className="h-3.5 w-3.5 shrink-0 text-muted-foreground"
                          aria-hidden="true"
                        />
                        {s.name}
                      </span>
                      <span className="flex shrink-0 items-center gap-2">
                        <span
                          className={cn(
                            "rounded-full px-2 py-0.5 text-xs font-medium",
                            TIER_CLASS_TONE[s.tierClass],
                          )}
                        >
                          {TIER_CLASS_LABEL[s.tierClass]}
                        </span>
                        <span className="tabular-nums text-xs text-muted-foreground">
                          {s.frequencyPerYear}x/yr
                        </span>
                      </span>
                    </li>
                  ))}
                  {plan.services.length === 0 && (
                    <li className="text-sm text-muted-foreground">No standing services listed.</li>
                  )}
                </ul>
              </div>
            ))}
          </div>
        </section>
      )}

      {picks.data && (
        <section className="mt-8">
          <h2 className="font-display text-lg font-bold">À la carte picks</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            The pickable services menu customers choose from, grouped by price band.
          </p>
          <div className="mt-3 grid gap-4 lg:grid-cols-3">
            {(["basic", "medium", "premium"] as const).map((band) => {
              const group = picks.data[band];
              return (
                <div key={band} className="rounded-2xl border border-border bg-card p-5">
                  <div className="flex items-baseline justify-between gap-2">
                    <h3 className="font-display text-lg font-bold">
                      {TIER_CLASS_LABEL[band.toUpperCase() as TierClass]}
                    </h3>
                    <span className="text-xs tabular-nums text-muted-foreground">
                      {formatCentsCAD(group.aLaCartePriceCents)} each
                    </span>
                  </div>
                  <ul className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
                    {group.services.map((s) => (
                      <li key={s.id} className="flex items-center justify-between gap-2">
                        <span>{s.name}</span>
                        <span className="flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
                          <span>{CATEGORY_LABEL[s.category]}</span>
                          <span className="tabular-nums">{s.defaultDurationMinutes}m</span>
                        </span>
                      </li>
                    ))}
                    {group.services.length === 0 && (
                      <li className="text-sm text-muted-foreground">No picks in this band yet.</li>
                    )}
                  </ul>
                </div>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
}
