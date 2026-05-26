import { createFileRoute, Link } from "@tanstack/react-router";
import {
  CalendarPlus,
  RefreshCcw,
  FileText,
  CreditCard,
  Camera,
  BellRing,
  Wrench,
  ArrowRight,
  ArrowUpRight,
  ArrowDownRight,
  MapPin,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  subscriber,
  greetingFor,
  formatRelativeTime,
  type ActivityItem,
} from "@/lib/mock-account";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/")({
  head: () => ({
    meta: [
      { title: "Your home — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: DashboardHome,
});

function DashboardHome() {
  const now = new Date();
  const period = greetingFor(now);
  const greetingWord =
    period === "morning"
      ? "Good morning"
      : period === "afternoon"
        ? "Good afternoon"
        : "Good evening";

  const nextVisitDate = new Date(subscriber.nextVisit.date);
  const daysUntil = Math.max(
    0,
    Math.ceil((nextVisitDate.getTime() - now.getTime()) / 86_400_000),
  );

  const summary =
    daysUntil === 0
      ? `Marcus is on his way today — ${subscriber.nextVisit.window}.`
      : daysUntil === 1
        ? `Your next visit is tomorrow, ${subscriber.nextVisit.window}.`
        : `Your next visit is in ${daysUntil} days. Home health is looking good — ${subscriber.health.score}/100.`;

  return (
    <div className="px-6 py-8 md:px-10 md:py-12">
      {/* Greeting */}
      <section aria-labelledby="greeting" className="max-w-3xl">
        <h1
          id="greeting"
          className="font-display text-3xl font-extrabold tracking-tight md:text-4xl"
        >
          {greetingWord}, {subscriber.firstName}.
        </h1>
        <p className="mt-2 text-muted-foreground md:text-lg">{summary}</p>
        <p className="mt-1 inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <MapPin className="size-3.5" aria-hidden="true" />
          {subscriber.address.street} · {subscriber.address.neighbourhood},{" "}
          {subscriber.address.city}
        </p>
      </section>

      <div className="mt-8 grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <NextVisitCard />
        </div>
        <div>
          <HealthCard />
        </div>
      </div>

      <section aria-labelledby="activity" className="mt-10">
        <div className="flex items-end justify-between">
          <div>
            <h2
              id="activity"
              className="font-display text-2xl font-bold tracking-tight"
            >
              Recent activity
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              The last few things that happened on your home.
            </p>
          </div>
        </div>
        <ActivityFeed items={subscriber.activity.slice(0, 7)} />
      </section>
    </div>
  );
}

function NextVisitCard() {
  const v = subscriber.nextVisit;
  const d = new Date(v.date);
  const month = d.toLocaleDateString("en-CA", { month: "short" }).toUpperCase();
  const day = d.getDate();
  const weekday = d.toLocaleDateString("en-CA", { weekday: "long" });

  return (
    <article
      aria-labelledby="next-visit"
      className="overflow-hidden rounded-3xl border border-border bg-card shadow-sm"
    >
      <div className="flex items-center gap-2 border-b border-border bg-primary/5 px-6 py-3">
        <BellRing className="size-4 text-primary" aria-hidden="true" />
        <span className="text-xs font-bold uppercase tracking-[0.18em] text-primary">
          Next visit
        </span>
      </div>

      <div className="grid gap-6 p-6 md:grid-cols-[auto_1fr] md:gap-8">
        {/* Calendar block */}
        <div
          className="flex w-full max-w-[140px] flex-col overflow-hidden rounded-2xl border border-border text-center"
          aria-label={`${weekday}, ${month} ${day}`}
        >
          <div className="bg-accent py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-accent-foreground">
            {month}
          </div>
          <div className="bg-card py-3">
            <div className="font-display text-5xl font-extrabold leading-none tracking-tight text-foreground">
              {day}
            </div>
            <div className="mt-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
              {weekday}
            </div>
          </div>
        </div>

        <div className="min-w-0">
          <h2
            id="next-visit"
            className="font-display text-xl font-bold tracking-tight"
          >
            {v.window} with {v.technicianFirstName}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {v.services.length} services scheduled. You'll get a photo report after.
          </p>

          <ul className="mt-4 grid gap-2 sm:grid-cols-2">
            {v.services.map((s) => (
              <li
                key={s.id}
                className="flex items-start gap-2 rounded-xl bg-surface px-3 py-2 text-sm"
              >
                <Wrench
                  className="mt-0.5 size-4 shrink-0 text-accent"
                  aria-hidden="true"
                />
                <span className="text-foreground/90">{s.label}</span>
              </li>
            ))}
          </ul>

          <div className="mt-6 flex flex-wrap gap-3">
            <Button variant="outline" size="sm">
              <RefreshCcw className="size-4" />
              Reschedule
            </Button>
            <Button size="sm">
              <CalendarPlus className="size-4" />
              Add a request
            </Button>
          </div>
        </div>
      </div>
    </article>
  );
}

function HealthCard() {
  const { score, delta, note } = subscriber.health;
  const trendUp = delta >= 0;

  return (
    <article
      aria-labelledby="health-score"
      className="flex h-full flex-col rounded-3xl border border-border bg-card p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold uppercase tracking-[0.18em] text-accent">
          Home health
        </span>
        <Link
          to="/app/health"
          className="inline-flex items-center gap-1 text-xs font-semibold text-foreground/80 hover:text-accent"
        >
          Details <ArrowRight className="size-3.5" />
        </Link>
      </div>

      <h2 id="health-score" className="sr-only">
        Home health score
      </h2>

      <div className="mt-4 flex items-center justify-center">
        <ScoreRing score={score} />
      </div>

      <div className="mt-4 flex items-center justify-center gap-2 text-sm">
        <span
          className={cn(
            "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold",
            trendUp
              ? "bg-accent/15 text-accent"
              : "bg-destructive/15 text-destructive",
          )}
        >
          {trendUp ? (
            <ArrowUpRight className="size-3.5" />
          ) : (
            <ArrowDownRight className="size-3.5" />
          )}
          {trendUp ? "+" : ""}
          {delta} vs last quarter
        </span>
      </div>

      <p className="mt-4 text-center text-sm text-muted-foreground">{note}</p>
    </article>
  );
}

function ScoreRing({ score }: { score: number }) {
  const size = 168;
  const stroke = 14;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, score));
  const offset = circumference * (1 - clamped / 100);

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role="img"
        aria-label={`Home health score ${clamped} out of 100`}
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="hsl(var(--border) / 1)"
          className="text-border"
          strokeWidth={stroke}
          opacity={0.4}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          className="text-accent"
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-5xl font-extrabold leading-none tracking-tight text-foreground">
          {clamped}
        </span>
        <span className="mt-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
          out of 100
        </span>
      </div>
    </div>
  );
}

