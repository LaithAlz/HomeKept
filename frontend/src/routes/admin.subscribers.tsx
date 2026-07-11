import { useId, useMemo, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Search, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { ApiError } from "@/lib/api";
import {
  useAdminSubscriber,
  useAdminSubscribers,
  useUpdatePropertySku,
  formatCentsCAD,
  type AdminSubscriberPropertySummary,
  type AdminUpdateSkuRequest,
} from "@/lib/admin";
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

            {detail.property?.propertyId && (
              <PropertySkuForm key={detail.property.propertyId} property={detail.property} />
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

type FlushEligibility = "unknown" | "yes" | "no";

function toFlushEligibility(value: boolean | null): FlushEligibility {
  if (value === null) return "unknown";
  return value ? "yes" : "no";
}

/**
 * Surfaces the backend's error honestly rather than a generic fallback:
 * `VALIDATION_FAILED` carries per-field messages (only `waterHeaterAgeYears` can
 * fail validation here — see `AdminUpdateSkuRequest.java`), and `PropertyNotFoundException`
 * maps to a plain "Property not found" 404 message that's safe to show verbatim
 * (same "pre-canned backend message" pattern as `RescheduleDialog`).
 */
function describeSkuError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 400 && err.fields && Object.keys(err.fields).length > 0) {
      return Object.values(err.fields).join(" ");
    }
    return err.message;
  }
  return "That didn't go through. Please try again.";
}

/**
 * The property SKU sheet (#56): technician-prep data captured on the walk-through
 * and refined over subsequent visits (docs/pricing-and-visits.md §Materials). Keyed
 * by `property.propertyId` from the parent so switching between subscribers in the
 * sheet remounts this form with fresh local state instead of carrying over stale
 * edits from a previously viewed property.
 */
function PropertySkuForm({ property }: { property: AdminSubscriberPropertySummary }) {
  const [hvacFilterSizes, setHvacFilterSizes] = useState(property.hvacFilterSizes ?? "");
  const [smokeCoDetectorModels, setSmokeCoDetectorModels] = useState(
    property.smokeCoDetectorModels ?? "",
  );
  const [humidifierModel, setHumidifierModel] = useState(property.humidifierModel ?? "");
  const [waterHeaterAgeYears, setWaterHeaterAgeYears] = useState(
    property.waterHeaterAgeYears !== null ? String(property.waterHeaterAgeYears) : "",
  );
  const [flushEligible, setFlushEligible] = useState<FlushEligibility>(
    toFlushEligibility(property.waterHeaterFlushEligible),
  );
  const [error, setError] = useState<string | null>(null);

  const mutation = useUpdatePropertySku(property.propertyId);
  const baseId = useId();
  const errorId = `${baseId}-sku-error`;

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const trimmedAge = waterHeaterAgeYears.trim();
    let ageYears: number | null = null;
    if (trimmedAge !== "") {
      ageYears = Number(trimmedAge);
      if (!Number.isInteger(ageYears) || ageYears < 0 || ageYears > 100) {
        setError("Water heater age must be a whole number of years, between 0 and 100.");
        return;
      }
    }

    const request: AdminUpdateSkuRequest = {
      hvacFilterSizes: hvacFilterSizes.trim() === "" ? null : hvacFilterSizes.trim(),
      smokeCoDetectorModels:
        smokeCoDetectorModels.trim() === "" ? null : smokeCoDetectorModels.trim(),
      humidifierModel: humidifierModel.trim() === "" ? null : humidifierModel.trim(),
      waterHeaterAgeYears: ageYears,
      waterHeaterFlushEligible: flushEligible === "unknown" ? null : flushEligible === "yes",
    };

    mutation.mutate(request, {
      onSuccess: () => {
        toast.success("SKU sheet saved");
      },
      onError: (err) => setError(describeSkuError(err)),
    });
  }

  return (
    <div className="rounded-xl border border-border p-3">
      <h3 className="font-display text-sm font-bold">SKU sheet</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">
        Technician prep captured on the walk-through and refined over later visits.
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-3 space-y-3">
        <fieldset disabled={mutation.isPending} className="space-y-3">
          <legend className="sr-only">SKU sheet fields</legend>

          <div>
            <Label htmlFor={`${baseId}-hvac`}>HVAC filter sizes</Label>
            <Input
              id={`${baseId}-hvac`}
              value={hvacFilterSizes}
              onChange={(e) => setHvacFilterSizes(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!hvacFilterSizes && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div>
            <Label htmlFor={`${baseId}-detectors`}>Smoke/CO detector models</Label>
            <Input
              id={`${baseId}-detectors`}
              value={smokeCoDetectorModels}
              onChange={(e) => setSmokeCoDetectorModels(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!smokeCoDetectorModels && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div>
            <Label htmlFor={`${baseId}-humidifier`}>Humidifier model</Label>
            <Input
              id={`${baseId}-humidifier`}
              value={humidifierModel}
              onChange={(e) => setHumidifierModel(e.target.value)}
              className="mt-1"
              aria-describedby={error ? errorId : undefined}
            />
            {!humidifierModel && (
              <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor={`${baseId}-wh-age`}>Water heater age (years)</Label>
              <Input
                id={`${baseId}-wh-age`}
                type="number"
                inputMode="numeric"
                min={0}
                max={100}
                step={1}
                value={waterHeaterAgeYears}
                onChange={(e) => setWaterHeaterAgeYears(e.target.value)}
                className="mt-1"
                aria-describedby={error ? errorId : undefined}
              />
              {!waterHeaterAgeYears && (
                <p className="mt-1 text-xs text-muted-foreground">Not captured yet.</p>
              )}
            </div>

            <div>
              <Label htmlFor={`${baseId}-wh-flush`}>Water heater flush eligible</Label>
              <Select
                value={flushEligible}
                onValueChange={(v) => setFlushEligible(v as FlushEligibility)}
                disabled={mutation.isPending}
              >
                <SelectTrigger id={`${baseId}-wh-flush`} className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="unknown">Not captured yet</SelectItem>
                  <SelectItem value="yes">Yes</SelectItem>
                  <SelectItem value="no">No</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </fieldset>

        {error && (
          <p id={errorId} role="alert" className="text-xs text-destructive">
            {error}
          </p>
        )}

        <div className="flex justify-end">
          <Button
            type="submit"
            size="sm"
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
          >
            {mutation.isPending && <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />}
            Save SKU sheet
          </Button>
        </div>
      </form>
    </div>
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
