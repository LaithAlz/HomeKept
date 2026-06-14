import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { useRevealGroup } from "@/hooks/use-reveal";
import { BASE_URL, OG_IMAGE_DEFAULT, buildLocalBusinessSchema } from "@/lib/seo";

const CITY_PATH = "/mississauga";
const CITY_URL = `${BASE_URL}${CITY_PATH}`;

export const Route = createFileRoute("/mississauga")({
  head: () => ({
    meta: [
      {
        title: "Home Maintenance Service Mississauga — HomeKept",
      },
      {
        name: "description",
        content:
          "HomeKept provides monthly home maintenance subscriptions in Mississauga, ON. We serve Port Credit, Lorne Park, Erin Mills, Streetsville, Meadowvale, and more.",
      },
      {
        property: "og:title",
        content: "Home Maintenance Subscription in Mississauga — HomeKept",
      },
      {
        property: "og:description",
        content:
          "Scheduled visits, vetted technicians, photo reports. Serving Mississauga neighbourhoods including Port Credit, Lorne Park, Erin Mills, and Streetsville.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: CITY_URL },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
      {
        "script:ld+json": buildLocalBusinessSchema({
          cityName: "Mississauga",
          cityUrl: CITY_URL,
        }),
      },
    ],
    links: [{ rel: "canonical", href: CITY_URL }],
  }),
  component: MississaugaPage,
});

// Mississauga FSAs — a large city with many postal prefixes.
// Sources: Canada Post forward sortation area map, Statistics Canada 2021.
// Western Mississauga (bordering Oakville/Milton) uses L4/L5 series.
const FSAS = [
  "L4T",
  "L4W",
  "L4X",
  "L4Y",
  "L4Z",
  "L5A",
  "L5B",
  "L5C",
  "L5E",
  "L5G",
  "L5H",
  "L5J",
  "L5K",
  "L5L",
  "L5M",
  "L5N",
  "L5R",
  "L5V",
  "L5W",
];

// Mississauga neighbourhoods — real, verifiable.
const NEIGHBOURHOODS = [
  "Port Credit",
  "Lorne Park",
  "Clarkson",
  "Erin Mills",
  "Streetsville",
  "Meadowvale",
  "Churchill Meadows",
  "Applewood",
  "Cooksville",
  "East Credit",
  "Malton",
  "Rathwood",
];

// Mississauga-specific seasonal pain points.
const SEASONAL = [
  {
    season: "Winter",
    heading: "Basement moisture in older Applewood and Cooksville stock",
    body: "Mississauga's older housing stock in Applewood, Rathwood, and Cooksville was built before modern drainage standards. Winter basement moisture checks — caulking, grading observations, sump pump health — catch water infiltration before it becomes a renovation.",
  },
  {
    season: "Spring",
    heading: "Sump pump and drainage in low-lying Meadowvale",
    body: "Parts of Meadowvale and Churchill Meadows sit in Credit River tributary drainage areas. A sump pump test and pit clean in March is essential: a failed pump during snowmelt can flood a finished basement in hours.",
  },
  {
    season: "Summer",
    heading: "Dryer vents and bath fans in tightly-built townhomes",
    body: "Erin Mills and Churchill Meadows include large blocks of semi-detached and townhome stock where dryer vents run long horizontal runs to the exterior. A dryer vent deep-clean in summer reduces fire risk and improves drying efficiency.",
  },
  {
    season: "Fall",
    heading: "Gutter clearing across Mississauga's wide tree canopy",
    body: "Mature silver maples and Norway maples across Lorne Park and Clarkson deposit leaves steadily through October. A post-leaf gutter clear before freeze prevents fascia damage and ice loads on older homes where gutters are already working hard.",
  },
];

const COVERAGE_CHECKLIST = [
  "HVAC filter swaps and furnace visual",
  "Smoke and CO detector tests",
  "Gutter clearing and downspout check",
  "Outdoor tap shut-down (fall) and reconnection (spring)",
  "Sump pump test and pit inspection",
  "Plumbing and under-sink leak check",
  "Photo report after every visit",
];

function AmbientGlows() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
      <div className="absolute -right-36 -top-44 size-[560px] animate-drift rounded-full bg-sage/40 blur-[90px]" />
      <div className="absolute -left-52 top-[45vh] size-[460px] animate-drift rounded-full bg-honey-soft/50 blur-[90px] [animation-direction:alternate-reverse]" />
    </div>
  );
}

