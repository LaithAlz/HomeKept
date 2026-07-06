import { createFileRoute } from "@tanstack/react-router";
import { Inbox } from "lucide-react";

export const Route = createFileRoute("/admin/leads")({
  head: () => ({
    meta: [{ title: "Leads — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: LeadsPage,
});

/**
 * There is no lead-capture backend yet (no endpoint, no table) — see
 * `backend/api-contract.md`. Walk-through bookings already have their own
 * pipeline at `/admin/walkthroughs`, backed by `GET /api/admin/bookings`.
 * This page is an honest placeholder until inbound lead capture (a form or
 * channel upstream of a booking) is actually built, rather than showing
 * invented contacts.
 */
function LeadsPage() {
  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Leads</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Inbound homeowners interested in HomeKept before they book a walk-through.
      </p>

      <div className="mt-6 flex flex-col items-center rounded-2xl border border-dashed border-border bg-card px-6 py-16 text-center">
        <Inbox className="size-8 text-muted-foreground" aria-hidden="true" />
        <h2 className="mt-4 font-display text-lg font-bold">No lead capture yet</h2>
        <p className="mt-2 max-w-sm text-sm text-muted-foreground">
          This page will list inbound leads once lead capture is built. Until then, walk-through
          bookings are tracked on the Walk-throughs page.
        </p>
      </div>
    </div>
  );
}
