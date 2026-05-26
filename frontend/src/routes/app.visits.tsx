import { createFileRoute } from "@tanstack/react-router";
import { Calendar, CheckCircle2, Clock, MapPin, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { subscriber } from "@/lib/mock-account";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/visits")({
  head: () => ({
    meta: [
      { title: "Visits — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: VisitsPage,
});

interface PastVisit {
  id: string;
  dateISO: string;
  technician: string;
  summary: string;
  services: string[];
  flagged?: string;
}

const past: PastVisit[] = [
  {
    id: "p1",
    dateISO: new Date(Date.now() - 6 * 86400000).toISOString(),
    technician: "Marcus T.",
    summary: "Spring readiness — 12 checkpoints, 1 flagged.",
    services: ["HVAC filter swap", "Smoke detector test", "Front gutter clearing"],
    flagged: "Dryer vent needs cleaning — recommended next visit.",
  },
  {
    id: "p2",
    dateISO: new Date(Date.now() - 67 * 86400000).toISOString(),
    technician: "Marcus T.",
    summary: "Winter check — all systems clear.",
    services: ["Furnace inspection", "Hose bib drain", "Caulking check (exterior)"],
  },
  {
    id: "p3",
    dateISO: new Date(Date.now() - 128 * 86400000).toISOString(),
    technician: "Sasha P.",
    summary: "Fall prep — gutters cleared, irrigation drained.",
    services: ["Gutter clearing", "Irrigation winterize", "Smoke + CO test"],
  },
];

function VisitsPage() {
  const next = subscriber.nextVisit;
  const nextDate = new Date(next.date);

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Visits</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Every past and upcoming visit on your home, with photo reports and notes.
      </p>

      <section className="mt-8">
        <h2 className="text-xs uppercase tracking-wide text-muted-foreground">Up next</h2>
        <div className="mt-3 rounded-3xl border border-border bg-card p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <DateBlock date={nextDate} />
              <div>
                <div className="font-display text-xl font-bold">
                  {nextDate.toLocaleDateString("en-CA", { weekday: "long" })} · {next.window}
                </div>
                <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
                  <User className="h-4 w-4" /> with {next.technicianFirstName}
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {next.services.map((s) => (
                    <span key={s.id} className="rounded-full border border-border bg-background px-2.5 py-0.5 text-xs">
                      {s.label}
                    </span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="outline">Reschedule</Button>
              <Button size="sm">Add request</Button>
            </div>
          </div>
        </div>
      </section>

      <section className="mt-10">
        <h2 className="text-xs uppercase tracking-wide text-muted-foreground">Past visits</h2>
        <div className="mt-3 space-y-3">
          {past.map((v) => {
            const d = new Date(v.dateISO);
            return (
              <div key={v.id} className="rounded-3xl border border-border bg-card p-5">
                <div className="flex flex-wrap items-start gap-4">
                  <DateBlock date={d} muted />
                  <div className="flex-1">
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> Completed by {v.technician}
                    </div>
                    <div className="mt-1 font-medium">{v.summary}</div>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
                      {v.services.map((s) => (
                        <span key={s} className="rounded-full border border-border bg-background px-2 py-0.5">{s}</span>
                      ))}
                    </div>
                    {v.flagged && (
                      <div className="mt-3 rounded-xl bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
                        Flagged: {v.flagged}
                      </div>
                    )}
                  </div>
                  <Button size="sm" variant="outline">Open report</Button>
                </div>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function DateBlock({ date, muted }: { date: Date; muted?: boolean }) {
  return (
    <div className={cn(
      "flex h-16 w-16 flex-col items-center justify-center rounded-2xl border text-center",
      muted ? "border-border bg-background text-muted-foreground" : "border-primary/30 bg-primary/10 text-primary",
    )}>
      <div className="text-[10px] font-semibold uppercase tracking-wide">
        {date.toLocaleString("en-CA", { month: "short" })}
      </div>
      <div className="font-display text-2xl font-extrabold leading-none">{date.getDate()}</div>
    </div>
  );
}
