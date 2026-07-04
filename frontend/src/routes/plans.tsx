import { Fragment, useMemo, useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { BASE_URL, OG_IMAGE_DEFAULT, canonicalUrl } from "@/lib/seo";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Check, Loader2, Minus } from "lucide-react";
import { Button, type ButtonProps } from "@/components/ui/button";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { cn } from "@/lib/utils";
import { PLANS, formatCad, annualMonthlyEquivalent, type Plan, type PlanId } from "@/lib/plans";
import { getSession } from "@/lib/auth";
import { ApiError, post } from "@/lib/api";

const searchSchema = z.object({
  tier: fallback(z.enum(["essential", "complete", "premier"]), "complete").default("complete"),
});

/** Maps the frontend's plan ids to the backend's `PlanCode` enum values. */
const PLAN_CODE: Record<PlanId, "ESSENTIAL" | "COMPLETE" | "PREMIER"> = {
  essential: "ESSENTIAL",
  complete: "COMPLETE",
  premier: "PREMIER",
};

type BillingCycle = "MONTHLY" | "ANNUAL";

interface CheckoutSessionResponse {
  checkoutUrl: string;
}

interface CheckoutState {
  status: "idle" | "loading";
  error: string | null;
}

const IDLE_CHECKOUT: CheckoutState = { status: "idle", error: null };

const GENERIC_CHECKOUT_ERROR = "Something went wrong starting checkout. Please try again.";

/**
 * Defense-in-depth for the redirect on a money path: the backend is the
 * source of truth for `checkoutUrl`, but a full-page `window.location`
 * redirect is worth double-checking client-side too. Only ever returns a URL
 * whose scheme is `https:` and whose host is exactly `checkout.stripe.com` or
 * a `*.stripe.com` subdomain (the leading dot in `.stripe.com` matters — it's
 * what keeps a host like `evilstripe.com` from matching). Returns `null` for
 * anything else, including unparseable strings.
 */
function trustedStripeCheckoutUrl(checkoutUrl: string): string | null {
  try {
    const u = new URL(checkoutUrl);
    if (
      u.protocol === "https:" &&
      (u.hostname === "checkout.stripe.com" || u.hostname.endsWith(".stripe.com"))
    ) {
      return u.toString();
    }
  } catch {
    /* unparseable — fall through to null below */
  }
  return null;
}

/**
 * Drives the "Choose {tier}" CTAs shared by the plan cards, the comparison
 * table, and the mobile breakdown. State is keyed by plan id so every
 * rendering of the same tier's CTA (there are three on desktop) reflects the
 * same in-flight/error state rather than only the one the visitor clicked.
 *
 * Signed-out visitors are sent to sign in and back
 * (`/signin?next=/plans` — a static literal, never derived from user input);
 * signed-in visitors get a Stripe Checkout session and a full-page redirect
 * to the (foreign-origin) checkout URL.
 */
function usePlanCheckout(billing: "monthly" | "annual") {
  const navigate = useNavigate();
  const [state, setState] = useState<Record<PlanId, CheckoutState>>({
    essential: IDLE_CHECKOUT,
    complete: IDLE_CHECKOUT,
    premier: IDLE_CHECKOUT,
  });

  async function startCheckout(tier: Plan) {
    setState((s) => ({ ...s, [tier.id]: { status: "loading", error: null } }));

    try {
      const session = await getSession();
      if (!session) {
        navigate({ to: "/signin", search: { next: "/plans" } });
        return;
      }

      const billingCycle: BillingCycle = billing === "monthly" ? "MONTHLY" : "ANNUAL";
      const { checkoutUrl } = await post<CheckoutSessionResponse>("/api/checkout/session", {
        planCode: PLAN_CODE[tier.id],
        billingCycle,
        // The founding-member rate is not yet surfaced in the frontend's plan
        // data (lib/plans.ts has no `foundingRateAvailable`), so we never opt
        // in from here; the backend re-validates the cap regardless.
        foundingRate: false,
      });

      const dest = trustedStripeCheckoutUrl(checkoutUrl);
      if (!dest) {
        setState((s) => ({ ...s, [tier.id]: { status: "idle", error: GENERIC_CHECKOUT_ERROR } }));
        return;
      }

      // Foreign origin (Stripe-hosted) — a full navigation, not the router.
      window.location.href = dest;
      // Left in "loading": the page is about to unload.
    } catch (err) {
      const message =
        err instanceof ApiError && err.status === 429
          ? "You've reached the limit for checkout attempts. Please try again in a few minutes."
          : GENERIC_CHECKOUT_ERROR;
      setState((s) => ({ ...s, [tier.id]: { status: "idle", error: message } }));
    }
  }

  return { state, startCheckout };
}

