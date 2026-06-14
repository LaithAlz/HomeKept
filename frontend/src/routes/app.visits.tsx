import { createFileRoute, Link } from "@tanstack/react-router";
import { CheckCircle2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { subscriber } from "@/lib/mock-account";
import { mockVisits } from "@/lib/mock-account";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/app/visits")({
  head: () => ({
    meta: [{ title: "Visits — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: VisitsPage,
});

function VisitsPage() {
  const next = subscriber.nextVisit;
  const nextDate = new Date(next.date);

  const pastVisits = mockVisits.filter((v) => v.status === "COMPLETED");

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Visits</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Every past and upcoming visit on your home, with photo reports and notes.
      </p>

      <section className="mt-8" aria-labelledby="up-next-heading">
        <h2 id="up-next-heading" className="text-xs uppercase tracking-wide text-muted-foreground">
          Up next
        </h2>
        <div className="mt-3 rounded-3xl border border-border bg-card p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <DateBlock date={nextDate} />
              <div>
                <div className="font-display text-xl font-bold">
                  {nextDate.toLocaleDateString("en-CA", { weekday: "long" })} · {next.window}
                </div>
                <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
                  <User className="h-4 w-4" aria-hidden="true" /> with {next.technicianFirstName}
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {next.services.map((s) => (
                    <span
                      key={s.id}
                      className="rounded-full border border-border bg-background px-2.5 py-0.5 text-xs"
                    >
                      {s.label}
                    </span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <Link to="/app/visits/$id" params={{ id: "next" }}>
                <Button size="sm" variant="outline">
                  View details
                </Button>
              </Link>
              <Button size="sm">Add request</Button>
            </div>
          </div>
        </div>
      </section>

      <section className="mt-10" aria-labelledby="past-visits-heading">
        <h2
          id="past-visits-heading"
          className="text-xs uppercase tracking-wide text-muted-foreground"
        >
          Past visits
        </h2>
        <div className="mt-3 space-y-3">
          {pastVisits.map((v) => {
            const d = new Date(v.dateISO);
            return (
              <div key={v.id} className="rounded-3xl border border-border bg-card p-5">
                <div className="flex flex-wrap items-start gap-4">
                  <DateBlock date={d} muted />
                  <div className="flex-1">
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />{" "}
                      Completed by {v.technician}
                    </div>
                    <div className="mt-1 font-medium">{v.summary}</div>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
                      {v.services.map((s) => (
                        <span
                          key={s.id}
                          className="rounded-full border border-border bg-background px-2 py-0.5"
                        >
                          {s.label}
                        </span>
                      ))}
                    </div>
                    {v.flagged && (
                      <div className="mt-3 rounded-xl bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
                        Flagged: {v.flagged}
                      </div>
                    )}
                  </div>
                  <Link to="/app/visits/$id" params={{ id: v.id }}>
                    <Button size="sm" variant="outline">
                      Open report
                    </Button>
                  </Link>
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
    <div
      className={cn(
        "flex h-16 w-16 flex-col items-center justify-center rounded-2xl border text-center",
        muted
          ? "border-border bg-background text-muted-foreground"
          : "border-primary/30 bg-primary/10 text-primary",
      )}
      aria-hidden="true"
    >
      <div className="text-[10px] font-semibold uppercase tracking-wide">
        {date.toLocaleString("en-CA", { month: "short" })}
      </div>
      <div className="font-display text-2xl font-extrabold leading-none">{date.getDate()}</div>
    </div>
  );
}
