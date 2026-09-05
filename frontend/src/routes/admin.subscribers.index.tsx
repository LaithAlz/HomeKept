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

export const Route = createFileRoute("/admin/subscribers/")({
  head: () => ({
    meta: [{ title: "Subscribers — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: SubscribersPage,
});

function SubscribersPage() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 100 });
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [plan, setPlan] = useState<string>("all");

  const rows = useMemo(() => {
    if (!subscribers) return [];
    return subscribers.filter((s) => {
      if (status !== "all" && s.status !== status) return false;
      if (plan !== "all" && s.planCode !== plan) return false;
      if (q) {
        const needle = q.trim().toLowerCase();
        const idMatch = String(s.id).includes(q.trim());
        const nameMatch = subscriberFullName(s).toLowerCase().includes(needle);
        const emailMatch = (s.email ?? "").toLowerCase().includes(needle);
        if (!idMatch && !nameMatch && !emailMatch) return false;
      }
      return true;
    });
  }, [subscribers, q, status, plan]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Subscribers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {subscribers
              ? `${rows.length} of ${subscribers.length} households`
              : "Loading households…"}
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-56">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="subscriber-search" className="sr-only">
            Search by name, email, or subscriber ID
          </label>
          <Input
            id="subscriber-search"
            placeholder="Search by name, email, or ID"
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
      </div>

      {isLoading && <PanelLoading label="Loading subscribers." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load subscribers."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {subscribers && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-2 py-3">Name</th>
                <th className="px-2 py-3">Plan</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3 text-right">MRR</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <SubscriberRow key={s.id} subscriber={s} />
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No subscribers match these filters.
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

function SubscriberRow({ subscriber: s }: { subscriber: AdminSubscriberListItem }) {
  const fullName = subscriberFullName(s);
  const hasName = fullName.length > 0;

  return (
    <tr className="border-t border-border hover:bg-muted/30">
      <td className="px-4 py-3">
        <Link
          to="/admin/subscribers/$id"
          params={{ id: String(s.id) }}
          className="rounded font-medium text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          #{s.id}
        </Link>
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
      <td className="px-2 py-3 text-right tabular-nums">{formatCentsCad(s.mrrCents)}</td>
    </tr>
  );
}