export const Route = createFileRoute("/plans")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Plans: HomeKept" },
      {
        name: "description",
        content:
          "Compare Essential, Complete, and Premier plans for HomeKept home maintenance. Monthly or annual billing. All prices in CAD.",
      },
      { property: "og:title", content: "HomeKept Plans: Essential, Complete, Premier" },
      {
        property: "og:description",
        content: "Pick the level of proactive home maintenance that fits your home. Pause anytime.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: `${BASE_URL}/plans` },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/plans") }],
  }),
  component: PlansPage,
});

type Cell = boolean | string;

type Row = { label: string; values: [Cell, Cell, Cell] };

type Group = { name: string; rows: Row[] };

const [essential, complete, premier] = PLANS;

const groups: Group[] = [
  {
    name: "Visits",
    rows: [
      {
        label: "Scheduled visits per year",
        values: [
          String(essential.visitsPerYear),
          String(complete.visitsPerYear),
          String(premier.visitsPerYear),
        ],
      },
      {
        label: "Priority scheduling (48h + emergency line)",
        values: [
          essential.priorityScheduling,
          complete.priorityScheduling,
          premier.priorityScheduling,
        ],
      },
      {
        label: "Same-week scheduling + 24h emergency",
        values: [
          essential.sameWeekEmergency,
          complete.sameWeekEmergency,
          premier.sameWeekEmergency,
        ],
      },
      {
        label: "Dedicated technician (guaranteed same person)",
        values: [
          essential.dedicatedTechnician,
          complete.dedicatedTechnician,
          premier.dedicatedTechnician,
        ],
      },
    ],
  },
  {
    name: "Every visit",
    rows: [
      { label: "Filter checks & swaps", values: [true, true, true] },
      { label: "Smoke & CO detector tests + batteries", values: [true, true, true] },
      { label: "Seasonal mechanicals walkaround", values: [true, true, true] },
      {
        label: "Your-list time per visit",
        values: ["~20 min", "~20 min", "Up to 1 hr, incl. minor repairs"],
      },
      { label: "Photo report + Home Health Score update", values: [true, true, true] },
    ],
  },
  {
    name: "Picks & extras",
    rows: [
      {
        label: "Included picks per year",
        values: [
          String(essential.includedPicks),
          String(complete.includedPicks),
          String(premier.includedPicks),
        ],
      },
      {
        label: "Max Premium picks included",
        values: [
          String(essential.maxPremiumPicks),
          String(complete.maxPremiumPicks),
          String(premier.maxPremiumPicks),
        ],
      },
      {
        label: "Licensed gas tune-up coordination",
        values: [
          essential.gasTuneupCoordination,
          complete.gasTuneupCoordination,
          premier.gasTuneupCoordination,
        ],
      },
      {
        label: "Smart-home support",
        values: [essential.smartHomeSupport, complete.smartHomeSupport, premier.smartHomeSupport],
      },
      {
        label: "Minor repairs included (parts at cost)",
        values: [essential.repairsIncluded, complete.repairsIncluded, premier.repairsIncluded],
      },
      {
        label: "Annual Home Plan (5-yr capital forecast)",
        values: [essential.annualHomePlan, complete.annualHomePlan, premier.annualHomePlan],
      },
    ],
  },
  {
    name: "Flexibility",
    rows: [{ label: "Pause anytime, up to 3 months a year", values: [true, true, true] }],
  },
];