const activityIcon: Record<ActivityItem["type"], typeof FileText> = {
  "visit-report": FileText,
  "visit-completed": Wrench,
  reminder: BellRing,
  photos: Camera,
  billing: CreditCard,
};

function ActivityFeed({ items }: { items: ActivityItem[] }) {
  return (
    <ul className="mt-6 divide-y divide-border overflow-hidden rounded-3xl border border-border bg-card shadow-sm">
      {items.map((item) => {
        const Icon = activityIcon[item.type];
        return (
          <li key={item.id}>
            <Link
              {...item.href}
              className="group flex items-start gap-4 p-5 transition-colors hover:bg-surface/60"
            >
              <span
                aria-hidden="true"
                className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-accent/15 text-accent"
              >
                <Icon className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                  <h3 className="text-sm font-semibold text-foreground">
                    {item.title}
                  </h3>
                  <time
                    dateTime={item.timestamp}
                    className="text-xs text-muted-foreground"
                  >
                    {formatRelativeTime(item.timestamp)} · {item.actor}
                  </time>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">{item.detail}</p>
              </div>
              <ArrowRight
                className="mt-1 size-4 shrink-0 text-muted-foreground/60 transition-transform group-hover:translate-x-0.5 group-hover:text-accent"
                aria-hidden="true"
              />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
