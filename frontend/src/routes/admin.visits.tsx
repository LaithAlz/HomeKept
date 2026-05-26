import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Clock, MapPin, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { subscribers, formatDateTime } from "@/lib/mock-admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/visits")({
  head: () => ({
    meta: [
      { title: "Visits — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: VisitsPage,
});

type Status = "scheduled" | "in-progress" | "completed" | "unassigned";

interface VisitRow {
  id: string;
  customer: string;
  city: string;
  plan: string;
  date: string;
  tech: string;
  status: Status;
  services: number;
}

function build(): VisitRow[] {
  const rows: VisitRow[] = subscribers
    .filter((s) => s.nextVisit)
    .map((s, i) => ({
      id: `v_${s.id}`,
      customer: s.name,
      city: `${s.city} · ${s.neighbourhood}`,
      plan: s.plan,
      date: s.nextVisit!.date,
      tech: s.nextVisit!.technician,
      status: s.nextVisit!.technician === "Unassigned" ? "unassigned" : i === 0 ? "in-progress" : "scheduled",
      services: 3 + (i % 3),
    }));
  // a few completed
  const past = ["Priya Sharma", "Mark & Helen Chen", "Greg & Lisa Park"];
  past.forEach((name, i) => {
    rows.push({
      id: `pv_${i}`,
      customer: name,
      city: "Mississauga · Erin Mills",
      plan: "Complete",
      date: new Date(Date.now() - (i + 1) * 86400000 * 7).toISOString(),
      tech: "Marcus T.",
      status: "completed",
      services: 4,
    });
  });
  return rows;
}

const TABS: { id: Status | "all"; label: string }[] = [
  { id: "all", label: "All" },
  { id: "in-progress", label: "In progress" },
  { id: "scheduled", label: "Scheduled" },
  { id: "unassigned", label: "Unassigned" },
  { id: "completed", label: "Completed" },
];

const TONE: Record<Status, string> = {
  scheduled: "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  "in-progress": "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  completed: "bg-muted text-muted-foreground",
  unassigned: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
};

function VisitsPage() {
  const [tab, setTab] = useState<Status | "all">("all");
  const rows = build();
  const filtered = tab === "all" ? rows : rows.filter((r) => r.status === tab);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Visits</h1>
          <p className="mt-1 text-sm text-muted-foreground">All scheduled, in-progress, and completed visits.</p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap gap-2">
        {TABS.map((t) => {
          const count = t.id === "all" ? rows.length : rows.filter((r) => r.status === t.id).length;
          return (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={cn(
                "rounded-full px-3.5 py-1.5 text-sm transition",
                tab === t.id ? "bg-foreground text-background" : "bg-card text-muted-foreground hover:text-foreground border border-border",
              )}
            >
              {t.label} <span className="ml-1 opacity-70">{count}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-4 space-y-2">
        {filtered.map((v) => (
          <div key={v.id} className="flex flex-wrap items-center gap-4 rounded-2xl border border-border bg-card p-4">
            <div className={cn("rounded-full px-2 py-0.5 text-xs font-medium", TONE[v.status])}>
              {v.status.replace("-", " ")}
            </div>
            <div className="flex-1 min-w-[200px]">
              <div className="font-medium">{v.customer}</div>
              <div className="mt-0.5 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {v.city}</span>
                <span>{v.plan} · {v.services} services</span>
              </div>
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Clock className="h-4 w-4" /> {formatDateTime(v.date)}
            </div>
            <div className="flex items-center gap-2 text-sm">
              <User className="h-4 w-4 text-muted-foreground" />
              <span className={v.tech === "Unassigned" ? "text-amber-700 dark:text-amber-300" : ""}>{v.tech}</span>
            </div>
            <Button size="sm" variant="outline">
              {v.status === "unassigned" ? "Assign" : v.status === "completed" ? "View report" : "Open"}
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
