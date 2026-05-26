import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowRight,
  Calendar,
  ClipboardList,
  Footprints,
  Wind,
  Droplets,
  Home,
  Lightbulb,
  Check,
  CheckCircle2,
  Wrench,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import heroHome from "@/assets/hero-home.jpg";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "HomeKept — Home maintenance on autopilot, GTA" },
      {
        name: "description",
        content:
          "HomeKept is a monthly subscription that handles routine home maintenance — HVAC, gutters, plumbing, smart-home — for homeowners in Oakville, Mississauga, and Milton.",
      },
      { property: "og:title", content: "HomeKept — Home maintenance on autopilot" },
      {
        property: "og:description",
        content:
          "Vetted technicians, scheduled visits, photo reports. Book a free 90-minute walk-through.",
      },
    ],
  }),
  component: LandingPage,
});

function LandingPage() {
  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main">
        <Hero />
        <HowItWorks />
        <WhatsIncluded />
        <PlansPreview />
        <Personas />
        <FinalCTA />
      </main>
      <SiteFooter />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Hero                                                                       */
/* -------------------------------------------------------------------------- */

function Hero() {
  return (
    <section className="mx-auto max-w-7xl px-6 pt-12 pb-24 md:pt-20 md:pb-32">
      <div className="grid items-center gap-16 lg:grid-cols-12">
        <div className="animate-reveal space-y-7 lg:col-span-6">
          <div className="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1 text-[11px] font-bold uppercase tracking-wider">
            <span className="size-1.5 rounded-full bg-accent" aria-hidden="true" />
            Serving Oakville · Mississauga · Milton
          </div>

          <h1 className="text-balance font-display text-5xl font-extrabold leading-[1.02] tracking-tight md:text-7xl">
            Home maintenance on{" "}
            <span className="text-accent">autopilot.</span>
          </h1>

          <p className="max-w-[44ch] text-lg leading-relaxed text-muted-foreground">
            HomeKept is a monthly subscription for homeowners who'd rather not spend
            weekends on filters, gutters, and inspections. We schedule the visits, send
            vetted technicians, and report back with photos — so your home stays cared for
            without it sitting on your to-do list.
          </p>

          <div className="flex flex-col items-start gap-6 pt-2 sm:flex-row sm:items-center">
            <Button asChild size="xl">
              <Link to="/book">
                Book free walk-through <ArrowRight className="size-4" />
              </Link>
            </Button>
            <div className="text-sm">
              <p className="font-semibold text-foreground">90 minutes, no obligation</p>
              <p className="text-muted-foreground">Written plan delivered within a week</p>
            </div>
          </div>
        </div>

        <div className="animate-reveal relative lg:col-span-6 [animation-delay:120ms]">
          <div className="absolute -inset-4 -z-10 rounded-[40px] bg-surface" aria-hidden="true" />
          <img
            src={heroHome}
            alt="A calm, sunlit interior of a modern Canadian home with sage-green walls"
            width={1280}
            height={1600}
            className="aspect-[4/5] w-full rounded-[32px] object-cover shadow-soft"
          />
          <FloatingStatusCard />
        </div>
      </div>
    </section>
  );
}

function FloatingStatusCard() {
  return (
    <div className="absolute -bottom-8 left-4 hidden w-[280px] rounded-2xl border border-border bg-card p-4 shadow-lift sm:block md:-left-6">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
          Your next visit
        </p>
        <span className="rounded-full bg-success/10 px-2 py-0.5 text-[10px] font-bold text-success">
          Scheduled
        </span>
      </div>
      <p className="font-display text-lg font-bold">Thursday, Nov 14</p>
      <p className="text-xs text-muted-foreground">
        Furnace filter swap · Smoke detector test · Exterior winterization
      </p>
      <div className="mt-3 flex items-center gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
        <Wrench className="size-3.5 text-accent" aria-hidden="true" />
        <span>Marcus — your lead technician</span>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* How it works                                                               */
/* -------------------------------------------------------------------------- */

const steps = [
  {
    icon: Footprints,
    title: "We walk the home.",
    body: "A 90-minute, no-obligation visit. We catalog every system — HVAC, plumbing, exterior, electrical — and ask how you live in the space.",
  },
  {
    icon: ClipboardList,
    title: "We build the plan.",
    body: "A written maintenance plan tailored to your home: what we'll do, when we'll be there, and what it costs. No surprise upsells.",
  },
  {
    icon: Calendar,
    title: "We carry it out.",
    body: "Scheduled visits show up in the app. After each one, you get a photo report covering what we did, what we noticed, and what's next.",
  },
];

function HowItWorks() {
  return (
    <section id="how" className="scroll-mt-24 border-t border-border bg-surface py-24">
      <div className="mx-auto max-w-7xl px-6">
        <header className="mb-16 max-w-2xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">
            How it works
          </p>
          <h2 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
            Three steps, then we take it from there.
          </h2>
        </header>

        <ol className="grid gap-8 md:grid-cols-3">
          {steps.map((s, i) => (
            <li
              key={s.title}
              className="relative rounded-3xl border border-border bg-card p-8"
            >
              <div className="mb-6 flex items-center justify-between">
                <div className="grid size-12 place-items-center rounded-2xl bg-surface">
                  <s.icon className="size-5 text-accent" aria-hidden="true" />
                </div>
                <span className="font-display text-3xl font-extrabold tabular-nums text-muted-foreground/40">
                  0{i + 1}
                </span>
              </div>
              <h3 className="font-display text-xl font-bold tracking-tight">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.body}</p>
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
    items: ["Gutter clearing", "Roof & siding scan", "Driveway & walkway check"],
  },
  {
    icon: Lightbulb,
    title: "Smart-home & electrical",
    items: ["Smoke & CO tests", "Thermostat tune-up", "Smart device setup"],
  },
];

function WhatsIncluded() {
  return (
    <section id="services" className="scroll-mt-24 py-24">
      <div className="mx-auto max-w-7xl px-6">
        <header className="mb-16 max-w-2xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">
            What's included
          </p>
          <h2 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
            The routine work, handled.
          </h2>
        </header>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {categories.map((c) => (
            <article
              key={c.title}
              className="group rounded-3xl border border-border bg-card p-6 transition-all hover:-translate-y-0.5 hover:shadow-soft"
            >
              <div className="grid size-11 place-items-center rounded-2xl bg-accent/10 text-accent">
                <c.icon className="size-5" aria-hidden="true" />
              </div>
              <h3 className="mt-5 font-display text-lg font-bold">{c.title}</h3>
              <ul className="mt-3 space-y-2">
                {c.items.map((item) => (
                  <li
                    key={item}
                    className="flex items-start gap-2 text-sm text-muted-foreground"
                  >
                    <Check
                      className="mt-0.5 size-4 shrink-0 text-accent"
                      aria-hidden="true"
                    />
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

interface PlanTier {
  id: "essential" | "complete" | "premier";
  name: string;
  price: number;
  cadence: string;
  description: string;
  features: string[];
  featured?: boolean;
}

const plans: PlanTier[] = [
  {
    id: "essential",
    name: "Essential",
    price: 129,
    cadence: "4 visits per year · quarterly",
    description: "For modern townhomes and condos.",
    features: [
      "Quarterly HVAC filter swaps",
      "Smoke & CO detector tests",
      "Seasonal exterior check",
      "Photo report after every visit",
    ],
  },
  {
    id: "complete",
    name: "Complete",
    price: 189,
    cadence: "12 visits per year · monthly",
    description: "Proactive care for most detached homes.",
    features: [
      "Everything in Essential",
      "Monthly visits, year-round",
      "Plumbing & leak inspections",
      "Gutter clearing & exterior work",
      "Priority emergency scheduling",
    ],
    featured: true,
  },
  {
    id: "premier",
    name: "Premier",
    price: 289,
    cadence: "24 visits per year · bi-monthly",
    description: "White-glove service for larger homes.",
    features: [
      "Everything in Complete",
      "Dedicated lead technician",
      "Smart-home setup & support",
      "Minor repairs included",
    ],
  },
];

function PlansPreview() {
  return (
    <section id="plans-preview" className="scroll-mt-24 border-t border-border bg-surface py-24">
      <div className="mx-auto max-w-7xl px-6">
        <header className="mb-16 max-w-2xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Plans</p>
          <h2 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
            Simple, monthly peace of mind.
          </h2>
          <p className="mt-4 text-muted-foreground">
            All prices in CAD. Annual billing saves ~17% (two months free).
          </p>
        </header>

        <div className="grid items-stretch gap-6 md:grid-cols-3">
          {plans.map((plan) => (
            <PlanCard key={plan.id} plan={plan} />
          ))}
        </div>

        <div className="mt-10 text-center">
          <Button asChild variant="ghost">
            <Link to="/plans">
              Compare plans in detail <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
      </div>
    </section>
  );
}

function PlanCard({ plan }: { plan: PlanTier }) {
  const featured = plan.featured;
  return (
    <article
      className={
        "relative flex flex-col rounded-3xl p-8 transition-all " +
        (featured
          ? "bg-primary text-primary-foreground shadow-lift ring-8 ring-surface"
          : "border border-border bg-card text-card-foreground hover:-translate-y-0.5 hover:shadow-soft")
      }
    >
      {featured && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-accent px-3 py-1 text-[10px] font-bold uppercase tracking-[0.18em] text-accent-foreground shadow-sm">
          Most chosen
        </div>
      )}
      <h3 className="font-display text-xl font-bold">{plan.name}</h3>
      <div className="mt-3 flex items-baseline gap-1">
        <span className="font-display text-5xl font-extrabold tracking-tight tabular-nums">
          ${plan.price}
        </span>
        <span className={featured ? "text-primary-foreground/70" : "text-muted-foreground"}>
          /mo
        </span>
      </div>
      <p
        className={
          "mt-1 text-sm " +
          (featured ? "text-primary-foreground/70" : "text-muted-foreground")
        }
      >
        {plan.cadence}
      </p>
      <p
        className={
          "mt-4 text-sm " +
          (featured ? "text-primary-foreground/80" : "text-muted-foreground")
        }
      >
        {plan.description}
      </p>

      <ul className="mt-6 flex-1 space-y-3 border-t border-border/40 pt-6">
        {plan.features.map((f) => (
          <li key={f} className="flex items-start gap-2 text-sm">
            <CheckCircle2
              className={
                "mt-0.5 size-4 shrink-0 " + (featured ? "text-accent" : "text-accent")
              }
              aria-hidden="true"
            />
            <span>{f}</span>
          </li>
        ))}
      </ul>

      <div className="mt-8">
        <Button
          asChild
          variant={featured ? "accent" : "outline"}
          size="lg"
          className="w-full"
        >
          <Link to="/plans" search={{ tier: plan.id }}>
            Choose {plan.name}
          </Link>
        </Button>
      </div>
    </article>
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
    quote:
      "We stopped pretending we'd get to it. Now we just don't think about it — and the house is actually in better shape.",
    attribution: "Sarah · Oakville",
  },
  {
    eyebrow: "For empty-nesters",
    headline: "Less climbing ladders. Same standards.",
    body: "You love the house. You're done with the physical work. Same technician each visit, calm communication, and proper attention to the systems that quietly age.",
    quote:
      "It feels like having a really competent nephew in the trades — except he actually shows up.",
    attribution: "James · Mississauga",
  },
  {
    eyebrow: "For first-time owners",
    headline: "Your home, finally explained.",
    body: "Nobody hands you a manual when you buy a house. We walk you through what your home needs — and then we do it — so you learn as we go.",
    quote:
      "I had no idea water heaters needed flushing. Now I do, and I haven't had to lift a wrench.",
    attribution: "Priya · Milton",
  },
];

function Personas() {
  return (
    <section id="who" className="scroll-mt-24 py-24">
      <div className="mx-auto max-w-7xl px-6">
        <header className="mb-16 max-w-2xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">
            For every kind of homeowner
          </p>
          <h2 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
            We fit the home, and the people in it.
          </h2>
        </header>

        <div className="grid gap-6 lg:grid-cols-3">
          {personas.map((p) => (
            <article
              key={p.attribution}
              className="flex flex-col rounded-3xl border border-border bg-card p-8"
            >
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-accent">
                {p.eyebrow}
              </p>
              <h3 className="mt-3 font-display text-2xl font-bold tracking-tight">
                {p.headline}
              </h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.body}</p>

              <figure className="mt-6 border-t border-border pt-6">
                <blockquote className="text-sm italic text-foreground">
                  &ldquo;{p.quote}&rdquo;
                </blockquote>
                <figcaption className="mt-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {p.attribution}
                </figcaption>
              </figure>
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
  return (
    <section className="px-6 pb-24">
      <div className="mx-auto max-w-7xl">
        <div className="relative overflow-hidden rounded-[40px] bg-primary px-6 py-16 text-primary-foreground md:px-16 md:py-24">
          <div
            className="absolute -right-24 -top-24 size-72 rounded-full bg-accent/20 blur-3xl"
            aria-hidden="true"
          />
          <div className="relative grid items-center gap-10 md:grid-cols-5">
            <div className="md:col-span-3">
              <h2 className="font-display text-4xl font-extrabold leading-tight tracking-tight md:text-5xl">
                Let's walk your home together.
              </h2>
              <p className="mt-4 max-w-xl text-primary-foreground/80">
                Ninety minutes, no obligation. You'll get a written maintenance plan within
                a week — keep it or don't.
              </p>
            </div>
            <div className="flex md:col-span-2 md:justify-end">
              <Button asChild size="xl" variant="accent">
                <Link to="/book">
                  Book free walk-through <ArrowRight className="size-4" />
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
