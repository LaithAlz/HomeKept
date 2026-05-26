import { createFileRoute } from "@tanstack/react-router";
import { Mail, Phone, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/leads")({
  head: () => ({
    meta: [
      { title: "Leads — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: LeadsPage,
});

type LeadStatus = "new" | "contacted" | "qualified" | "cold";

interface Lead {
  id: string;
  name: string;
  city: string;
  source: "Nextdoor" | "Referral" | "Facebook group" | "Door-knock" | "Website";
  email: string;
  phone: string;
  status: LeadStatus;
  receivedDays: number;
  note: string;
}

const leads: Lead[] = [
  { id: "l1", name: "Tariq Ahmed", city: "Mississauga", source: "Nextdoor", email: "tariq@example.com", phone: "(905) 555-0182", status: "new", receivedDays: 0, note: "Found us via Nextdoor recommendation thread. Owns a 2014 detached in Erin Mills." },
  { id: "l2", name: "Maya Kowalski", city: "Oakville", source: "Referral", email: "maya.k@example.com", phone: "(905) 555-0144", status: "new", receivedDays: 1, note: "Referred by Mark & Helen Chen. Premier plan likely." },
  { id: "l3", name: "Brendan O'Hara", city: "Milton", source: "Door-knock", email: "bohara@example.com", phone: "(905) 555-0119", status: "contacted", receivedDays: 2, note: "Spoke at door Saturday. Wants to compare Essential vs Complete." },
  { id: "l4", name: "Yuki & David Sato", city: "Oakville", source: "Facebook group", email: "satohome@example.com", phone: "(905) 555-0167", status: "qualified", receivedDays: 3, note: "Replied 'yes' to walk-through DM. Available evenings only." },
  { id: "l5", name: "Anita Persaud", city: "Mississauga", source: "Website", email: "apersaud@example.com", phone: "(905) 555-0192", status: "contacted", receivedDays: 4, note: "Filled the plans page lead form. Interested in Complete." },
  { id: "l6", name: "George Mwangi", city: "Mississauga", source: "Referral", email: "g.mwangi@example.com", phone: "(905) 555-0173", status: "new", receivedDays: 5, note: "Referred by Priya Sharma. Mentioned dryer vent issue." },
  { id: "l7", name: "Patti Lin", city: "Milton", source: "Website", email: "pattilin@example.com", phone: "(905) 555-0136", status: "qualified", receivedDays: 6, note: "Wants annual billing. Asked about Boler insurance discount." },
  { id: "l8", name: "Hank Rowley", city: "Oakville", source: "Nextdoor", email: "hank@example.com", phone: "(905) 555-0150", status: "cold", receivedDays: 12, note: "No reply after 3 follow-ups." },
  { id: "l9", name: "Renata Costa", city: "Mississauga", source: "Door-knock", email: "renata@example.com", phone: "(905) 555-0128", status: "new", receivedDays: 0, note: "Caught on the street walking route. New build in Churchill Meadows." },
];

const TONE: Record<LeadStatus, string> = {
  new: "bg-sky-500/10 text-sky-700 dark:text-sky-300",
  contacted: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
  qualified: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  cold: "bg-muted text-muted-foreground",
};

function LeadsPage() {
  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Leads</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {leads.length} inbound homeowners who haven't yet booked a walk-through.
          </p>
        </div>
        <Button size="sm">Add lead manually</Button>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {(["new", "contacted", "qualified", "cold"] as LeadStatus[]).map((s) => (
          <div key={s} className="rounded-2xl border border-border bg-card p-4">
            <div className="text-xs uppercase tracking-wide text-muted-foreground">{s}</div>
            <div className="mt-1 font-display text-2xl font-extrabold">
              {leads.filter((l) => l.status === s).length}
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-3 lg:grid-cols-2">
        {leads.map((l) => (
          <div key={l.id} className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-medium">{l.name}</h3>
                  <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", TONE[l.status])}>
                    {l.status}
                  </span>
                </div>
                <div className="mt-0.5 text-xs text-muted-foreground">
                  {l.city} · {l.source} · {l.receivedDays === 0 ? "today" : `${l.receivedDays}d ago`}
                </div>
              </div>
              <Button size="sm" variant="ghost">
                Book walk-through <ArrowRight className="ml-1 h-3.5 w-3.5" />
              </Button>
            </div>
            <p className="mt-3 text-sm text-muted-foreground">{l.note}</p>
            <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
              <span className="flex items-center gap-1"><Mail className="h-3.5 w-3.5" /> {l.email}</span>
              <span className="flex items-center gap-1"><Phone className="h-3.5 w-3.5" /> {l.phone}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
