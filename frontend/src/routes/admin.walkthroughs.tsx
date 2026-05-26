import { createFileRoute } from "@tanstack/react-router";
import { Calendar, MapPin, Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { pendingWalkthroughs, formatDateTime } from "@/lib/mock-admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/walkthroughs")({
  head: () => ({
    meta: [
      { title: "Walk-throughs — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: WalkthroughsPage,
});

function WalkthroughsPage() {
  const confirmed = pendingWalkthroughs.filter((w) => w.confirmed);
  const unconfirmed = pendingWalkthroughs.filter((w) => !w.confirmed);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Walk-throughs</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Free 90-minute in-home visits across Mississauga, Oakville, and Milton.
          </p>
        </div>
        <Button size="sm">Schedule walk-through</Button>
      </div>

      <div className="mt-6 grid grid-cols-3 gap-3">
        <Stat label="This week" value={pendingWalkthroughs.length} hint="scheduled" />
        <Stat label="Confirmed" value={confirmed.length} hint="ready to go" />
        <Stat label="Awaiting reply" value={unconfirmed.length} hint="needs follow-up" />
      </div>

      <Section title="Needs confirmation" items={unconfirmed} highlight />
      <Section title="Confirmed" items={confirmed} />
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: number; hint: string }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 font-display text-2xl font-extrabold">{value}</div>
      <div className="text-xs text-muted-foreground">{hint}</div>
    </div>
  );
}

function Section({
  title,
  items,
  highlight,
}: {
  title: string;
  items: typeof pendingWalkthroughs;
  highlight?: boolean;
}) {
  if (items.length === 0) return null;
  return (
    <div className="mt-8">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      <div className="mt-3 overflow-hidden rounded-2xl border border-border">
        {items.map((w, i) => (
          <div
            key={w.id}
            className={cn(
              "flex flex-wrap items-center gap-4 px-4 py-4",
              i > 0 && "border-t border-border",
              highlight && "bg-amber-500/5",
            )}
          >
            <div className="flex-1 min-w-[200px]">
              <div className="font-medium">{w.homeowner}</div>
              <div className="mt-0.5 flex items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {w.city}</span>
                <span>Source: {w.source}</span>
              </div>
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Calendar className="h-4 w-4" />
              {formatDateTime(w.date)}
            </div>
            <div className="flex gap-2">
              {!w.confirmed ? (
                <>
                  <Button size="sm" variant="outline"><Check className="mr-1 h-3.5 w-3.5" /> Confirm</Button>
                  <Button size="sm" variant="ghost"><X className="mr-1 h-3.5 w-3.5" /> Cancel</Button>
                </>
              ) : (
                <Button size="sm" variant="outline">Open prep notes</Button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