const faqs = [
  {
    q: "Can I pause my plan?",
    a: "Yes, pause anytime for up to three months per year. Useful for travel or seasonal homes. We'll keep your schedule and pick right back up when you're ready.",
  },
  {
    q: "What if I need work outside the plan?",
    a: "We'll send a transparent, line-itemed quote before any non-routine work begins.",
  },
  {
    q: "Who are the technicians?",
    a: "Right now, HomeKept visits are run by our founders, based locally in the GTA. Every visit is photo-documented, and we stick to a clear scope: routine maintenance and visual checks, never licensed-trade work like gas, electrical, or plumbing repairs. When something needs a licensed trade, we refer you to a vetted partner.",
  },
  {
    q: "What happens if I cancel?",
    a: "Cancel anytime in two clicks. No fees, no questions. You keep full access through the end of your current billing cycle.",
  },
  {
    q: "What if I'm not home during a visit?",
    a: "Most subscribers grant access via a lockbox, smart lock, or garage code. You'll receive a photo report whether you're home or not.",
  },
  {
    q: "Do you serve my neighbourhood?",
    a: "We currently serve Oakville, Mississauga, and Milton. If you're nearby and not sure, book a walk-through. We'll let you know upfront.",
  },
];

function CellMark({ value }: { value: Cell }) {
  if (value === true) {
    return (
      <span className="inline-flex items-center justify-center">
        <Check className="size-5 text-accent" aria-label="Included" />
      </span>
    );
  }
  if (value === false) {
    return (
      <span className="inline-flex items-center justify-center text-muted-foreground/60">
        <Minus className="size-5" aria-label="Not included" />
      </span>
    );
  }
  return <span className="text-sm font-semibold text-foreground">{value}</span>;
}

