import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Search, Download, Mail, MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import {
  subscribers,
  formatCAD,
  formatDateShort,
  type Subscriber,
} from "@/lib/mock-admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/subscribers")({
  head: () => ({
    meta: [
      { title: "Subscribers — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: SubscribersPage,
});

const STATUS_LABEL: Record<Subscriber["status"], string> = {
  active: "Active",
  "first-visit": "First visit",
  "payment-issue": "Payment issue",
  "at-risk": "At risk",
  paused: "Paused",
};

const STATUS_TONE: Record<Subscriber["status"], string> = {
  active: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  "first-visit": "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  "payment-issue": "bg-rose-500/10 text-rose-700 dark:text-rose-300",
  "at-risk": "bg-amber-500/10 text-amber-700 dark:text-amber-300",
  paused: "bg-muted text-muted-foreground",
};

function SubscribersPage() {
  const [q, setQ] = useState("");
  const [city, setCity] = useState<string>("all");
  const [plan, setPlan] = useState<string>("all");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [openSub, setOpenSub] = useState<Subscriber | null>(null);

  const rows = useMemo(() => {
    return subscribers.filter((s) => {
      if (city !== "all" && s.city !== city) return false;
      if (plan !== "all" && s.plan !== plan) return false;
      if (q && !`${s.name} ${s.street} ${s.neighbourhood}`.toLowerCase().includes(q.toLowerCase()))
        return false;
      return true;
    });
  }, [q, city, plan]);

  const allChecked = rows.length > 0 && rows.every((r) => selected.has(r.id));
  const toggleAll = () => {
    const next = new Set(selected);
    if (allChecked) rows.forEach((r) => next.delete(r.id));
    else rows.forEach((r) => next.add(r.id));
    setSelected(next);
  };
  const toggle = (id: string) => {
    const next = new Set(selected);
    next.has(id) ? next.delete(id) : next.add(id);
    setSelected(next);
  };

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">
            Subscribers
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {rows.length} of {subscribers.length} households · filter, segment, and act in bulk.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" /> Export CSV
          </Button>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search name or address"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={city} onValueChange={setCity}>
          <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All cities</SelectItem>
            <SelectItem value="Mississauga">Mississauga</SelectItem>
            <SelectItem value="Oakville">Oakville</SelectItem>
            <SelectItem value="Milton">Milton</SelectItem>
          </SelectContent>
        </Select>
        <Select value={plan} onValueChange={setPlan}>
          <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All plans</SelectItem>
            <SelectItem value="Essential">Essential</SelectItem>
            <SelectItem value="Complete">Complete</SelectItem>
            <SelectItem value="Premier">Premier</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {selected.size > 0 && (
        <div className="mt-4 flex items-center justify-between rounded-xl border border-border bg-card px-4 py-3 text-sm">
          <span>{selected.size} selected</span>
          <div className="flex gap-2">
            <Button size="sm" variant="outline"><Mail className="mr-2 h-3.5 w-3.5" /> Email</Button>
            <Button size="sm" variant="outline">Move plan</Button>
            <Button size="sm" variant="outline">Pause</Button>
          </div>
        </div>
      )}

      <div className="mt-4 overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="w-10 px-4 py-3">
                <Checkbox checked={allChecked} onCheckedChange={toggleAll} />
              </th>
              <th className="px-2 py-3">Name</th>
              <th className="px-2 py-3">City / area</th>
              <th className="px-2 py-3">Plan</th>
              <th className="px-2 py-3">Status</th>
              <th className="px-2 py-3 text-right">MRR</th>
              <th className="px-2 py-3">Next visit</th>
              <th className="w-10"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((s) => (
              <tr key={s.id} className="border-t border-border hover:bg-muted/30">
                <td className="px-4 py-3"><Checkbox checked={selected.has(s.id)} onCheckedChange={() => toggle(s.id)} /></td>
                <td className="px-2 py-3">
                  <button onClick={() => setOpenSub(s)} className="font-medium text-foreground hover:underline">
                    {s.name}
                  </button>
                  <div className="text-xs text-muted-foreground">{s.street}</div>
                </td>
                <td className="px-2 py-3 text-muted-foreground">
                  {s.city} · <span className="text-foreground/80">{s.neighbourhood}</span>
                </td>
                <td className="px-2 py-3">{s.plan}</td>
                <td className="px-2 py-3">
                  <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", STATUS_TONE[s.status])}>
                    {STATUS_LABEL[s.status]}
                  </span>
                </td>
                <td className="px-2 py-3 text-right tabular-nums">{formatCAD(s.mrr)}</td>
                <td className="px-2 py-3 text-muted-foreground">
                  {s.nextVisit ? formatDateShort(s.nextVisit.date) : "—"}
                </td>
                <td className="px-2 py-3"><Button variant="ghost" size="icon" className="h-7 w-7"><MoreHorizontal className="h-4 w-4" /></Button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Sheet open={!!openSub} onOpenChange={(v) => !v && setOpenSub(null)}>
        <SheetContent className="w-full sm:max-w-md">
          {openSub && (
            <>
              <SheetHeader>
                <SheetTitle>{openSub.name}</SheetTitle>
                <SheetDescription>
                  {openSub.street}, {openSub.neighbourhood}, {openSub.city}
                </SheetDescription>
              </SheetHeader>
              <div className="mt-6 space-y-4 text-sm">
                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-xl border border-border p-3">
                    <div className="text-xs text-muted-foreground">Plan</div>
                    <div className="font-medium">{openSub.plan}</div>
                  </div>
                  <div className="rounded-xl border border-border p-3">
                    <div className="text-xs text-muted-foreground">MRR</div>
                    <div className="font-medium tabular-nums">{formatCAD(openSub.mrr)}</div>
                  </div>
                  <div className="rounded-xl border border-border p-3">
                    <div className="text-xs text-muted-foreground">Status</div>
                    <div className="font-medium">{STATUS_LABEL[openSub.status]}</div>
                  </div>
                  <div className="rounded-xl border border-border p-3">
                    <div className="text-xs text-muted-foreground">Next visit</div>
                    <div className="font-medium">
                      {openSub.nextVisit ? formatDateShort(openSub.nextVisit.date) : "—"}
                    </div>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" className="flex-1">Open visit history</Button>
                  <Button size="sm" variant="outline">Email</Button>
                </div>
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}
