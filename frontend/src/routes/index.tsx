import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { BASE_URL, OG_IMAGE_DEFAULT, buildLocalBusinessSchema, canonicalUrl } from "@/lib/seo";
import { PLANS, annualMonthlyEquivalent } from "@/lib/plans";
import {
  ArrowRight,
  Bell,
  Calendar,
  Camera,
  ClipboardList,
  Droplets,
  Footprints,
  Home,
  Leaf,
  Lightbulb,
  Check,
  Wind,
  Wrench,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { useRevealGroup } from "@/hooks/use-reveal";
import heroHome from "@/assets/hero-home.jpg";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "HomeKept: Home maintenance on autopilot, GTA" },
      {
        name: "description",
        content:
          "HomeKept is a monthly subscription that handles routine home maintenance, including HVAC, gutters, plumbing, and smart-home devices, for homeowners in Oakville, Mississauga, and Milton.",
      },
      { property: "og:title", content: "HomeKept: Home maintenance on autopilot" },
      {
        property: "og:description",
        content:
          "Founder-run visits, seasonal checklists, photo reports. Book a free 90-minute walk-through.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: BASE_URL },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
      { "script:ld+json": buildLocalBusinessSchema() },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/") }],
  }),
  component: LandingPage,
});

function LandingPage() {
  return (
    <div className="min-h-dvh overflow-x-clip bg-background">
      <AmbientGlows />
      <SiteNav />
      <main id="main" className="relative">
        <Hero />
        <ServicesMarquee />
        <HowItWorks />
        <WhatsIncluded />
        <PlansPreview />
        <HealthScorePanel />
        <Personas />
        <FinalCTA />
      </main>
      <SiteFooter />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Ambient background light                                                   */
/* -------------------------------------------------------------------------- */

function AmbientGlows() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
      <div className="absolute -right-36 -top-44 size-[560px] animate-drift rounded-full bg-sage/40 blur-[90px]" />
      <div className="absolute -left-52 top-[45vh] size-[460px] animate-drift rounded-full bg-honey-soft/50 blur-[90px] [animation-direction:alternate-reverse]" />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Hero                                                                       */
/* -------------------------------------------------------------------------- */

function Squiggle() {
  return (
    <svg
      aria-hidden="true"
      className="absolute -bottom-2 left-0 h-3.5 w-full overflow-visible"
      viewBox="0 0 220 14"
      preserveAspectRatio="none"
    >
      <path
        d="M3 9 Q 28 2, 55 8 T 110 8 T 165 8 T 217 7"
        pathLength={1}
        className="fill-none stroke-accent"
        style={{
          strokeWidth: 3.5,
          strokeLinecap: "round",
          strokeDasharray: 1,
          strokeDashoffset: 1,
          animation: "draw 1s ease 0.9s forwards",
        }}
      />
    </svg>
  );
}

function Hero() {
  return (
    <section className="relative mx-auto max-w-6xl px-6 pb-28 pt-16 md:pt-24">
      <div className="grid items-center gap-14 lg:grid-cols-[1.1fr_0.9fr]">
        <div>
          <div className="animate-reveal inline-flex items-center gap-2.5 rounded-full border border-border bg-card px-4 py-2 text-[13px] font-semibold text-primary shadow-soft">
            <span
              className="size-2 rounded-full bg-accent motion-safe:animate-pulse"
              aria-hidden="true"
            />
            Serving Oakville · Mississauga · Milton
          </div>

          <h1 className="mt-7 font-display text-[clamp(34px,5.8vw,82px)] font-[560] leading-[1.04] tracking-[-0.025em] text-primary">
            <span className="block overflow-hidden">
              <span className="block animate-rise">Home maintenance</span>
            </span>
            <span className="block overflow-hidden">
              <span className="block animate-rise [animation-delay:120ms]">
                on{" "}
                <em className="relative whitespace-nowrap font-[480] italic text-moss">
                  autopilot.
                  <Squiggle />
                </em>
              </span>
            </span>
          </h1>

          <p className="mt-7 max-w-[46ch] animate-reveal text-lg leading-relaxed text-muted-foreground [animation-delay:450ms]">
            A monthly subscription for homeowners who'd rather not spend weekends on filters,
            gutters, and inspections. We schedule the visits, run them ourselves, and report back
            with photos, so your home stays cared for without sitting on your to-do list.
          </p>

          <div className="mt-9 flex animate-reveal flex-col items-start gap-5 [animation-delay:600ms] sm:flex-row sm:items-center">
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

        <div className="relative animate-reveal [animation-delay:300ms]">
          <div
            aria-hidden="true"
            className="absolute -left-[8%] -top-[4%] -z-10 h-[108%] w-[115%] animate-breathe bg-[radial-gradient(circle_at_35%_35%,theme(colors.honey-soft/85%),theme(colors.sage/35%)_65%,transparent_75%)]"
          />
          <div className="aspect-[4/5] max-h-[560px] w-full overflow-hidden rounded-b-[36px] rounded-t-[200px] bg-gradient-to-br from-sage via-moss to-primary shadow-lift">
            <img
              src={heroHome}
              alt="A calm, sunlit interior of a modern Canadian home"
              width={1280}
              height={1600}
              className="size-full object-cover"
            />
          </div>
          <HealthMiniPill />
          <FloatingVisitCard />
        </div>
      </div>
    </section>
  );
}

function HealthMiniPill() {
  return (
    <div className="absolute -right-2 top-9 animate-bob rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground shadow-lift [--bob-rotate:3deg] [animation-duration:7s] md:-right-4">
      Home health <span className="text-honey-soft">84 ↑</span>
    </div>
  );
}

function FloatingVisitCard() {
  // Computed client-side so SSR markup can't disagree with the hydrated date.
  const [date, setDate] = useState<string | null>(null);
  useEffect(() => {
    const d = new Date();
    d.setDate(d.getDate() + ((4 - d.getDay() + 7) % 7 || 7));
    setDate(d.toLocaleDateString("en-CA", { weekday: "long", month: "long", day: "numeric" }));
  }, []);

  return (
    <div className="absolute -bottom-7 left-0 w-[280px] animate-bob rounded-3xl border border-border bg-card p-4.5 shadow-lift [--bob-rotate:-1.2deg] sm:-left-8">
      <div className="mb-2.5 flex items-center justify-between">
        <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
          Example visit
        </p>
        <span className="rounded-full bg-moss/15 px-2.5 py-1 text-[11px] font-bold text-moss">
          Scheduled
        </span>
      </div>
      <p className="font-display text-lg font-semibold text-primary">{date ?? "Thursday"}</p>
      <p className="mt-0.5 text-xs text-muted-foreground">
        Furnace filter · Smoke detector test · Gutter clearing
      </p>
      <p className="mt-3 border-t border-dashed border-border pt-3 text-xs text-muted-foreground">
        Shown: an example visit.
      </p>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Services marquee                                                           */
/* -------------------------------------------------------------------------- */

const marqueeItems = [
  "Furnace filters",
  "Gutter clearing",
  "Smoke & CO tests",
  "Water heater flush",
  "Thermostat tune-up",
  "Leak inspections",
  "Dryer vents",
  "Seasonal checks",
];

function MarqueeRun() {
  return (
    <span className="flex items-center">
      {marqueeItems.map((item) => (
        <span
          key={item}
          className="flex items-center gap-7 whitespace-nowrap px-7 text-sm font-medium tracking-wide"
        >
          {item}
          <Leaf className="size-3.5 text-accent" aria-hidden="true" />
        </span>
      ))}
    </span>
  );
}

function ServicesMarquee() {
  return (
    <div className="mx-auto max-w-6xl px-6 pt-2" aria-hidden="true">
      <div className="group -mx-2 -rotate-1 overflow-hidden rounded-full bg-primary py-4 text-primary-foreground">
        <div className="flex w-max animate-marquee group-hover:[animation-play-state:paused]">
          <MarqueeRun />
          <MarqueeRun />
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Section header                                                             */
/* -------------------------------------------------------------------------- */

function SectionHead({
  eyebrow,
  children,
  centered = false,
}: {
  eyebrow: string;
  children: React.ReactNode;
  centered?: boolean;
}) {
  return (
    <header className={centered ? "mx-auto max-w-2xl text-center" : "max-w-2xl"}>
      <p
        className={
          "flex items-center gap-3 text-xs font-bold uppercase tracking-[0.14em] text-accent " +
          (centered ? "justify-center" : "")
        }
      >
        <span className="h-0.5 w-7 rounded-full bg-accent" aria-hidden="true" />
        {eyebrow}
      </p>
      <h2 className="mt-4 font-display text-[clamp(28px,4.2vw,56px)] font-[560] leading-[1.08] tracking-[-0.02em] text-primary">
        {children}
      </h2>
    </header>
  );
}

/* -------------------------------------------------------------------------- */
/* How it works                                                               */
/* -------------------------------------------------------------------------- */

const steps = [
  {
    icon: Footprints,
    title: "We walk the home.",
    body: "A 90-minute, no-obligation visit. We catalog every system, including HVAC, plumbing, exterior, and electrical, and ask how you live in the space.",
  },
  {
    icon: ClipboardList,
    title: "We build your plan.",
    body: "A written maintenance plan tailored to your home: what we'll do, when we'll be there, and what it costs. No surprise upsells, ever.",
  },
  {
    icon: Calendar,
    title: "We carry it out.",
    body: "Visits appear in the app. After each one, you get a photo report: what we did, what we noticed, and what's coming next.",
  },
];

function HowItWorks() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section id="how" className="scroll-mt-24 py-28">
      <div ref={ref} className="relative mx-auto max-w-6xl px-6">
        <div data-reveal className="reveal-init">
          <SectionHead eyebrow="How it works" centered>
            Three steps, then we{" "}
            <em className="font-[480] italic text-moss">take it from there.</em>
          </SectionHead>
        </div>

        <svg
          aria-hidden="true"
          className="pointer-events-none absolute left-0 top-[190px] hidden h-[120px] w-full lg:block"
          viewBox="0 0 1100 120"
          preserveAspectRatio="none"
        >
          <path
            d="M 130 80 C 320 10, 420 110, 560 60 C 700 10, 820 100, 980 50"
            className="fill-none stroke-sage opacity-70"
            style={{ strokeWidth: 2.5, strokeDasharray: "8 10" }}
          />
        </svg>

        <ol className="mt-16 grid gap-8 md:grid-cols-3">
          {steps.map((s, i) => (
            <li
              key={s.title}
              data-reveal
              className={"reveal-init group relative pt-3 " + (i === 1 ? "md:translate-y-8" : "")}
              style={{ "--reveal-index": i } as React.CSSProperties}
            >
              <div className="grid size-[74px] place-items-center border border-border bg-card font-display text-[27px] font-semibold text-primary shadow-soft transition-all duration-300 [border-radius:50%_50%_50%_18%] group-hover:-rotate-6 group-hover:scale-110 group-hover:bg-accent group-hover:text-accent-foreground">
                {i + 1}
              </div>
              <h3 className="mt-6 font-display text-2xl font-semibold text-primary">{s.title}</h3>
              <p className="mt-2.5 text-[15px] leading-relaxed text-muted-foreground">{s.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* What's included                                                            */
/* -------------------------------------------------------------------------- */

const categories = [
  {
    icon: Wind,
    title: "HVAC & filters",
    items: ["Furnace filter swaps", "Coil & vent checks", "Humidifier service"],
  },
  {
    icon: Droplets,
    title: "Plumbing & seasonal",
    items: ["Leak inspections", "Outdoor tap shut-down", "Water heater flush"],
  },
  {
    icon: Home,
    title: "Exterior & gutters",
    items: ["Gutter clearing", "Roof & siding scan", "Walkway check"],
  },
  {
    icon: Lightbulb,
    title: "Smart-home & electrical",
    items: ["Smoke & CO tests", "Thermostat tune-up", "Smart device setup"],
  },
];

function WhatsIncluded() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section id="services" className="scroll-mt-24 px-6 pb-28">
      <div
        ref={ref}
        className="mx-auto max-w-6xl rounded-[56px] bg-card px-6 py-20 shadow-soft md:px-14"
      >
        <div data-reveal className="reveal-init">
          <SectionHead eyebrow="What's included">
            The routine work, <em className="font-[480] italic text-moss">handled.</em>
          </SectionHead>
        </div>

        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {categories.map((c, i) => (
            <article
              key={c.title}
              data-reveal
              className={
                "reveal-init group rounded-[28px] bg-background p-6 transition-all duration-300 hover:-translate-y-1.5 hover:shadow-lift " +
                (i % 2 === 1 ? "lg:translate-y-5" : "")
              }
              style={{ "--reveal-index": i } as React.CSSProperties}
            >
              <div className="grid size-12 place-items-center rounded-2xl bg-moss/15 text-primary transition-colors duration-300 group-hover:bg-primary group-hover:text-honey-soft">
                <c.icon className="size-5" aria-hidden="true" />
              </div>
              <h3 className="mt-4.5 font-display text-xl font-semibold text-primary">{c.title}</h3>
              <ul className="mt-3 space-y-2">
                {c.items.map((item) => (
                  <li key={item} className="flex items-start gap-2 text-sm text-muted-foreground">
                    <Check className="mt-0.5 size-3.5 shrink-0 text-accent" aria-hidden="true" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Plans preview                                                              */
/* -------------------------------------------------------------------------- */

// "2 months free" applies uniformly across tiers (annualPriceCad = monthlyPriceCad × 10 for
// every plan), so the savings percentage is the same regardless of which plan we compute it from.
const annualSavingsPct = Math.round(
  ((PLANS[0].monthlyPriceCad - annualMonthlyEquivalent(PLANS[0])) / PLANS[0].monthlyPriceCad) * 100,
);

function PlansPreview() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section id="plans-preview" className="scroll-mt-24 pb-28">
      <div ref={ref} className="mx-auto max-w-6xl px-6">
        <div data-reveal className="reveal-init">
          <SectionHead eyebrow="Plans" centered>
            Simple, monthly <em className="font-[480] italic text-moss">peace of mind.</em>
          </SectionHead>
        </div>

        <div className="mt-16 grid items-center gap-6 md:grid-cols-3">
          {PLANS.map((plan, i) => (
            <div
              key={plan.id}
              data-reveal
              className="reveal-init"
              style={{ "--reveal-index": i } as React.CSSProperties}
            >
              <PlanCard plan={plan} />
            </div>
          ))}
        </div>

        <p data-reveal className="reveal-init mt-9 text-center text-sm text-muted-foreground">
          All prices in CAD. Annual billing saves about {annualSavingsPct}% (two months free).
        </p>
      </div>
    </section>
  );
}

function PlanCard({ plan }: { plan: (typeof PLANS)[number] }) {
  const featured = plan.recommended;
  return (
    <article
      className={
        "relative flex flex-col rounded-[34px] p-8 transition-all duration-300 hover:-translate-y-2 " +
        (featured
          ? "bg-primary py-11 text-primary-foreground shadow-lift"
          : "border border-border bg-card hover:shadow-lift")
      }
    >
      {featured && (
        <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 animate-bob rounded-full bg-accent px-4 py-1.5 text-xs font-bold tracking-wide text-accent-foreground shadow-soft [animation-duration:5s]">
          Recommended
        </div>
      )}
      <h3 className={"font-display text-[23px] font-semibold " + (featured ? "" : "text-primary")}>
        {plan.emoji} {plan.name}
      </h3>
      <p className={"mt-1 text-[13px] " + (featured ? "text-sage" : "text-muted-foreground")}>
        {plan.visitsDescription}
      </p>
      <div className="mt-5 flex items-baseline gap-1.5">
        <span className="font-display text-[54px] font-semibold leading-none tracking-tight">
          ${plan.monthlyPriceCad}
        </span>
        <span className={featured ? "text-sm text-sage" : "text-sm text-muted-foreground"}>
          /mo
        </span>
      </div>

      <ul
        className={
          "mt-6 flex-1 space-y-2.5 border-t border-dashed pt-5 " +
          (featured ? "border-primary-foreground/20" : "border-border")
        }
      >
        {plan.features.map((f) => (
          <li key={f} className="flex items-start gap-2.5 text-sm">
            <Check className="mt-0.5 size-3.5 shrink-0 text-accent" aria-hidden="true" />
            <span className={featured ? "text-primary-foreground/90" : ""}>{f}</span>
          </li>
        ))}
      </ul>

      <div className="mt-7">
        <Button asChild variant={featured ? "accent" : "outline"} size="lg" className="w-full">
          <Link to="/plans" search={{ tier: plan.id }}>
            Choose {plan.name}
          </Link>
        </Button>
      </div>
    </article>
  );
}

/* -------------------------------------------------------------------------- */
/* Health score panel (illustrative product UI)                               */
/* -------------------------------------------------------------------------- */

const RING_R = 105;
const RING_C = 2 * Math.PI * RING_R;
const DEMO_SCORE = 84;

function HealthRing() {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(false);
  const [num, setNum] = useState(0);

  useEffect(() => {
    const el = ref.current;
    const reduced =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!el || reduced || typeof IntersectionObserver === "undefined") {
      setActive(true);
      setNum(DEMO_SCORE);
      return;
    }
    let raf = 0;
    const io = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        setActive(true);
        const t0 = performance.now();
        const tick = (t: number) => {
          const p = Math.min((t - t0) / 1800, 1);
          setNum(Math.round(DEMO_SCORE * (1 - Math.pow(1 - p, 3))));
          if (p < 1) raf = requestAnimationFrame(tick);
        };
        raf = requestAnimationFrame(tick);
        io.disconnect();
      },
      { threshold: 0.5 },
    );
    io.observe(el);
    return () => {
      io.disconnect();
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <div ref={ref} className="relative mx-auto size-60">
      <svg viewBox="0 0 240 240" className="size-full -rotate-90">
        <circle
          cx="120"
          cy="120"
          r={RING_R}
          className="fill-none stroke-primary-foreground/15"
          strokeWidth="14"
        />
        <circle
          cx="120"
          cy="120"
          r={RING_R}
          className="fill-none stroke-accent transition-[stroke-dashoffset] duration-[1800ms] ease-out"
          strokeWidth="14"
          strokeLinecap="round"
          strokeDasharray={RING_C}
          strokeDashoffset={active ? RING_C * (1 - DEMO_SCORE / 100) : RING_C}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">
        <div>
          <div className="font-display text-6xl font-semibold leading-none">{num}</div>
          <div className="mt-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-sage">
            Home health
          </div>
        </div>
      </div>
    </div>
  );
}

const healthFeatures = [
  { icon: Camera, label: "Photo reports" },
  { icon: Leaf, label: "Seasonal checklists" },
  { icon: Bell, label: "Gentle reminders" },
  { icon: Wrench, label: "Same technician, every time" },
];

function HealthScorePanel() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section className="px-6 pb-28">
      <div
        ref={ref}
        data-reveal
        className="reveal-init relative mx-auto max-w-6xl overflow-hidden rounded-[56px] bg-primary text-primary-foreground"
      >
        <div
          aria-hidden="true"
          className="absolute -right-24 -top-32 size-[420px] animate-drift rounded-full bg-accent/20 blur-[70px]"
        />
        <div className="relative grid items-center gap-12 px-8 py-16 md:grid-cols-[auto_1fr] md:gap-16 md:px-16 md:py-20">
          <HealthRing />
          <div>
            <p className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.14em] text-accent">
              <span className="h-0.5 w-7 rounded-full bg-accent" aria-hidden="true" />
              Always-on insight
            </p>
            <h2 className="mt-4 font-display text-[clamp(28px,4.2vw,56px)] font-[560] leading-[1.08] tracking-[-0.02em]">
              Every home gets a <em className="font-[480] italic text-sage">health score.</em>
            </h2>
            <p className="mt-4 max-w-[54ch] leading-relaxed text-sage">
              After each visit, your score updates: a single number for how well your home's systems
              are doing, with anything that needs attention flagged and scheduled automatically.
              (Shown: an example home.)
            </p>
            <div className="mt-7 flex flex-wrap gap-2.5">
              {healthFeatures.map((f) => (
                <span
                  key={f.label}
                  className="inline-flex items-center gap-2 rounded-full border border-primary-foreground/15 bg-primary-foreground/10 px-4 py-2 text-[13px] text-primary-foreground/90"
                >
                  <f.icon className="size-3.5 text-honey-soft" aria-hidden="true" />
                  {f.label}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Personas                                                                   */
/* -------------------------------------------------------------------------- */

const personas = [
  {
    eyebrow: "For busy families",
    headline: "Weekends back. Home still cared for.",
    body: "Between work, kids, and weekends that disappear in a blink, the gutters never seem to get done. We put them on the calendar so you don't have to.",
    rotate: "-rotate-1",
  },
  {
    eyebrow: "For empty-nesters",
    headline: "Less climbing ladders. Same standards.",
    body: "You love the house. You're done with the physical work. Same technician each visit, calm communication, and proper attention to the systems that quietly age.",
    rotate: "rotate-1",
  },
  {
    eyebrow: "For first-time owners",
    headline: "Your home, finally explained.",
    body: "Nobody hands you a manual when you buy a house. We walk you through what your home needs, then we do it, so you learn as we go.",
    rotate: "-rotate-[0.5deg]",
  },
];

function Personas() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section id="who" className="scroll-mt-24 pb-28">
      <div ref={ref} className="mx-auto max-w-6xl px-6">
        <div data-reveal className="reveal-init">
          <SectionHead eyebrow="For every kind of homeowner" centered>
            We fit the home, <em className="font-[480] italic text-moss">and the people in it.</em>
          </SectionHead>
        </div>

        <div className="mt-16 grid gap-7 lg:grid-cols-3">
          {personas.map((p, i) => (
            <article
              key={p.eyebrow}
              data-reveal
              className={`reveal-init flex flex-col rounded-tl-[30px] rounded-tr-[30px] rounded-br-[30px] rounded-bl-md border border-border bg-card p-7 shadow-soft transition-transform duration-300 hover:rotate-0 hover:-translate-y-1 ${p.rotate}`}
              style={{ "--reveal-index": i } as React.CSSProperties}
            >
              <p className="text-xs font-bold uppercase tracking-[0.14em] text-accent">
                {p.eyebrow}
              </p>
              <h3 className="mt-3 font-display text-2xl font-semibold tracking-tight text-primary">
                {p.headline}
              </h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Final CTA                                                                  */
/* -------------------------------------------------------------------------- */

function FinalCTA() {
  const ref = useRevealGroup<HTMLDivElement>();
  return (
    <section className="px-6 pb-28">
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
          <h2 className="font-display text-[clamp(30px,4.8vw,66px)] font-[560] leading-[1.06] tracking-[-0.02em]">
            Let's walk your <em className="font-[480] italic text-honey-soft">home together.</em>
          </h2>
          <p className="mx-auto mt-5 max-w-[46ch] leading-relaxed text-sage">
            Ninety minutes, no obligation. You'll get a written maintenance plan within a week: keep
            it or don't. Either way, you'll finally know your house.
          </p>
          <div className="mt-9">
            <Button asChild size="xl" variant="accent">
              <Link to="/book">
                Book your free walk-through <ArrowRight className="size-4" />
              </Link>
            </Button>
          </div>
          <p className="mt-5 text-[13px] text-sage/80">
            No credit card · Cancel anytime · Proudly local to the GTA
          </p>
        </div>
      </div>
    </section>
  );
}