function PlansPage() {
  const { tier: highlightedTier } = Route.useSearch();
  const [billing, setBilling] = useState<"monthly" | "annual">("monthly");
  const { state: checkoutState, startCheckout } = usePlanCheckout(billing);

  const annualSavings = useMemo(() => {
    const monthlyEq = annualMonthlyEquivalent(complete);
    return Math.round(((complete.monthlyPriceCad - monthlyEq) / complete.monthlyPriceCad) * 100);
  }, []);

  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main">
        {/* Hero */}
        <section className="border-b border-border bg-surface/60">
          <div className="mx-auto max-w-5xl px-6 py-20 text-center md:py-28">
            <p className="text-xs font-bold uppercase tracking-[0.22em] text-accent">
              Plans &amp; pricing
            </p>
            <h1 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-6xl">
              One simple subscription.
              <br />
              Your whole home, looked after.
            </h1>
            <p className="mx-auto mt-5 max-w-2xl text-base text-muted-foreground md:text-lg">
              Pick the level of care that fits your home. Every plan is month-to-month, pausable,
              and cancellable in two clicks. All prices in Canadian dollars.
            </p>

            <BillingToggle billing={billing} setBilling={setBilling} savings={annualSavings} />
          </div>
        </section>

        {/* Plan cards */}
        <section className="mx-auto max-w-7xl px-6 py-16 md:py-20">
          <div className="grid gap-6 md:grid-cols-3 md:gap-6">
            {PLANS.map((p) => (
              <PlanCard
                key={p.id}
                tier={p}
                billing={billing}
                highlighted={highlightedTier === p.id}
                checkout={checkoutState[p.id]}
                onChoose={() => startCheckout(p)}
              />
            ))}
          </div>
          <p className="mt-6 text-center text-xs text-muted-foreground">
            Prices in CAD plus HST. Annual plans billed once per year.
          </p>
        </section>

        {/* Comparison table */}
        <section className="border-t border-border bg-surface/40">
          <div className="mx-auto max-w-7xl px-6 py-20">
            <div className="mx-auto max-w-3xl text-center">
              <p className="text-xs font-bold uppercase tracking-[0.22em] text-accent">Compare</p>
              <h2 className="mt-3 font-display text-3xl font-extrabold tracking-tight md:text-5xl">
                Every feature, side by side.
              </h2>
              <p className="mt-4 text-muted-foreground">
                The full picture so you can pick with confidence.
              </p>
            </div>

            {/* Desktop / tablet: scrollable table */}
            <div className="mt-12 hidden md:block">
              <ComparisonTable
                billing={billing}
                highlightedTier={highlightedTier}
                checkoutState={checkoutState}
                onChoose={startCheckout}
              />
            </div>

            {/* Mobile: stacked per-tier */}
            <div className="mt-10 space-y-8 md:hidden">
              {PLANS.map((p, pi) => (
                <MobileTierBreakdown
                  key={p.id}
                  tier={p}
                  billing={billing}
                  columnIndex={pi}
                  checkout={checkoutState[p.id]}
                  onChoose={() => startCheckout(p)}
                />
              ))}
            </div>
          </div>
        </section>

        {/* FAQ */}
        <section className="mx-auto max-w-3xl px-6 py-20">
          <div className="text-center">
            <p className="text-xs font-bold uppercase tracking-[0.22em] text-accent">FAQ</p>
            <h2 className="mt-3 font-display text-3xl font-extrabold tracking-tight md:text-5xl">
              Questions, answered.
            </h2>
          </div>
          <dl className="mt-12 divide-y divide-border rounded-3xl border border-border bg-card">
            {faqs.map((f) => (
              <div key={f.q} className="p-6 md:p-8">
                <dt className="font-display text-lg font-bold text-foreground md:text-xl">{f.q}</dt>
                <dd className="mt-2 text-muted-foreground">{f.a}</dd>
              </div>
            ))}
          </dl>
        </section>

        {/* Final CTA */}
        <section className="border-t border-border bg-primary text-primary-foreground">
          <div className="mx-auto max-w-4xl px-6 py-20 text-center md:py-24">
            <h2 className="font-display text-3xl font-extrabold tracking-tight md:text-5xl">
              Not ready to subscribe?
            </h2>
            <p className="mx-auto mt-4 max-w-xl text-base text-primary-foreground/80 md:text-lg">
              Start with a free 90-minute walk-through. We'll assess your home and email a custom
              maintenance plan the next day: no obligation.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Button asChild size="xl" variant="accent">
                <Link to="/book">Book a free walk-through</Link>
              </Button>
              <Button
                asChild
                size="xl"
                variant="outline"
                className="border-primary-foreground/40 bg-transparent text-primary-foreground hover:bg-primary-foreground hover:text-primary"
              >
                <Link to="/">Back to home</Link>
              </Button>
            </div>
          </div>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}

function BillingToggle({
  billing,
  setBilling,
  savings,
}: {
  billing: "monthly" | "annual";
  setBilling: (b: "monthly" | "annual") => void;
  savings: number;
}) {
  return (
    <div className="mt-10 flex flex-col items-center gap-3">
      <div
        role="radiogroup"
        aria-label="Billing period"
        className="inline-flex items-center rounded-full border border-border bg-background p-1 shadow-sm"
      >
        <button
          type="button"
          role="radio"
          aria-checked={billing === "monthly"}
          onClick={() => setBilling("monthly")}
          className={cn(
            "rounded-full px-5 py-3 text-sm font-semibold transition-colors",
            billing === "monthly"
              ? "bg-primary text-primary-foreground shadow"
              : "text-foreground/70 hover:text-foreground",
          )}
        >
          Monthly
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={billing === "annual"}
          onClick={() => setBilling("annual")}
          className={cn(
            "rounded-full px-5 py-3 text-sm font-semibold transition-colors",
            billing === "annual"
              ? "bg-primary text-primary-foreground shadow"
              : "text-foreground/70 hover:text-foreground",
          )}
        >
          Annual
        </button>
      </div>
      <p className="text-xs font-medium text-muted-foreground">
        Annual saves about <span className="font-bold text-accent">{savings}%</span>: roughly two
        months free.
      </p>
    </div>
  );
}