function MississaugaPage() {
  return (
    <div className="min-h-dvh overflow-x-clip bg-background">
      <AmbientGlows />
      <SiteNav />
      <main id="main">
        <HeroSection />
        <ServiceAreaSection />
        <SeasonalSection />
        <CoverageSection />
        <CtaSection />
      </main>
      <SiteFooter />
    </div>
  );
}

function HeroSection() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section className="relative mx-auto max-w-6xl px-6 pb-20 pt-16 md:pt-24">
      <div ref={ref} className="max-w-3xl">
        <div
          data-reveal
          className="reveal-init inline-flex items-center gap-2.5 rounded-full border border-border bg-card px-4 py-2 text-[13px] font-semibold text-primary shadow-soft"
        >
          <span
            className="size-2 rounded-full bg-accent motion-safe:animate-pulse"
            aria-hidden="true"
          />
          Serving Mississauga, ON
        </div>

        <h1
          data-reveal
          className="reveal-init mt-7 font-display text-[clamp(32px,5.2vw,72px)] font-[560] leading-[1.05] tracking-[-0.025em] text-primary"
          style={{ "--reveal-index": 1 } as React.CSSProperties}
        >
          Home maintenance <em className="font-[480] italic text-moss">in Mississauga,</em>
          <br />
          on a schedule.
        </h1>

        <p
          data-reveal
          className="reveal-init mt-7 max-w-[52ch] text-lg leading-relaxed text-muted-foreground"
          style={{ "--reveal-index": 2 } as React.CSSProperties}
        >
          A monthly subscription for Mississauga homeowners who want their home looked after
          year-round — HVAC, gutters, plumbing, and seasonal checks — without the to-do list.
        </p>

        <div
          data-reveal
          className="reveal-init mt-9 flex flex-col items-start gap-5 sm:flex-row sm:items-center"
          style={{ "--reveal-index": 3 } as React.CSSProperties}
        >
          <Button asChild size="xl">
            <Link to="/book">
              Book free walk-through <ArrowRight className="size-4" />
            </Link>
          </Button>
          <div className="text-sm">
            <p className="font-semibold text-primary">90 minutes, no obligation</p>
            <p className="text-muted-foreground">Written plan within a week</p>
          </div>
        </div>
      </div>
    </section>
  );
}

function ServiceAreaSection() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section aria-labelledby="service-area-heading" className="px-6 pb-20">
      <div
        ref={ref}
        className="mx-auto max-w-6xl rounded-[48px] bg-card px-8 py-16 shadow-soft md:px-14"
      >
        <div data-reveal className="reveal-init">
          <p className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.14em] text-accent">
            <span className="h-0.5 w-7 rounded-full bg-accent" aria-hidden="true" />
            Where we work
          </p>
          <h2
            id="service-area-heading"
            className="mt-4 font-display text-[clamp(26px,3.8vw,48px)] font-[560] leading-[1.08] tracking-[-0.02em] text-primary"
          >
            Mississauga neighbourhoods we serve
          </h2>
        </div>

        <div className="mt-12 grid gap-10 md:grid-cols-2">
          <div
            data-reveal
            className="reveal-init"
            style={{ "--reveal-index": 1 } as React.CSSProperties}
          >
            <h3 className="font-display text-xl font-semibold text-primary">Neighbourhoods</h3>
            <ul
              className="mt-5 grid grid-cols-2 gap-x-6 gap-y-3"
              aria-label="Mississauga neighbourhoods served"
            >
              {NEIGHBOURHOODS.map((n) => (
                <li key={n} className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Check className="size-3.5 shrink-0 text-accent" aria-hidden="true" />
                  {n}
                </li>
              ))}
            </ul>
          </div>

          <div
            data-reveal
            className="reveal-init"
            style={{ "--reveal-index": 2 } as React.CSSProperties}
          >
            <h3 className="font-display text-xl font-semibold text-primary">Postal code areas</h3>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
              We serve Mississauga addresses whose postal code begins with:
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {FSAS.map((fsa) => (
                <span
                  key={fsa}
                  className="inline-block rounded-full border border-border bg-background px-3.5 py-1.5 font-mono text-sm font-semibold text-primary"
                >
                  {fsa}
                </span>
              ))}
            </div>
            <p className="mt-5 text-sm text-muted-foreground">
              Not sure if we cover your street?{" "}
              <Link
                to="/book"
                className="font-semibold text-primary underline-offset-4 hover:underline"
              >
                Book a walk-through
              </Link>{" "}
              and we'll confirm before we schedule.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

