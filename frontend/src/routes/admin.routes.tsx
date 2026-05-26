import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { ChevronLeft, ChevronRight, GripVertical, MapPin } from "lucide-react";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/admin/routes")({
  head: () => ({
    meta: [
      { title: "Routes — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: RoutesPage,
});

interface Stop {
  id: string;
  time: string;
  customer: string;
  address: string;
  duration: number;
}

interface TechRoute {
  tech: string;
  stops: Stop[];
  driveMin: number;
}

const data: TechRoute[] = [
  {
    tech: "Marcus T.",
    driveMin: 47,
    stops: [
      { id: "m1", time: "8:30 AM", customer: "Priya Sharma", address: "14 Maple Ridge Crt, Erin Mills", duration: 90 },
      { id: "m2", time: "10:30 AM", customer: "Mark & Helen Chen", address: "27 Bronte Creek Dr, Oakville", duration: 90 },
      { id: "m3", time: "1:00 PM", customer: "Olivia & Tom Ward", address: "9 Lakeshore Rd E, Old Oakville", duration: 60 },
      { id: "m4", time: "3:00 PM", customer: "Daniel Nguyen", address: "8 Whitehorn Pl, Milton", duration: 60 },
    ],
  },
  {
    tech: "Sasha P.",
    driveMin: 32,
    stops: [
      { id: "s1", time: "9:00 AM", customer: "Greg & Lisa Park", address: "55 Glenashton Dr, Oakville", duration: 90 },
      { id: "s2", time: "11:30 AM", customer: "Aiko Tanaka", address: "102 Lorne Park Rd, Mississauga", duration: 60 },
      { id: "s3", time: "2:00 PM", customer: "Rohan Mehta", address: "311 Britannia Rd W, Milton", duration: 60 },
    ],
  },
];

function RoutesPage() {
  const [dayOffset, setDayOffset] = useState(0);
  const date = new Date();
  date.setDate(date.getDate() + dayOffset);
  const label = date.toLocaleDateString("en-CA", { weekday: "long", month: "long", day: "numeric" });

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Routes</h1>
          <p className="mt-1 text-sm text-muted-foreground">Drag to reorder. Drive time recomputes automatically.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button size="icon" variant="outline" onClick={() => setDayOffset(dayOffset - 1)}><ChevronLeft className="h-4 w-4" /></Button>
          <div className="min-w-[200px] rounded-lg border border-border bg-card px-4 py-2 text-center text-sm font-medium">{label}</div>
          <Button size="icon" variant="outline" onClick={() => setDayOffset(dayOffset + 1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        {data.map((r) => (
          <div key={r.tech} className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-center justify-between">
              <h2 className="font-display text-lg font-bold">{r.tech}</h2>
              <div className="text-xs text-muted-foreground">
                {r.stops.length} stops · {r.driveMin} min driving
              </div>
            </div>
            <div className="mt-4 space-y-2">
              {r.stops.map((s, i) => (
                <div key={s.id} className="group relative flex items-center gap-3 rounded-xl border border-border bg-background p-3 hover:border-foreground/20">
                  <GripVertical className="h-4 w-4 cursor-grab text-muted-foreground opacity-0 transition group-hover:opacity-100" />
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">{i + 1}</div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="font-medium">{s.customer}</span>
                      <span className="text-xs tabular-nums text-muted-foreground">{s.time}</span>
                    </div>
                    <div className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                      <MapPin className="h-3 w-3" /> {s.address}
                    </div>
                  </div>
                  <div className="text-xs tabular-nums text-muted-foreground">{s.duration}m</div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