/** Shared CTA for all three renderings of a tier's "Choose {name}" action. */
function PlanCtaButton({
  tier,
  checkout,
  onChoose,
  variant = "default",
  size = "default",
  className,
}: {
  tier: Plan;
  checkout: CheckoutState;
  onChoose: () => void;
  variant?: ButtonProps["variant"];
  size?: ButtonProps["size"];
  className?: string;
}) {
  const errorId = `checkout-error-${tier.id}`;
  const loading = checkout.status === "loading";

  return (
    <div>
      <Button
        type="button"
        variant={variant}
        size={size}
        className={className}
        onClick={onChoose}
        disabled={loading}
        aria-describedby={checkout.error ? errorId : undefined}
      >
        {loading ? (
          <>
            Starting checkout... <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          </>
        ) : (
          `Choose ${tier.name}`
        )}
      </Button>
      {checkout.error && (
        <p id={errorId} role="alert" className="mt-2 text-xs font-semibold text-destructive">
          {checkout.error}
        </p>
      )}
    </div>
  );
}

function PlanCard({
  tier,
  billing,
  highlighted,
  checkout,
  onChoose,
}: {
  tier: Plan;
  billing: "monthly" | "annual";
  highlighted: boolean;
  checkout: CheckoutState;
  onChoose: () => void;
}) {
  const price = billing === "monthly" ? tier.monthlyPriceCad : annualMonthlyEquivalent(tier);
  const isFeatured = tier.recommended;
  const ring = highlighted
    ? "ring-2 ring-accent ring-offset-4 ring-offset-background"
    : isFeatured
      ? "ring-2 ring-primary ring-offset-4 ring-offset-background"
      : "";

  return (
    <div
      className={cn(
        "relative flex flex-col rounded-3xl border border-border bg-card p-8 shadow-sm transition-transform",
        isFeatured && "md:-translate-y-2",
        ring,
      )}
    >
      {isFeatured && (
        <span className="absolute -top-3 left-1/2 inline-flex -translate-x-1/2 items-center gap-1 rounded-full bg-accent px-3 py-1 text-xs font-bold uppercase tracking-wider text-accent-foreground shadow">
          Recommended
        </span>
      )}

      <h3 className="font-display text-2xl font-extrabold tracking-tight">
        {tier.emoji} {tier.name}
      </h3>
      <p className="mt-2 text-sm text-muted-foreground">{tier.tagline}</p>
      <p className="mt-1 min-h-[2.25rem] text-xs font-medium text-foreground/60">{tier.forWho}</p>

      <div className="mt-4 flex items-baseline gap-1">
        <span className="font-display text-5xl font-extrabold tracking-tight text-foreground">
          {formatCad(price)}
        </span>
        <span className="text-sm text-muted-foreground">/mo</span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        {billing === "monthly"
          ? `Billed monthly · ${formatCad(tier.monthlyPriceCad * 12)}/yr`
          : `Billed annually · ${formatCad(tier.annualPriceCad)}/yr`}
      </p>

      <PlanCtaButton
        tier={tier}
        checkout={checkout}
        onChoose={onChoose}
        size="lg"
        variant={isFeatured ? "accent" : "default"}
        className="mt-6 w-full"
      />

      <ul className="mt-8 space-y-3 text-sm">
        {tier.features.map((h) => (
          <li key={h} className="flex items-start gap-3">
            <Check className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
            <span className="text-foreground/90">{h}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ComparisonTable({
  billing,
  highlightedTier,
  checkoutState,
  onChoose,
}: {
  billing: "monthly" | "annual";
  highlightedTier: PlanId;
  checkoutState: Record<PlanId, CheckoutState>;
  onChoose: (tier: Plan) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-3xl border border-border bg-card shadow-sm">
      <table className="w-full min-w-[640px] border-collapse text-left">
        <thead>
          <tr className="border-b border-border">
            <th
              scope="col"
              className="w-[34%] p-6 text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground"
            >
              Features
            </th>
            {PLANS.map((t) => {
              const price = billing === "monthly" ? t.monthlyPriceCad : annualMonthlyEquivalent(t);
              const isHi = highlightedTier === t.id;
              return (
                <th key={t.id} scope="col" className={cn("p-6 align-top", isHi && "bg-accent/10")}>
                  <div className="flex flex-col">
                    <span className="font-display text-xl font-extrabold tracking-tight text-foreground">
                      {t.name}
                    </span>
                    <span className="mt-1 text-sm text-muted-foreground">
                      <span className="font-bold text-foreground">{formatCad(price)}</span>
                      /mo
                    </span>
                  </div>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <Fragment key={g.name}>
              <tr className="bg-surface/60">
                <th
                  scope="rowgroup"
                  colSpan={4}
                  className="p-4 text-xs font-bold uppercase tracking-[0.18em] text-foreground"
                >
                  {g.name}
                </th>
              </tr>
              {g.rows.map((r) => (
                <tr key={`${g.name}-${r.label}`} className="border-t border-border">
                  <th scope="row" className="p-4 text-sm font-medium text-foreground/90">
                    {r.label}
                  </th>
                  {r.values.map((v, i) => (
                    <td
                      key={i}
                      className={cn(
                        "p-4 text-center",
                        highlightedTier === PLANS[i].id && "bg-accent/5",
                      )}
                    >
                      <CellMark value={v} />
                    </td>
                  ))}
                </tr>
              ))}
            </Fragment>
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t border-border bg-surface/60">
            <td className="p-4" />
            {PLANS.map((t) => (
              <td key={t.id} className="p-4 text-center">
                <PlanCtaButton
                  tier={t}
                  checkout={checkoutState[t.id]}
                  onChoose={() => onChoose(t)}
                  size="sm"
                  variant={t.recommended ? "accent" : "outline"}
                />
              </td>
            ))}
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

function MobileTierBreakdown({
  tier,
  billing,
  columnIndex,
  checkout,
  onChoose,
}: {
  tier: Plan;
  billing: "monthly" | "annual";
  columnIndex: number;
  checkout: CheckoutState;
  onChoose: () => void;
}) {
  const price = billing === "monthly" ? tier.monthlyPriceCad : annualMonthlyEquivalent(tier);
  return (
    <div
      className={cn(
        "rounded-3xl border border-border bg-card p-6 shadow-sm",
        tier.recommended && "ring-2 ring-primary",
      )}
    >
      <div className="flex items-baseline justify-between">
        <h3 className="font-display text-2xl font-extrabold tracking-tight">{tier.name}</h3>
        <div className="text-right">
          <div className="font-display text-2xl font-extrabold">{formatCad(price)}</div>
          <div className="text-xs text-muted-foreground">/mo</div>
        </div>
      </div>
      <div className="mt-6 space-y-6">
        {groups.map((g) => (
          <div key={g.name}>
            <h4 className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground">
              {g.name}
            </h4>
            <ul className="mt-3 space-y-2 text-sm">
              {g.rows.map((r) => {
                const v = r.values[columnIndex];
                return (
                  <li
                    key={r.label}
                    className="flex items-start justify-between gap-4 border-b border-border/60 pb-2 last:border-0"
                  >
                    <span className="text-foreground/90">{r.label}</span>
                    <span className="shrink-0">
                      {typeof v === "boolean" ? (
                        v ? (
                          <Check className="size-4 text-accent" aria-label="Included" />
                        ) : (
                          <Minus
                            className="size-4 text-muted-foreground/60"
                            aria-label="Not included"
                          />
                        )
                      ) : (
                        <span className="font-semibold text-foreground">{v}</span>
                      )}
                    </span>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
      <PlanCtaButton
        tier={tier}
        checkout={checkout}
        onChoose={onChoose}
        size="lg"
        variant={tier.recommended ? "accent" : "default"}
        className="mt-6 w-full"
      />
    </div>
  );
}