function SeasonalSection() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section aria-labelledby="seasonal-heading" className="px-6 pb-20">
      <div ref={ref} className="mx-auto max-w-6xl">
        <div data-reveal className="reveal-init">
          <p className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.14em] text-accent">
            <span className="h-0.5 w-7 rounded-full bg-accent" aria-hidden="true" />
            Local conditions
          </p>
          <h2
            id="seasonal-heading"
            className="mt-4 font-display text-[clamp(26px,3.8vw,48px)] font-[560] leading-[1.08] tracking-[-0.02em] text-primary"
          >
            What Mississauga homes{" "}
            <em className="font-[480] italic text-moss">need most, by season</em>
          </h2>
          <p className="mt-4 max-w-[60ch] text-base leading-relaxed text-muted-foreground">
            Mississauga's mix of housing eras — 1960s stock in Cooksville, newer builds in Churchill
            Meadows — means maintenance priorities vary by neighbourhood.
          </p>
        </div>

        <div className="mt-14 grid gap-6 md:grid-cols-2">
          {SEASONAL.map((item, i) => (
            <article
              key={item.season}
              data-reveal
              className="reveal-init rounded-[28px] border border-border bg-card p-7 transition-all duration-300 hover:-translate-y-1 hover:shadow-lift"
              style={{ "--reveal-index": i } as React.CSSProperties}
            >
              <span className="inline-block rounded-full bg-moss/15 px-3 py-1 text-xs font-bold uppercase tracking-wider text-moss">
                {item.season}
              </span>
              <h3 className="mt-4 font-display text-xl font-semibold text-primary">
                {item.heading}
              </h3>
              <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">{item.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function CoverageSection() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section aria-labelledby="coverage-heading" className="px-6 pb-20">
      <div
        ref={ref}
        className="relative mx-auto max-w-6xl overflow-hidden rounded-[56px] bg-primary px-8 py-16 text-primary-foreground md:px-16 md:py-20"
      >
        <div
          aria-hidden="true"
          className="absolute -right-24 -top-32 size-[420px] animate-drift rounded-full bg-accent/20 blur-[70px]"
        />
        <div className="relative grid gap-12 md:grid-cols-[1fr_auto]">
          <div>
            <p className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.14em] text-accent">
              <span className="h-0.5 w-7 rounded-full bg-accent" aria-hidden="true" />
              Every visit includes
            </p>
            <h2
              id="coverage-heading"
              className="mt-4 font-display text-[clamp(26px,3.8vw,48px)] font-[560] leading-[1.08] tracking-[-0.02em]"
            >
              The whole home, <em className="font-[480] italic text-sage">looked after.</em>
            </h2>
            <ul className="mt-8 space-y-3" aria-label="What's included in every visit">
              {COVERAGE_CHECKLIST.map((item) => (
                <li
                  key={item}
                  className="flex items-start gap-3 text-sm text-primary-foreground/90"
                >
                  <Check className="mt-0.5 size-3.5 shrink-0 text-accent" aria-hidden="true" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
          <div className="flex items-center">
            <Button asChild size="xl" variant="accent">
              <Link to="/plans">
                See all plans <ArrowRight className="size-4" />
              </Link>
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}

function CtaSection() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section className="px-6 pb-28" aria-labelledby="cta-heading">
      <div
        ref={ref}
        data-reveal
        className="reveal-init relative mx-auto max-w-6xl overflow-hidden rounded-[56px] bg-gradient-to-br from-pine-2 to-primary px-8 py-20 text-center text-primary-foreground md:py-24"
      >
        <div
          aria-hidden="true"
          className="absolute -bottom-32 -left-20 size-96 animate-drift rounded-full bg-accent/20 blur-[70px]"
        />
        <div
          aria-hidden="true"
          className="absolute -right-16 -top-24 size-72 animate-drift rounded-full bg-sage/25 blur-[60px] [animation-direction:alternate-reverse]"
        />
        <div className="relative">
          <h2
            id="cta-heading"
            className="font-display text-[clamp(28px,4.4vw,60px)] font-[560] leading-[1.06] tracking-[-0.02em]"
          >
            Start with a free{" "}
            <em className="font-[480] italic text-honey-soft">Mississauga walk-through.</em>
          </h2>
          <p className="mx-auto mt-5 max-w-[46ch] leading-relaxed text-sage">
            Ninety minutes, no obligation. We'll walk your home, note every system, and send you a
            written maintenance plan — keep it or don't.
          </p>
          <div className="mt-9">
            <Button asChild size="xl" variant="accent">
              <Link to="/book">
                Book your free walk-through <ArrowRight className="size-4" />
              </Link>
            </Button>
          </div>
          <p className="mt-5 text-[13px] text-sage/80">
            No credit card · Cancel anytime · Proudly local to Mississauga
          </p>
        </div>
      </div>
    </section>
  );
}
