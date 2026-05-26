import { createFileRoute } from "@tanstack/react-router";
import { Plus, Wrench } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/catalog")({
  head: () => ({
    meta: [
      { title: "Service catalog — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: CatalogPage,
});

interface Service {
  id: string;
  name: string;
  category: "HVAC" | "Plumbing" | "Exterior" | "Safety" | "Seasonal";
  durationMin: number;
  tools: string[];
  plans: { Essential: boolean; Complete: boolean; Premier: boolean };
}

const services: Service[] = [
  { id: "sv1", name: "Furnace filter replacement", category: "HVAC", durationMin: 15, tools: ["MERV 11 filters"], plans: { Essential: true, Complete: true, Premier: true } },
  { id: "sv2", name: "AC startup & coil rinse", category: "HVAC", durationMin: 30, tools: ["Coil cleaner", "Hose"], plans: { Essential: false, Complete: true, Premier: true } },
  { id: "sv3", name: "Smoke & CO detector test", category: "Safety", durationMin: 10, tools: ["Test spray", "9V batteries"], plans: { Essential: true, Complete: true, Premier: true } },
  { id: "sv4", name: "Gutter clearing & downspout flush", category: "Exterior", durationMin: 45, tools: ["Ladder", "Leaf blower"], plans: { Essential: false, Complete: true, Premier: true } },
  { id: "sv5", name: "Hose bib reconnection (spring)", category: "Seasonal", durationMin: 15, tools: ["Pipe key"], plans: { Essential: true, Complete: true, Premier: true } },
  { id: "sv6", name: "Hose bib drain (fall)", category: "Seasonal", durationMin: 15, tools: ["Pipe key"], plans: { Essential: true, Complete: true, Premier: true } },
  { id: "sv7", name: "Dryer vent cleaning", category: "Safety", durationMin: 45, tools: ["Vent brush kit", "Shop vac"], plans: { Essential: false, Complete: false, Premier: true } },
  { id: "sv8", name: "Water heater flush", category: "Plumbing", durationMin: 60, tools: ["Hose", "Bucket"], plans: { Essential: false, Complete: false, Premier: true } },
  { id: "sv9", name: "Caulking touch-up (exterior)", category: "Exterior", durationMin: 30, tools: ["Caulk gun", "Silicone"], plans: { Essential: false, Complete: true, Premier: true } },
];

const CAT_TONE: Record<Service["category"], string> = {
  HVAC: "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  Plumbing: "bg-blue-500/10 text-blue-700 dark:text-blue-300",
  Exterior: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  Safety: "bg-rose-500/10 text-rose-700 dark:text-rose-300",
  Seasonal: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
};

function CatalogPage() {
  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Service catalog</h1>
          <p className="mt-1 text-sm text-muted-foreground">{services.length} services · default duration, tools, and plan inclusion.</p>
        </div>
        <Button size="sm"><Plus className="mr-2 h-4 w-4" /> New service</Button>
      </div>

      <div className="mt-6 overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Service</th>
              <th className="px-2 py-3">Category</th>
              <th className="px-2 py-3 text-right">Duration</th>
              <th className="px-2 py-3">Tools</th>
              <th className="px-2 py-3 text-center">Essential</th>
              <th className="px-2 py-3 text-center">Complete</th>
              <th className="px-2 py-3 text-center">Premier</th>
            </tr>
          </thead>
          <tbody>
            {services.map((s) => (
              <tr key={s.id} className="border-t border-border hover:bg-muted/30">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <Wrench className="h-3.5 w-3.5 text-muted-foreground" />
                    <span className="font-medium">{s.name}</span>
                  </div>
                </td>
                <td className="px-2 py-3">
                  <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", CAT_TONE[s.category])}>{s.category}</span>
                </td>
                <td className="px-2 py-3 text-right tabular-nums text-muted-foreground">{s.durationMin}m</td>
                <td className="px-2 py-3 text-xs text-muted-foreground">{s.tools.join(", ")}</td>
                {(["Essential", "Complete", "Premier"] as const).map((p) => (
                  <td key={p} className="px-2 py-3 text-center">
                    {s.plans[p] ? <span className="text-emerald-600">●</span> : <span className="text-muted-foreground/40">–</span>}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
