import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import {
  ArrowUpRight,
  ArrowDownRight,
  Download,
  Plus,
  Search,
  ChevronUp,
  ChevronDown,
  ChevronsUpDown,
  AlertTriangle,
  CreditCard,
  UserMinus,
  CalendarX,
  CheckCircle2,
  Circle,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import {
  attention,
  formatCAD,
  formatDateShort,
  formatDateTime,
  formatTodayLong,
  metrics,
  pendingWalkthroughs,
  subscribers,
  type Plan,

  type Subscriber,
  type SubscriberStatus,
} from "@/lib/mock-admin";

export const Route = createFileRoute("/admin/")({
  head: () => ({
    meta: [
      { title: "Dashboard — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: AdminDashboard,
});

type SortKey = "name" | "plan" | "status" | "nextVisit" | "mrr";
type SortDir = "asc" | "desc";

function AdminDashboard() {
  const [newBookingOpen, setNewBookingOpen] = useState(false);

  // Filter + sort state
  const [query, setQuery] = useState("");
  const [planFilter, setPlanFilter] = useState<"all" | Plan>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | SubscriberStatus>(
    "all",
  );
  const [sortKey, setSortKey] = useState<SortKey>("name");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = subscribers.filter((s) => {
      if (planFilter !== "all" && s.plan !== planFilter) return false;
      if (statusFilter !== "all" && s.status !== statusFilter) return false;
      if (!q) return true;
      return (
        s.name.toLowerCase().includes(q) ||
        s.street.toLowerCase().includes(q) ||
        s.neighbourhood.toLowerCase().includes(q) ||
        s.city.toLowerCase().includes(q)
      );
    });

    return list.slice().sort((a, b) => {
      const dir = sortDir === "asc" ? 1 : -1;
      switch (sortKey) {
        case "name":
          return a.name.localeCompare(b.name) * dir;
        case "plan":
          return a.plan.localeCompare(b.plan) * dir;
        case "status":
          return a.status.localeCompare(b.status) * dir;
        case "mrr":
          return (a.mrr - b.mrr) * dir;
        case "nextVisit": {
          const av = a.nextVisit?.date ?? "";
          const bv = b.nextVisit?.date ?? "";
          return av.localeCompare(bv) * dir;
        }
      }
    });
  }, [query, planFilter, statusFilter, sortKey, sortDir]);

  return (
    <>
      {/* Top bar */}
      <div className="sticky top-0 z-20 border-b border-border bg-card/95 backdrop-blur">
        <div className="flex flex-col gap-3 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs text-muted-foreground">
              {formatTodayLong()} ·{" "}
              <span className="font-semibold text-foreground">
                All systems normal
              </span>{" "}
              · 3 visits today
            </p>
            <h1 className="mt-0.5 font-display text-2xl font-extrabold tracking-tight">
              Dashboard
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm">
              <Download className="size-4" />
              Export
            </Button>
            <Button size="sm" onClick={() => setNewBookingOpen(true)}>
              <Plus className="size-4" />
              New booking
            </Button>
          </div>
        </div>
      </div>

      <div className="px-6 py-6 space-y-6">
        {/* Metric strip */}
        <section
          aria-label="Key metrics"
          className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <MetricCard
            label="MRR"
            value={formatCAD(metrics.mrr)}
            sub={
              <Delta value={metrics.mrrDeltaPct} suffix="% vs last month" up />
            }
          />
          <MetricCard
            label="Active subscribers"
            value={metrics.activeCount.toString()}
            sub={
              <span className="text-accent">
                +{metrics.activeNetNew} net new this month
              </span>
            }
          />
          <MetricCard
            label="Walk-throughs booked"
            value={metrics.walkthroughsBooked.toString()}
            sub={
              <Delta
                value={metrics.walkthroughsWeekDelta}
                suffix=" vs last week"
                up
              />
            }
          />
          <MetricCard
            label="At-risk subscribers"
            value={metrics.atRiskCount.toString()}
            sub={
              <span className="inline-flex items-center gap-1 text-destructive">
                <AlertTriangle className="size-3.5" /> Flagged for outreach
              </span>
            }
            tone="warn"
          />
        </section>

        {/* Subscribers table */}
        <section
          aria-labelledby="subs-h"
          className="rounded-2xl border border-border bg-card shadow-sm"
        >
          <div className="flex flex-col gap-3 border-b border-border p-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2
                id="subs-h"
                className="font-display text-lg font-bold tracking-tight"
              >
                Recent subscribers
              </h2>
              <p className="text-xs text-muted-foreground">
                {filtered.length} of {subscribers.length} shown
              </p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search name or address"
                  className="h-9 w-full pl-9 sm:w-64"
                  aria-label="Search subscribers"
                />
              </div>
              <Select
                value={statusFilter}
                onValueChange={(v) =>
                  setStatusFilter(v as "all" | SubscriberStatus)
                }
              >
                <SelectTrigger className="h-9 w-full sm:w-40" aria-label="Status filter">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All statuses</SelectItem>
                  <SelectItem value="active">Active</SelectItem>
                  <SelectItem value="first-visit">First visit</SelectItem>
                  <SelectItem value="payment-issue">Payment issue</SelectItem>
                  <SelectItem value="at-risk">At risk</SelectItem>
                  <SelectItem value="paused">Paused</SelectItem>
                </SelectContent>
              </Select>
              <Select
                value={planFilter}
                onValueChange={(v) => setPlanFilter(v as "all" | Plan)}
              >
                <SelectTrigger className="h-9 w-full sm:w-36" aria-label="Plan filter">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All plans</SelectItem>
                  <SelectItem value="Essential">Essential</SelectItem>
                  <SelectItem value="Complete">Complete</SelectItem>
                  <SelectItem value="Premier">Premier</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[840px] border-collapse text-left text-sm">
              <thead className="bg-surface/60 text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <Th
                    label="Subscriber"
                    sortKey="name"
                    activeSortKey={sortKey}
                    sortDir={sortDir}
                    onSort={toggleSort}
                  />
                  <Th
                    label="Plan"
                    sortKey="plan"
                    activeSortKey={sortKey}
                    sortDir={sortDir}
                    onSort={toggleSort}
                  />
                  <Th
                    label="Status"
                    sortKey="status"
                    activeSortKey={sortKey}
                    sortDir={sortDir}
                    onSort={toggleSort}
                  />
                  <Th
                    label="Next visit"
                    sortKey="nextVisit"
                    activeSortKey={sortKey}
                    sortDir={sortDir}
                    onSort={toggleSort}
                  />
                  <Th
                    label="MRR"
                    sortKey="mrr"
                    activeSortKey={sortKey}
                    sortDir={sortDir}
                    onSort={toggleSort}
                    align="right"
                  />
                </tr>
              </thead>
              <tbody>
                {filtered.map((s) => (
                  <SubscriberRow key={s.id} subscriber={s} />
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td
                      colSpan={5}
                      className="p-10 text-center text-sm text-muted-foreground"
                    >
                      No subscribers match these filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* Two-column section */}
        <section className="grid gap-6 xl:grid-cols-2">
          <PendingWalkthroughsPanel />
          <NeedsAttentionPanel />
        </section>
      </div>

      <NewBookingSheet
        open={newBookingOpen}
        onOpenChange={setNewBookingOpen}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// Metric cards
// ---------------------------------------------------------------------------

function MetricCard({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: string;
  sub: React.ReactNode;
  tone?: "warn";
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border bg-card p-4 shadow-sm",
        tone === "warn" ? "border-destructive/30" : "border-border",
      )}
    >
      <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 font-display text-3xl font-extrabold tracking-tight">
        {value}
      </p>
      <p className="mt-2 text-xs text-muted-foreground">{sub}</p>
    </div>
  );
}

function Delta({
  value,
  suffix,
  up,
}: {
  value: number;
  suffix: string;
  up: boolean;
}) {
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 font-semibold",
        up ? "text-accent" : "text-destructive",
      )}
    >
      <Icon className="size-3.5" />
      {value > 0 ? "+" : ""}
      {value}
      {suffix}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

function Th({
  label,
  sortKey,
  activeSortKey,
  sortDir,
  onSort,
  align = "left",
}: {
  label: string;
  sortKey: SortKey;
  activeSortKey: SortKey;
  sortDir: SortDir;
  onSort: (k: SortKey) => void;
  align?: "left" | "right";
}) {
  const isActive = sortKey === activeSortKey;
  const Icon = !isActive ? ChevronsUpDown : sortDir === "asc" ? ChevronUp : ChevronDown;
  return (
    <th scope="col" className={cn("p-3", align === "right" && "text-right")}>
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        aria-sort={
          isActive ? (sortDir === "asc" ? "ascending" : "descending") : "none"
        }
        className={cn(
          "inline-flex items-center gap-1 font-bold uppercase tracking-wider hover:text-foreground",
          isActive ? "text-foreground" : "text-muted-foreground",
        )}
      >
        {label}
        <Icon className="size-3" />
      </button>
    </th>
  );
}

function SubscriberRow({ subscriber: s }: { subscriber: Subscriber }) {
  return (
    <tr className="border-t border-border hover:bg-surface/40">
      <td className="p-3">
        <div className="font-semibold text-foreground">{s.name}</div>
        <div className="text-xs text-muted-foreground">
          {s.street} · {s.neighbourhood}, {s.city}
        </div>
      </td>
      <td className="p-3">
        <PlanPill plan={s.plan} />
      </td>
      <td className="p-3">
        <StatusPill status={s.status} />
      </td>
      <td className="p-3 text-sm">
        {s.nextVisit ? (
          <>
            <div className="font-medium text-foreground">
              {formatDateShort(s.nextVisit.date)}
            </div>
            <div className="text-xs text-muted-foreground">
              {s.nextVisit.technician}
            </div>
          </>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </td>
      <td className="p-3 text-right font-semibold tabular-nums">
        {s.mrr === 0 ? (
          <span className="text-muted-foreground">—</span>
        ) : (
          formatCAD(s.mrr)
        )}
      </td>
    </tr>
  );
}

function PlanPill({ plan }: { plan: Plan }) {
  const cls =
    plan === "Premier"
      ? "bg-primary text-primary-foreground"
      : plan === "Complete"
        ? "bg-accent/15 text-accent"
        : "border border-border bg-surface text-foreground";
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider",
        cls,
      )}
    >
      {plan}
    </span>
  );
}

function StatusPill({ status }: { status: SubscriberStatus }) {
  const map: Record<
    SubscriberStatus,
    { label: string; cls: string; dot: string }
  > = {
    active: {
      label: "Active",
      cls: "bg-accent/15 text-accent",
      dot: "bg-accent",
    },
    "first-visit": {
      label: "First visit",
      cls: "bg-primary/10 text-primary",
      dot: "bg-primary",
    },
    "payment-issue": {
      label: "Payment issue",
      cls: "bg-destructive/15 text-destructive",
      dot: "bg-destructive",
    },
    "at-risk": {
      label: "At risk",
      cls: "bg-destructive/10 text-destructive",
      dot: "bg-destructive",
    },
    paused: {
      label: "Paused",
      cls: "bg-surface text-muted-foreground border border-border",
      dot: "bg-muted-foreground",
    },
  };
  const s = map[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-semibold",
        s.cls,
      )}
    >
      <span className={cn("size-1.5 rounded-full", s.dot)} aria-hidden="true" />
      {s.label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Pending walk-throughs
// ---------------------------------------------------------------------------

function PendingWalkthroughsPanel() {
  return (
    <article
      aria-labelledby="walk-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2
            id="walk-h"
            className="font-display text-lg font-bold tracking-tight"
          >
            Pending walk-throughs
          </h2>
          <p className="text-xs text-muted-foreground">
            {pendingWalkthroughs.length} upcoming
          </p>
        </div>
        <Button variant="ghost" size="sm">
          See all
        </Button>
      </header>
      <ul className="divide-y divide-border">
        {pendingWalkthroughs.map((w) => (
          <li key={w.id} className="flex items-start gap-3 p-4">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-xs font-bold text-foreground/80">
              {formatDateShort(w.date)
                .split(" ")
                .map((p) => p)
                .join(" ")}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline justify-between gap-3">
                <p className="truncate font-semibold text-foreground">
                  {w.homeowner}
                </p>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {formatDateTime(w.date)}
                </span>
              </div>
              <p className="text-xs text-muted-foreground">
                {w.city} ·{" "}
                <span className="rounded-md bg-surface px-1.5 py-0.5 font-semibold text-foreground/80">
                  {w.source}
                </span>
              </p>
            </div>
            <span
              className={cn(
                "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold",
                w.confirmed
                  ? "bg-accent/15 text-accent"
                  : "bg-surface text-muted-foreground border border-border",
              )}
            >
              {w.confirmed ? (
                <CheckCircle2 className="size-3" />
              ) : (
                <Circle className="size-3" />
              )}
              {w.confirmed ? "Confirmed" : "Unconfirmed"}
            </span>
          </li>
        ))}
      </ul>
    </article>
  );
}

// ---------------------------------------------------------------------------
// Needs attention
// ---------------------------------------------------------------------------

const attentionIcon = {
  "payment-failure": CreditCard,
  "churn-risk": UserMinus,
  "unassigned-visit": CalendarX,
} as const;

function NeedsAttentionPanel() {
  return (
    <article
      aria-labelledby="att-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="size-4 text-destructive" aria-hidden="true" />
          <h2
            id="att-h"
            className="font-display text-lg font-bold tracking-tight"
          >
            Needs attention
          </h2>
        </div>
        <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-[11px] font-bold text-destructive">
          {attention.length} open
        </span>
      </header>
      <ul className="divide-y divide-border">
        {attention.map((a) => {
          const Icon = attentionIcon[a.kind];
          return (
            <li key={a.id} className="flex items-start gap-3 p-4">
              <span
                className={cn(
                  "inline-flex size-9 shrink-0 items-center justify-center rounded-lg",
                  a.kind === "payment-failure" && "bg-destructive/15 text-destructive",
                  a.kind === "churn-risk" && "bg-destructive/10 text-destructive",
                  a.kind === "unassigned-visit" && "bg-primary/10 text-primary",
                )}
              >
                <Icon className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-foreground">{a.title}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {a.detail}
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0">
                {a.action}
              </Button>
            </li>
          );
        })}
      </ul>
    </article>
  );
}


// ---------------------------------------------------------------------------
// New booking sheet
// ---------------------------------------------------------------------------

function NewBookingSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const [plan, setPlan] = useState<Plan>("Complete");
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <div className="flex items-center justify-between">
            <SheetTitle className="font-display text-2xl font-extrabold tracking-tight">
              New booking
            </SheetTitle>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              aria-label="Close"
              className="inline-flex size-8 items-center justify-center rounded-full hover:bg-surface"
            >
              <X className="size-4" />
            </button>
          </div>
          <SheetDescription>
            Create a subscriber booking manually. This is not a walk-through.
          </SheetDescription>
        </SheetHeader>

        <form
          className="mt-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            onOpenChange(false);
          }}
        >
          <Field label="Homeowner name">
            <Input placeholder="Jane Doe" />
          </Field>
          <Field label="Email">
            <Input type="email" placeholder="jane@example.com" />
          </Field>
          <Field label="Phone">
            <Input type="tel" placeholder="(905) 555-0123" />
          </Field>
          <Field label="Street address">
            <Input placeholder="123 Example Rd" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="City">
              <Select defaultValue="Mississauga">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="Mississauga">Mississauga</SelectItem>
                  <SelectItem value="Oakville">Oakville</SelectItem>
                  <SelectItem value="Milton">Milton</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Postal code">
              <Input placeholder="L5L 0A0" />
            </Field>
          </div>
          <Field label="Plan">
            <Select value={plan} onValueChange={(v) => setPlan(v as Plan)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Essential">Essential — $129/mo</SelectItem>
                <SelectItem value="Complete">Complete — $189/mo</SelectItem>
                <SelectItem value="Premier">Premier — $289/mo</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Billing">
            <Select defaultValue="monthly">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="monthly">Monthly</SelectItem>
                <SelectItem value="annual">Annual (saves ~17%)</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Internal note (optional)">
            <textarea
              rows={3}
              placeholder="Anything ops should know about this booking…"
              className="w-full resize-none rounded-md border border-input bg-background p-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </Field>

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit">Create booking</Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {children}
    </label>
  );
}
