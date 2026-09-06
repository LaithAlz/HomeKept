import { useMemo, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { formatCentsCad } from "@/lib/format";
import {
  useAdminSubscribers,
  subscriberFullName,
  STATUS_LABEL,
  STATUS_TONE,
  PLAN_LABEL,
  type AdminSubscriberListItem,
} from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/customers/")({
  head: () => ({
    meta: [{ title: "Customers — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: CustomersPage,
});

/**
 * Client-side-only sort over the already-fetched page (mirrors the client-side
 * filtering below — the list endpoint has no `sort` param, and the pipeline is small
 * enough at MVP that a single page covers it, same pattern as `useAdminBookings`).
 * "Newest first" is the endpoint's own default ordering (`cursor`-paginated, newest
 * first), so it needs no comparator of its own.
 */
type SortOption = "recent" | "name" | "mrr-desc" | "mrr-asc";

const SORT_LABEL: Record<SortOption, string> = {
  recent: "Newest first",
  name: "Name (A-Z)",
  "mrr-desc": "MRR (highest first)",
  "mrr-asc": "MRR (lowest first)",
};

/** Nameless rows sort after every named row, regardless of direction. */
function compareByName(a: AdminSubscriberListItem, b: AdminSubscriberListItem): number {
  const an = subscriberFullName(a);
  const bn = subscriberFullName(b);
  if (!an && !bn) return 0;
  if (!an) return 1;
  if (!bn) return -1;
  return an.localeCompare(bn);
}

function CustomersPage() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 100 });
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [plan, setPlan] = useState<string>("all");
  const [sort, setSort] = useState<SortOption>("recent");

  const rows = useMemo(() => {
    if (!subscribers) return [];
    const filtered = subscribers.filter((s) => {
      if (status !== "all" && s.status !== status) return false;
      if (plan !== "all" && s.planCode !== plan) return false;
      if (q) {
        const needle = q.trim().toLowerCase();
        const idMatch = String(s.id).includes(q.trim());
        const nameMatch = subscriberFullName(s).toLowerCase().includes(needle);
        const emailMatch = (s.email ?? "").toLowerCase().includes(needle);
        const phoneMatch = (s.phone ?? "").includes(q.trim());
        if (!idMatch && !nameMatch && !emailMatch && !phoneMatch) return false;
      }
      return true;
    });

    if (sort === "name") {
      return [...filtered].sort(compareByName);
    }
    if (sort === "mrr-desc") {
      return [...filtered].sort((a, b) => (b.mrrCents ?? 0) - (a.mrrCents ?? 0));
    }
    if (sort === "mrr-asc") {
      return [...filtered].sort((a, b) => (a.mrrCents ?? 0) - (b.mrrCents ?? 0));
    }
    return filtered;
  }, [subscribers, q, status, plan, sort]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Customers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {subscribers
              ? `${rows.length} of ${subscribers.length} households`
              : "Loading households…"}
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-72">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="customer-search" className="sr-only">
            Search by name, email, phone, or ID
          </label>
          <Input
            id="customer-search"
            placeholder="Search customers"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-44" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {Object.entries(STATUS_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={plan} onValueChange={setPlan}>
          <SelectTrigger className="w-40" aria-label="Filter by plan">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All plans</SelectItem>
            {Object.entries(PLAN_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={sort} onValueChange={(v) => setSort(v as SortOption)}>
          <SelectTrigger className="w-48" aria-label="Sort customers">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {(Object.entries(SORT_LABEL) as [SortOption, string][]).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && <PanelLoading label="Loading customers." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load customers."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {subscribers && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3">
                  ID
                </th>
                <th scope="col" className="px-2 py-3">
                  Name
                </th>
                <th scope="col" className="px-2 py-3">
                  Plan
                </th>
                <th scope="col" className="px-2 py-3">
                  Status
                </th>
                <th scope="col" className="px-4 py-3 text-right">
                  MRR
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <CustomerRow key={s.id} customer={s} />
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No customers match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * The whole row is the click target (founder: "i want the whole row clickable"), via a
 * single `Link` stretched to cover the row (`absolute inset-0` inside a `relative` `tr`)
 * rather than one link per cell — that would plant several identically-destined links in
 * one row, which is worse for screen reader and keyboard users, not better. There are no
 * other interactive elements in this row to trap focus around: every cell here is plain
 * text, so a single focus stop per row is unambiguous. The `#id` text stays visible (and
 * un-linked) since ids are how an operator cross-references Stripe and the database.
 */
function CustomerRow({ customer: s }: { customer: AdminSubscriberListItem }) {
  const fullName = subscriberFullName(s);
  const hasName = fullName.length > 0;

  return (
    <tr className="relative border-t border-border hover:bg-muted/30">
      <td className="px-4 py-3">
        <Link
          to="/admin/customers/$id"
          params={{ id: String(s.id) }}
          className="absolute inset-0 rounded-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
        >
          <span className="sr-only">View {hasName ? fullName : `customer #${s.id}`}</span>
        </Link>
        <span className="font-medium text-foreground">#{s.id}</span>
      </td>
      <td className="px-2 py-3">
        {hasName ? (
          <div>
            <div className="font-medium text-foreground">{fullName}</div>
            {s.email && <div className="text-xs text-muted-foreground">{s.email}</div>}
          </div>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-2 py-3">{s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "—"}</td>
      <td className="px-2 py-3">
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            STATUS_TONE[s.status] ?? "bg-muted text-muted-foreground",
          )}
        >
          {STATUS_LABEL[s.status] ?? s.status}
        </span>
      </td>
      <td className="px-4 py-3 text-right tabular-nums">{formatCentsCad(s.mrrCents)}</td>
    </tr>
  );
}
