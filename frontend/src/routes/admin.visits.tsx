import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Loader2, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatDateTime } from "@/lib/format";
import { useAdminVisits, formatCentsCAD, type AdminVisitListItem } from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/visits")({
  head: () => ({
    meta: [{ title: "Visits — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: VisitsPage,
});

const STATUS_LABEL: Record<string, string> = {
  SCHEDULED: "Scheduled",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  INCOMPLETE: "Incomplete",
  CANCELLED: "Cancelled",
  RESCHEDULED: "Rescheduled",
};

const STATUS_TONE: Record<string, string> = {
  SCHEDULED: "bg-sky-500/10 text-sky-700",
  IN_PROGRESS: "bg-emerald-500/10 text-emerald-700",
  COMPLETED: "bg-muted text-muted-foreground",
  INCOMPLETE: "bg-amber-500/10 text-amber-700",
  CANCELLED: "bg-muted text-muted-foreground",
  RESCHEDULED: "bg-sky-500/10 text-sky-700",
};

const TYPE_LABEL: Record<string, string> = {
  ROUTINE: "Routine",
  EXTRA: "Extra",
  WARRANTY: "Warranty",
  WALKTHROUGH: "Walkthrough",
};

function VisitsPage() {
  const { data: visits, isLoading, isError, refetch } = useAdminVisits({ limit: 100 });
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [type, setType] = useState<string>("all");

  const rows = useMemo(() => {
    if (!visits) return [];
    return visits.filter((v) => {
      if (status !== "all" && v.status !== status) return false;
      if (type !== "all" && v.type !== type) return false;
      if (q && !String(v.subscriberId).includes(q.trim())) return false;
      return true;
    });
  }, [visits, q, status, type]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Visits</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {visits ? `${rows.length} of ${visits.length} visits` : "Loading visits…"}
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-56">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="visit-search" className="sr-only">
            Search by subscriber ID
          </label>
          <Input
            id="visit-search"
            placeholder="Search by subscriber ID"
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
        <Select value={type} onValueChange={setType}>
          <SelectTrigger className="w-40" aria-label="Filter by type">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All types</SelectItem>
            {Object.entries(TYPE_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading visits.
        </div>
      )}

      {isError && !isLoading && (
        <div
          role="alert"
          className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>We couldn't load visits.</span>
          <Button size="sm" variant="outline" onClick={() => void refetch()}>
            Try again
          </Button>
        </div>
      )}

      {visits && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Visit</th>
                <th className="px-2 py-3">Type</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3">Scheduled for</th>
                <th className="px-2 py-3">Technician</th>
                <th className="px-2 py-3 text-right">Materials</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <VisitRow key={v.id} visit={v} />
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No visits match these filters.
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

function VisitRow({ visit: v }: { visit: AdminVisitListItem }) {
  return (
    <tr className="border-t border-border hover:bg-muted/30">
      <td className="px-4 py-3">
        <div className="font-medium text-foreground">Visit #{v.id}</div>
        <div className="text-xs text-muted-foreground">
          Subscriber #{v.subscriberId} · Property #{v.propertyId}
        </div>
      </td>
      <td className="px-2 py-3">{TYPE_LABEL[v.type] ?? v.type}</td>
      <td className="px-2 py-3">
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            STATUS_TONE[v.status] ?? "bg-muted text-muted-foreground",
          )}
        >
          {STATUS_LABEL[v.status] ?? v.status}
        </span>
      </td>
      <td className="px-2 py-3">{formatDateTime(v.scheduledFor)}</td>
      <td className="px-2 py-3">
        {v.technicianId ? (
          `Tech #${v.technicianId}`
        ) : (
          <span className="text-amber-700">Unassigned</span>
        )}
      </td>
      <td className="px-2 py-3 text-right tabular-nums">{formatCentsCAD(v.materialsCostCents)}</td>
    </tr>
  );
}
