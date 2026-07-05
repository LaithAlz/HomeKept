import { createFileRoute, Link } from "@tanstack/react-router";
import {
  CalendarPlus,
  RefreshCcw,
  BellRing,
  Wrench,
  ArrowRight,
  MapPin,
  Loader2,
  AlertTriangle,
  FileText,
  Plus,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAccount, type AppAccount } from "@/lib/account";
import { useHealthScore, type HealthScoreFlaggedItem } from "@/lib/health";
import {
  greetingFor,
  formatFullDate,
  formatTime,
  getCalendarParts,
  formatVisitWindow,
} from "@/lib/format";
import { useNextVisit, useRecentCompletedVisits, type AppVisitListItem } from "@/lib/visits";
import { useTodos, type TodoResponse, type TodoItemStatus } from "@/lib/todos";
import { useSessionExpiredRedirect } from "@/lib/auth";
import {
  ScoreRing,
  HealthDeltaChip,
  OpenItemsList,
  attentionFlag,
  verdictFor,
  summaryFor,
} from "@/components/app/health-score";

export const Route = createFileRoute("/app/")({
  head: () => ({
    meta: [{ title: "Your home — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: DashboardHome,
});

function formatAddress(account: AppAccount): string | null {
  const parts: string[] = [];
  if (account.streetAddress) {
    parts.push(
      account.unit ? `${account.streetAddress}, Unit ${account.unit}` : account.streetAddress,
    );
  }
  if (account.city) parts.push(account.city);
  if (account.postalCode) parts.push(account.postalCode);
  return parts.length > 0 ? parts.join(" · ") : null;
}

function DashboardHome() {
  const now = new Date();
  const period = greetingFor(now);
  const greetingWord =
    period === "morning"
      ? "Good morning"
      : period === "afternoon"
        ? "Good afternoon"
        : "Good evening";

  const accountQuery = useAccount();
  useSessionExpiredRedirect(accountQuery.error);
  const account = accountQuery.data;
  const address = account ? formatAddress(account) : null;
  // Once loading settles, fall back to a neutral greeting rather than an
  // abrupt name-less sentence if the account couldn't be loaded.
  const accountLoadFailed = !accountQuery.isLoading && !account;

  const healthQuery = useHealthScore();
  useSessionExpiredRedirect(healthQuery.error);
  const flagged = healthQuery.data?.flagged ?? [];
  const topFlag = attentionFlag(flagged);

  return (
    <div className="px-6 py-8 md:px-10 md:py-12">
      {/* Greeting */}
      <section aria-labelledby="greeting" className="max-w-3xl">
        <h1
          id="greeting"
          className="font-display text-3xl font-extrabold tracking-tight md:text-4xl"
        >
          {greetingWord}
          {account?.firstName ? `, ${account.firstName}` : accountLoadFailed ? ", there" : ""}.
        </h1>
        {address && (
          <p className="mt-2 inline-flex items-center gap-1.5 text-sm text-muted-foreground">
            <MapPin className="size-3.5" aria-hidden="true" />
            {address}
          </p>
        )}
      </section>

      {topFlag && <AttentionBand flag={topFlag} />}

      {/* Hero row: home health + next visit */}
      <div className="mt-8 grid gap-6 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <HealthHeroCard />
        </div>
        <div className="lg:col-span-3">
          <NextVisitCard />
        </div>
      </div>

      <OpenItemsSection />

      <div className="mt-10 grid gap-6 lg:grid-cols-2">
        <YourListPreview />
        <LatestReportCard />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Attention band
// ---------------------------------------------------------------------------

function AttentionBand({ flag }: { flag: HealthScoreFlaggedItem }) {
  return (
    <div
      role="status"
      className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-warning/40 bg-warning/10 px-5 py-4"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden="true" />
        <p className="text-sm text-foreground">{flag.body}</p>
      </div>
      <Link
        to="/app/health"
        className="inline-flex shrink-0 items-center gap-1 rounded text-sm font-semibold text-foreground hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        See details <ArrowRight className="size-4" aria-hidden="true" />
      </Link>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Home health (hero)
// ---------------------------------------------------------------------------

function HealthHeroCard() {
  const query = useHealthScore();
  useSessionExpiredRedirect(query.error);

  return (
    <article
      aria-labelledby="health-score-heading"
      className="flex h-full flex-col overflow-hidden rounded-3xl bg-primary p-6 text-primary-foreground shadow-sm md:p-8"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground/70">
          Home health
        </span>
        <Link
          to="/app/health"
          className="inline-flex items-center gap-1 rounded text-xs font-semibold text-primary-foreground/80 hover:text-primary-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          Details <ArrowRight className="size-3.5" aria-hidden="true" />
        </Link>
      </div>

      <h2 id="health-score-heading" className="sr-only">
        Home health score
      </h2>

      <div className="flex flex-1 flex-col items-center justify-center">
        {query.isLoading ? (
          <div
            className="flex items-center gap-3 py-10 text-sm text-primary-foreground/70"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your home health score.
          </div>
        ) : query.isError ? (
          <p className="py-10 text-center text-sm text-primary-foreground/70">
            We couldn't load your home health score. Try refreshing the page.
          </p>
        ) : query.data ? (
          <HealthHeroContent
            score={query.data.score}
            delta={query.data.delta}
            computedAt={query.data.computedAt}
          />
        ) : null}
      </div>
    </article>
  );
}

function HealthHeroContent({
  score,
  delta,
  computedAt,
}: {
  score: number;
  delta: number;
  computedAt: string;
}) {
  const verdict = verdictFor(score);
  const summary = summaryFor(score);

  return (
    <div className="mt-4 flex flex-col items-center text-center">
      <ScoreRing score={score} tone="dark" />
      <p className="mt-4 font-display text-xl font-bold">{verdict}</p>
      <p className="mt-2 max-w-[28ch] text-sm text-primary-foreground/80">{summary}</p>
      {delta !== 0 && (
        <div className="mt-4">
          <HealthDeltaChip delta={delta} />
        </div>
      )}
      <p className="mt-4 text-xs text-primary-foreground/60">
        Last checked {formatFullDate(computedAt)} at {formatTime(computedAt)}
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Next visit
// ---------------------------------------------------------------------------

function NextVisitCard() {
  const query = useNextVisit();
  useSessionExpiredRedirect(query.error);
  const visit = query.data?.[0] ?? null;

  return (
    <article
      aria-label="Next visit"
      className="flex h-full flex-col overflow-hidden rounded-3xl border border-border bg-card shadow-sm"
    >
      <div className="flex items-center gap-2 border-b border-border bg-primary/5 px-6 py-3">
        <BellRing className="size-4 text-primary" aria-hidden="true" />
        <span className="text-xs font-bold uppercase tracking-[0.18em] text-primary">
          Next visit
        </span>
      </div>

      {query.isLoading ? (
        <div
          className="flex items-center gap-3 p-6 text-sm text-muted-foreground"
          role="status"
          aria-live="polite"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading your next visit.
        </div>
      ) : query.isError ? (
        <p className="p-6 text-sm text-muted-foreground">
          We couldn't load your next visit. Try refreshing the page.
        </p>
      ) : !visit ? (
        <p className="p-6 text-sm text-muted-foreground">
          No visits scheduled yet. We'll be in touch shortly to confirm your first visit.
        </p>
      ) : (
        <NextVisitContent visit={visit} />
      )}
    </article>
  );
}

function NextVisitContent({ visit }: { visit: AppVisitListItem }) {
  const { month, day, weekday } = getCalendarParts(visit.scheduledFor);
  const window = formatVisitWindow(visit.scheduledFor, visit.durationMinutes);

  return (
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
        <h2 className="font-display text-xl font-bold tracking-tight">
          {visit.technicianFirstName ? `${window} with ${visit.technicianFirstName}` : window}
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {visit.services.length} services scheduled. You'll get a photo report after.
        </p>

        <ul className="mt-4 grid gap-2 sm:grid-cols-2">
          {visit.services.map((s) => (
            <li
              key={s.id}
              className="flex items-start gap-2 rounded-xl bg-surface px-3 py-2 text-sm"
            >
              <Wrench className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
              <span className="text-foreground/90">{s.serviceName}</span>
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
  );
}

// ---------------------------------------------------------------------------
// Open items (real flagged items — no fabricated per-system grid)
// ---------------------------------------------------------------------------

function OpenItemsSection() {
  const query = useHealthScore();
  useSessionExpiredRedirect(query.error);
  const flagged = query.data?.flagged ?? [];

  return (
    <section aria-labelledby="open-items-heading" className="mt-10">
      <h2 id="open-items-heading" className="font-display text-2xl font-bold tracking-tight">
        Open items
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Things your technician has flagged for your home.
      </p>

      <div className="mt-6">
        <OpenItemsList flagged={flagged} isLoading={query.isLoading} isError={query.isError} />
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Your list preview
// ---------------------------------------------------------------------------

const TODO_STATUS_LABEL: Record<TodoItemStatus, string> = {
  OPEN: "Open",
  SCHEDULED: "Scheduled",
  DONE: "Done",
  DECLINED: "Couldn't be done",
};

function TodoStatusChip({ status }: { status: TodoItemStatus }) {
  return (
    <span className="shrink-0 rounded-full bg-card px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
      {TODO_STATUS_LABEL[status]}
    </span>
  );
}

function YourListPreview() {
  const query = useTodos();
  useSessionExpiredRedirect(query.error);
  const items: TodoResponse[] = (query.data ?? []).slice(0, 3);

  return (
    <article
      aria-labelledby="list-preview-heading"
      className="rounded-3xl border border-border bg-card p-6 shadow-sm"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="list-preview-heading" className="font-display text-lg font-bold tracking-tight">
          Your list
        </h2>
        <Link
          to="/app/list"
          className="inline-flex items-center gap-1 rounded text-xs font-semibold text-foreground/80 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          See all <ArrowRight className="size-3.5" aria-hidden="true" />
        </Link>
      </div>

      {query.isLoading ? (
        <div
          className="mt-4 flex items-center gap-3 text-sm text-muted-foreground"
          role="status"
          aria-live="polite"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading your list.
        </div>
      ) : query.isError ? (
        <p className="mt-4 text-sm text-muted-foreground">
          We couldn't load your list. Try refreshing the page.
        </p>
      ) : items.length === 0 ? (
        <div className="mt-4">
          <p className="text-sm text-muted-foreground">
            Nothing on your list yet. Add a small task and we'll fold it into your next visit.
          </p>
          <Link to="/app/list" className="mt-3 inline-flex">
            <Button size="sm" variant="outline">
              <Plus className="size-4" aria-hidden="true" />
              Add something
            </Button>
          </Link>
        </div>
      ) : (
        <ul className="mt-4 space-y-2">
          {items.map((item) => (
            <li
              key={item.id}
              className="flex items-start justify-between gap-3 rounded-xl bg-surface px-3 py-2.5 text-sm"
            >
              <span className="min-w-0 flex-1 truncate text-foreground/90">{item.body}</span>
              <TodoStatusChip status={item.status} />
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

// ---------------------------------------------------------------------------
// Latest report
// ---------------------------------------------------------------------------

function LatestReportCard() {
  const query = useRecentCompletedVisits(1);
  useSessionExpiredRedirect(query.error);
  const visit = query.data?.[0] ?? null;

  return (
    <article
      aria-labelledby="latest-report-heading"
      className="rounded-3xl border border-border bg-card p-6 shadow-sm"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="latest-report-heading" className="font-display text-lg font-bold tracking-tight">
          Latest report
        </h2>
        <Link
          to="/app/reports"
          className="inline-flex items-center gap-1 rounded text-xs font-semibold text-foreground/80 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          All reports <ArrowRight className="size-3.5" aria-hidden="true" />
        </Link>
      </div>

      {query.isLoading ? (
        <div
          className="mt-4 flex items-center gap-3 text-sm text-muted-foreground"
          role="status"
          aria-live="polite"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading your latest report.
        </div>
      ) : query.isError ? (
        <p className="mt-4 text-sm text-muted-foreground">
          We couldn't load your latest report. Try refreshing the page.
        </p>
      ) : !visit ? (
        <p className="mt-4 text-sm text-muted-foreground">
          No completed visits yet. Your visit history will show up here after your first visit.
        </p>
      ) : (
        <Link
          to="/app/visits/$id"
          params={{ id: String(visit.id) }}
          className="mt-4 flex items-start gap-4 rounded-2xl bg-surface p-4 transition-colors hover:bg-surface/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <span
            aria-hidden="true"
            className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-accent/15 text-accent"
          >
            <FileText className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-foreground">
              {formatFullDate(visit.scheduledFor)}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              {visit.services.length > 0
                ? visit.services.map((s) => s.serviceName).join(", ")
                : visit.name}
            </p>
          </div>
          <ArrowRight
            className="mt-1 size-4 shrink-0 text-muted-foreground/60"
            aria-hidden="true"
          />
        </Link>
      )}
    </article>
  );
}
