import { createFileRoute } from "@tanstack/react-router";
import { Award, Calendar, Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/technicians")({
  head: () => ({
    meta: [
      { title: "Technicians — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: TechniciansPage,
});

interface Tech {
  id: string;
  name: string;
  initials: string;
  region: string;
  status: "available" | "on-visit" | "off";
  visitsThisWeek: number;
  visitsCapacity: number;
  rating: number;
  certs: string[];
  tenureMonths: number;
}

const techs: Tech[] = [
  { id: "t1", name: "Marcus Thompson", initials: "MT", region: "Mississauga & Oakville", status: "on-visit", visitsThisWeek: 32, visitsCapacity: 36, rating: 4.9, certs: ["Gas Tech 2", "WHMIS", "Working at heights"], tenureMonths: 18 },
  { id: "t2", name: "Sasha Petrov", initials: "SP", region: "Oakville & Burlington", status: "available", visitsThisWeek: 28, visitsCapacity: 32, rating: 4.8, certs: ["Gas Tech 2", "WHMIS"], tenureMonths: 11 },
  { id: "t3", name: "Devon Harper", initials: "DH", region: "Milton & North Oakville", status: "available", visitsThisWeek: 24, visitsCapacity: 32, rating: 4.7, certs: ["WHMIS", "First aid"], tenureMonths: 5 },
  { id: "t4", name: "Priscilla Adeyemi", initials: "PA", region: "Mississauga (training)", status: "off", visitsThisWeek: 6, visitsCapacity: 16, rating: 4.9, certs: ["WHMIS"], tenureMonths: 1 },
];

const TONE = {
  available: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  "on-visit": "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  off: "bg-muted text-muted-foreground",
};

function TechniciansPage() {
  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Technicians</h1>
          <p className="mt-1 text-sm text-muted-foreground">{techs.length} active · regions, certifications, and weekly load.</p>
        </div>
        <Button size="sm">Add technician</Button>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {techs.map((t) => {
          const pct = Math.round((t.visitsThisWeek / t.visitsCapacity) * 100);
          return (
            <div key={t.id} className="rounded-2xl border border-border bg-card p-5">
              <div className="flex items-start gap-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 font-display text-base font-bold text-primary">
                  {t.initials}
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h3 className="font-medium">{t.name}</h3>
                    <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", TONE[t.status])}>
                      {t.status.replace("-", " ")}
                    </span>
                  </div>
                  <div className="mt-0.5 text-xs text-muted-foreground">
                    {t.region} · {t.tenureMonths} mo tenure
                  </div>
                  <div className="mt-2 flex items-center gap-3 text-sm">
                    <span className="flex items-center gap-1"><Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" /> {t.rating}</span>
                    <span className="text-muted-foreground">·</span>
                    <span className="flex items-center gap-1 text-muted-foreground"><Calendar className="h-3.5 w-3.5" /> {t.visitsThisWeek} visits this week</span>
                  </div>
                </div>
              </div>

              <div className="mt-4">
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>Weekly load</span>
                  <span className="tabular-nums">{t.visitsThisWeek} / {t.visitsCapacity} · {pct}%</span>
                </div>
                <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
                  <div className="h-full bg-primary" style={{ width: `${pct}%` }} />
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {t.certs.map((c) => (
                  <span key={c} className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs">
                    <Award className="h-3 w-3" /> {c}
                  </span>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
