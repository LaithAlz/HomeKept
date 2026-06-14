import { createFileRoute } from "@tanstack/react-router";
import { CreditCard, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { subscriber } from "@/lib/mock-account";

export const Route = createFileRoute("/app/billing")({
  head: () => ({
    meta: [{ title: "Billing — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: BillingPage,
});

const planPrice: Record<string, number> = {
  Essential: 129,
  Complete: 189,
  Premier: 289,
};

const invoices = [
  { id: "in_001", date: "2026-05-01", amount: 189, status: "Paid" },
  { id: "in_002", date: "2026-04-01", amount: 189, status: "Paid" },
  { id: "in_003", date: "2026-03-01", amount: 189, status: "Paid" },
  { id: "in_004", date: "2026-02-01", amount: 189, status: "Paid" },
  { id: "in_005", date: "2026-01-01", amount: 189, status: "Paid" },
  { id: "in_006", date: "2025-12-01", amount: 189, status: "Paid" },
];

function fmt(n: number) {
  return new Intl.NumberFormat("en-CA", { style: "currency", currency: "CAD" }).format(n);
}

function BillingPage() {
  const price = planPrice[subscriber.planName];
  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Billing</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Your plan, payment method, and invoice history.
      </p>

      <section className="mt-8 grid gap-4 lg:grid-cols-3">
        <div className="rounded-3xl border border-border bg-card p-6 lg:col-span-2">
          <div className="text-xs uppercase tracking-wide text-muted-foreground">Current plan</div>
          <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
            <div>
              <div className="font-display text-3xl font-extrabold">{subscriber.planName}</div>
              <div className="mt-1 text-sm text-muted-foreground">
                Billed monthly · renews June 1
              </div>
            </div>
            <div className="text-right">
              <div className="font-display text-3xl font-extrabold tabular-nums">{fmt(price)}</div>
              <div className="text-xs text-muted-foreground">per month</div>
            </div>
          </div>
          <div className="mt-5 flex gap-2">
            <Button size="sm" variant="outline">
              Switch to annual (save {fmt(price * 12 * 0.15)})
            </Button>
            <Button size="sm" variant="ghost">
              Change plan
            </Button>
          </div>
        </div>

        <div className="rounded-3xl border border-border bg-card p-6">
          <div className="text-xs uppercase tracking-wide text-muted-foreground">
            Payment method
          </div>
          <div className="mt-3 flex items-center gap-3">
            <div className="flex h-10 w-14 items-center justify-center rounded-md bg-foreground text-background">
              <CreditCard className="h-5 w-5" />
            </div>
            <div className="text-sm">
              <div className="font-medium">Visa •• 4321</div>
              <div className="text-xs text-muted-foreground">Expires 09 / 28</div>
            </div>
          </div>
          <Button size="sm" variant="outline" className="mt-4 w-full">
            Update card
          </Button>
        </div>
      </section>

      <section className="mt-10">
        <h2 className="text-xs uppercase tracking-wide text-muted-foreground">Invoice history</h2>
        <div className="mt-3 overflow-x-auto rounded-2xl border border-border bg-card">
          <table className="w-full min-w-[480px] text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Date</th>
                <th className="px-4 py-3">Invoice</th>
                <th className="px-4 py-3 text-right">Amount</th>
                <th className="px-4 py-3">Status</th>
                <th className="w-10"></th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((i) => (
                <tr key={i.id} className="border-t border-border">
                  <td className="px-4 py-3">
                    {new Date(i.date).toLocaleDateString("en-CA", {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                    })}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{i.id}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{fmt(i.amount)}</td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs text-emerald-700 dark:text-emerald-300">
                      {i.status}
                    </span>
                  </td>
                  <td className="px-2 py-1.5">
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-11 w-11"
                      aria-label="Download invoice"
                    >
                      <Download className="h-3.5 w-3.5" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
