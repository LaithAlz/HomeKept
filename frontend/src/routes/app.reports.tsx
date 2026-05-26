import { createFileRoute } from "@tanstack/react-router";
import { Camera, Download, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/app/reports")({
  head: () => ({
    meta: [
      { title: "Reports — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: ReportsPage,
});

interface Report {
  id: string;
  title: string;
  date: string;
  photoCount: number;
  pageCount: number;
  highlights: string[];
}

const reports: Report[] = [
  {
    id: "r1",
    title: "Spring readiness report",
    date: new Date(Date.now() - 6 * 86400000).toISOString(),
    photoCount: 12,
    pageCount: 4,
    highlights: ["AC startup passed", "Gutters cleared", "Dryer vent flagged"],
  },
  {
    id: "r2",
    title: "Winter mid-season check",
    date: new Date(Date.now() - 67 * 86400000).toISOString(),
    photoCount: 9,
    pageCount: 3,
    highlights: ["Furnace efficient", "Caulking holding", "CO detectors pass"],
  },
  {
    id: "r3",
    title: "Fall prep report",
    date: new Date(Date.now() - 128 * 86400000).toISOString(),
    photoCount: 14,
    pageCount: 5,
    highlights: ["Hose bibs drained", "Roof condition: good", "Sump pump cycled"],
  },
  {
    id: "r4",
    title: "Summer exterior review",
    date: new Date(Date.now() - 200 * 86400000).toISOString(),
    photoCount: 18,
    pageCount: 6,
    highlights: ["Deck stain due 2027", "Window screens repaired", "Patio drains clear"],
  },
];

function ReportsPage() {
  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Reports</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Photo reports and seasonal summaries from every visit, in one place.
      </p>

      <div className="mt-8 grid gap-4 md:grid-cols-2">
        {reports.map((r) => {
          const d = new Date(r.date);
          return (
            <div key={r.id} className="group rounded-3xl border border-border bg-card p-5 transition hover:border-foreground/20">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div>
                    <h3 className="font-display text-lg font-bold">{r.title}</h3>
                    <div className="mt-0.5 text-xs text-muted-foreground">
                      {d.toLocaleDateString("en-CA", { month: "long", day: "numeric", year: "numeric" })}
                    </div>
                  </div>
                </div>
                <Button size="icon" variant="ghost" className="h-8 w-8"><Download className="h-4 w-4" /></Button>
              </div>

              <ul className="mt-4 space-y-1 text-sm text-muted-foreground">
                {r.highlights.map((h) => (
                  <li key={h} className="flex items-center gap-2">
                    <span className="h-1 w-1 rounded-full bg-foreground/40" /> {h}
                  </li>
                ))}
              </ul>

              <div className="mt-5 flex items-center justify-between text-xs text-muted-foreground">
                <div className="flex items-center gap-3">
                  <span className="flex items-center gap-1"><Camera className="h-3.5 w-3.5" /> {r.photoCount} photos</span>
                  <span>{r.pageCount} pages</span>
                </div>
                <Button size="sm" variant="outline">Open</Button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
