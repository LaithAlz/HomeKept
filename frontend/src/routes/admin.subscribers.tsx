import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Search, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { formatDateShort } from "@/lib/format";
import { useAdminSubscriber, useAdminSubscribers, formatCentsCAD } from "@/lib/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/subscribers")({
  head: () => ({
    meta: [{ title: "Subscribers — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: SubscribersPage,
});

const STATUS_LABEL: Record<string, string> = {
  PENDING_ACTIVATION: "Pending activation",
  ACTIVE: "Active",
  PAUSED: "Paused",
  PAYMENT_ISSUE: "Payment issue",
  CANCELLED: "Cancelled",
};

const STATUS_TONE: Record<string, string> = {
  PENDING_ACTIVATION: "bg-sky-500/10 text-sky-700",
  ACTIVE: "bg-emerald-500/10 text-emerald-700",
  PAUSED: "bg-muted text-muted-foreground",
  PAYMENT_ISSUE: "bg-rose-500/10 text-rose-700",
  CANCELLED: "bg-muted text-muted-foreground",
};

const PLAN_LABEL: Record<string, string> = {
  ESSENTIAL: "Essential",
  COMPLETE: "Complete",
  PREMIER: "Premier",
};

function SubscribersPage() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 100 });
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("all");
  const [plan, setPlan] = useState<string>("all");
  const [openId, setOpenId] = useState<number | null>(null);

  const rows = useMemo(() => {
    if (!subscribers) return [];
    return subscribers.filter((s) => {
      if (status !== "all" && s.status !== status) return false;
      if (plan !== "all" && s.planCode !== plan) return false;
      if (q && !String(s.id).includes(q.trim())) return false;
      return true;
    });
  }, [subscribers, q, status, plan]);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Subscribers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {subscribers
              ? `${rows.length} of ${subscribers.length} households`
              : "Loading households…"}
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-56">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          />
          <label htmlFor="subscriber-search" className="sr-only">
            Search by subscriber ID
          </label>
          <Input
            id="subscriber-search"
            placeholder="Search by ID"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-44" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {Object.entries(STATUS_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={plan} onValueChange={setPlan}>
          <SelectTrigger className="w-40" aria-label="Filter by plan">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All plans</SelectItem>
            {Object.entries(PLAN_LABEL).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Loading subscribers.
        </div>
      )}

      {isError && !isLoading && (
        <div
          role="alert"
          className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>We couldn't load subscribers.</span>
          <Button size="sm" variant="outline" onClick={() => void refetch()}>
            Try again
          </Button>
        </div>
      )}

      {subscribers && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-2 py-3">Plan</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3 text-right">MRR</th>
                <th className="px-2 py-3">Founding rate</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id} className="border-t border-border hover:bg-muted/30">
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setOpenId(s.id)}
                      className="font-medium text-foreground hover:underline"
                    >
                      #{s.id}
                    </button>
                  </td>
                  <td className="px-2 py-3">
                    {s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "—"}
                  </td>
                  <td className="px-2 py-3">
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-xs font-medium",
                        STATUS_TONE[s.status] ?? "bg-muted text-muted-foreground",
                      )}
                    >
                      {STATUS_LABEL[s.status] ?? s.status}
                    </span>
                  </td>
                  <td className="px-2 py-3 text-right tabular-nums">
                    {formatCentsCAD(s.mrrCents)}
                  </td>
                  <td className="px-2 py-3 text-muted-foreground">
                    {s.foundingRate ? "Yes" : "—"}
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No subscribers match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <SubscriberDetailSheet id={openId} onOpenChange={(open) => !open && setOpenId(null)} />
    </div>
  );
}

function SubscriberDetailSheet({
  id,
  onOpenChange,
}: {
  id: number | null;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: detail, isLoading, isError, refetch } = useAdminSubscriber(id);

  return (
    <Sheet open={id !== null} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-md">
        {/* SheetTitle is always rendered (Radix requires an accessible name for the
            dialog) — it just doesn't have a subscriber id to show until `detail` loads. */}
        <SheetHeader>
          <SheetTitle>{detail ? `Subscriber #${detail.id}` : "Subscriber detail"}</SheetTitle>
          <SheetDescription>
            {detail
              ? detail.property
                ? `${detail.property.streetAddress}, ${detail.property.city}`
                : "No property linked yet."
              : "Loading subscriber detail."}
          </SheetDescription>
        </SheetHeader>

        {isLoading && (
          <div
            role="status"
            aria-live="polite"
            className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading subscriber.
          </div>
        )}

        {isError && !isLoading && (
          <div
            role="alert"
            className="mt-6 flex flex-col items-start gap-2 text-sm text-destructive"
          >
            <span>We couldn't load this subscriber.</span>
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              Try again
            </Button>
          </div>
        )}

        {detail && (
          <div className="mt-6 space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-3">
              <DetailTile label="Plan">
                {detail.planCode
                  ? (PLAN_LABEL[detail.planCode] ?? detail.planCode)
                  : "Not chosen yet"}
              </DetailTile>
              <DetailTile label="MRR">{formatCentsCAD(detail.mrrCents)}</DetailTile>
              <DetailTile label="Status">{STATUS_LABEL[detail.status] ?? detail.status}</DetailTile>
              <DetailTile label="Billing cycle">
                {detail.billingCycle === "ANNUAL" ? "Annual" : "Monthly"}
              </DetailTile>
              <DetailTile label="Founding rate">{detail.foundingRate ? "Yes" : "No"}</DetailTile>
              <DetailTile label="Started">
                {detail.startedAt ? formatDateShort(detail.startedAt) : "—"}
              </DetailTile>
              {detail.pausedAt && (
                <DetailTile label="Paused">{formatDateShort(detail.pausedAt)}</DetailTile>
              )}
              {detail.cancelledAt && (
                <DetailTile label="Cancelled">{formatDateShort(detail.cancelledAt)}</DetailTile>
              )}
              {detail.currentPeriodEnd && (
                <DetailTile label="Current period ends">
                  {formatDateShort(detail.currentPeriodEnd)}
                </DetailTile>
              )}
            </div>

            {detail.property && (
              <div className="rounded-xl border border-border p-3">
                <div className="text-xs text-muted-foreground">Property</div>
                <div className="font-medium">
                  {detail.property.streetAddress}, {detail.property.city}{" "}
                  {detail.property.postalCode}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  {detail.property.propertyType ?? "Type not set"} ·{" "}
                  {detail.property.hasAccessNotes
                    ? "Access notes on file"
                    : "No access notes on file"}
                </div>
              </div>
            )}

            <div className="rounded-xl border border-dashed border-border p-3 text-xs text-muted-foreground">
              Visit history isn't available yet.
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

function DetailTile({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-border p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="font-medium">{children}</div>
    </div>
  );
}
