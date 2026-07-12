import { useEffect, useMemo, useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { toast } from "sonner";
import { Download, Plus, AlertTriangle, CreditCard, CalendarClock, Loader2, X } from "lucide-react";
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
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api";
import { formatDateShort, formatDateTime, formatTime, formatTodayLong } from "@/lib/format";
import type { WalkthroughBookingRequest } from "@/lib/booking";
import {
  useAdminDashboard,
  useAdminSubscribers,
  useAdminBookings,
  useAdminRescheduleRequests,
  useCreateWalkthroughBooking,
  formatCentsCAD,
} from "@/lib/admin";

export const Route = createFileRoute("/admin/")({
  head: () => ({
    meta: [{ title: "Dashboard — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: AdminDashboard,
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

/**
 * `preferredWeek` is a LocalDate ("YYYY-MM-DD") with no time-of-day meaning.
 * Anchoring it to UTC noon before formatting with an explicit UTC timeZone
 * avoids an off-by-one day depending on the viewer's local timezone. Mirrors
 * the identical helper in `admin.walkthroughs.tsx`.
 */
function formatWeekOf(dateStr: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
  }).format(new Date(`${dateStr}T12:00:00Z`));
}

function AdminDashboard() {
  const [newBookingOpen, setNewBookingOpen] = useState(false);
  const {
    data: dashboard,
    dataUpdatedAt,
    isLoading: dashboardLoading,
    isError: dashboardError,
    refetch: refetchDashboard,
  } = useAdminDashboard();

  return (
    <>
      {/* Top bar */}
      <div className="sticky top-0 z-20 border-b border-border bg-card/95 backdrop-blur">
        <div className="flex flex-col gap-3 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs text-muted-foreground">
              {formatTodayLong()}
              {/* Derived from the real dashboard query state, not an unverified
                  system-health claim: last-updated time on success, an honest
                  error note on failure, nothing while the first load is in flight. */}
              {dashboard && !dashboardError && (
                <>
                  {" "}
                  ·{" "}
                  <span className="font-semibold text-foreground">
                    Updated {formatTime(new Date(dataUpdatedAt).toISOString())}
                  </span>
                </>
              )}
              {dashboardError && (
                <>
                  {" "}
                  ·{" "}
                  <span className="font-semibold text-destructive">Dashboard data unavailable</span>
                </>
              )}
            </p>
            <h1 className="mt-0.5 font-display text-2xl font-extrabold tracking-tight">
              Dashboard
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm">
              <Download className="size-4" />
              Export
            </Button>
            <Button size="sm" onClick={() => setNewBookingOpen(true)}>
              <Plus className="size-4" />
              New booking
            </Button>
          </div>
        </div>
      </div>

      <div className="px-6 py-6 space-y-6">
        {/* Metric strip */}
        {dashboardLoading && (
          <div
            role="status"
            aria-live="polite"
            className="flex items-center gap-2 text-sm text-muted-foreground"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading dashboard metrics.
          </div>
        )}

        {dashboardError && !dashboardLoading && (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
          >
            <span>We couldn't load dashboard metrics.</span>
            <Button size="sm" variant="outline" onClick={() => void refetchDashboard()}>
              Try again
            </Button>
          </div>
        )}

        <section
          aria-label="Key metrics"
          className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
        >
          <MetricCard label="MRR" value={dashboard ? formatCentsCAD(dashboard.mrrCents) : "—"} />
          <MetricCard
            label="Active subscribers"
            value={dashboard ? String(dashboard.activeSubscribers) : "—"}
          />
          <MetricCard
            label="Pending walk-throughs"
            value={dashboard ? String(dashboard.pendingWalkthroughs) : "—"}
          />
          <MetricCard
            label="Upcoming visits"
            value={dashboard ? String(dashboard.upcomingVisits) : "—"}
          />
          <MetricCard
            label="Founding rate slots remaining"
            value={dashboard ? String(dashboard.foundingRateSlotsRemaining) : "—"}
          />
        </section>

        {/* Recent subscribers */}
        <RecentSubscribersPanel />

        {/* Two-column section */}
        <section className="grid gap-6 xl:grid-cols-2">
          <PendingWalkthroughsPanel />
          <NeedsAttentionPanel />
        </section>
      </div>

      <NewBookingSheet open={newBookingOpen} onOpenChange={setNewBookingOpen} />
    </>
  );
}

// ---------------------------------------------------------------------------
// Metric cards
// ---------------------------------------------------------------------------

function MetricCard({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: string;
  sub?: React.ReactNode;
  tone?: "warn";
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border bg-card p-4 shadow-sm",
        tone === "warn" ? "border-destructive/30" : "border-border",
      )}
    >
      <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 font-display text-3xl font-extrabold tracking-tight">{value}</p>
      {sub && <p className="mt-2 text-xs text-muted-foreground">{sub}</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Panel loading / error helpers (shared shape across the three panels below)
// ---------------------------------------------------------------------------

function PanelLoading({ label }: { label: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-2 p-4 text-sm text-muted-foreground"
    >
      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
      {label}
    </div>
  );
}

function PanelError({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm text-destructive"
    >
      <span>{label}</span>
      <Button size="sm" variant="outline" onClick={onRetry}>
        Try again
      </Button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Recent subscribers
// ---------------------------------------------------------------------------

function RecentSubscribersPanel() {
  const { data: subscribers, isLoading, isError, refetch } = useAdminSubscribers({ limit: 6 });

  return (
    <section
      aria-labelledby="subs-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2 id="subs-h" className="font-display text-lg font-bold tracking-tight">
            Recent subscribers
          </h2>
          <p className="text-xs text-muted-foreground">
            {subscribers ? `${subscribers.length} most recent` : "Loading subscribers."}
          </p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/admin/subscribers">See all</Link>
        </Button>
      </header>

      {isLoading && <PanelLoading label="Loading subscribers." />}
      {isError && !isLoading && (
        <PanelError label="We couldn't load subscribers." onRetry={() => void refetch()} />
      )}

      {subscribers && subscribers.length === 0 && (
        <p className="p-6 text-sm text-muted-foreground">No subscribers yet.</p>
      )}

      {subscribers && subscribers.length > 0 && (
        <ul className="divide-y divide-border">
          {subscribers.map((s) => (
            <li key={s.id} className="flex items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                <Link
                  to="/admin/subscribers"
                  className="font-semibold text-foreground hover:underline"
                >
                  Subscriber #{s.id}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {s.planCode ? (PLAN_LABEL[s.planCode] ?? s.planCode) : "No plan yet"}
                  {s.foundingRate ? " · Founding rate" : ""}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-xs font-medium",
                    STATUS_TONE[s.status] ?? "bg-muted text-muted-foreground",
                  )}
                >
                  {STATUS_LABEL[s.status] ?? s.status}
                </span>
                <span className="text-sm font-semibold tabular-nums">
                  {formatCentsCAD(s.mrrCents)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Pending walk-throughs
// ---------------------------------------------------------------------------

function PendingWalkthroughsPanel() {
  const {
    data: bookings,
    isLoading,
    isError,
    refetch,
  } = useAdminBookings({ status: "PENDING", limit: 6 });

  return (
    <article
      aria-labelledby="walk-h"
      className="rounded-2xl border border-border bg-card shadow-sm"
    >
      <header className="flex items-center justify-between border-b border-border p-4">
        <div>
          <h2 id="walk-h" className="font-display text-lg font-bold tracking-tight">
            Pending walk-throughs
          </h2>
          <p className="text-xs text-muted-foreground">
            {bookings ? `${bookings.length} awaiting confirmation` : "Loading walk-throughs."}
          </p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/admin/walkthroughs">See all</Link>
        </Button>
      </header>

      {isLoading && <PanelLoading label="Loading walk-throughs." />}
      {isError && !isLoading && (
        <PanelError
          label="We couldn't load the walk-through pipeline."
          onRetry={() => void refetch()}
        />
      )}

      {bookings && bookings.length === 0 && (
        <p className="p-6 text-sm text-muted-foreground">
          No walk-throughs are awaiting confirmation.
        </p>
      )}

      {bookings && bookings.length > 0 && (
        <ul className="divide-y divide-border">
          {bookings.map((b) => (
            <li key={b.id} className="flex items-start gap-3 p-4">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-border bg-surface text-muted-foreground">
                <CalendarClock className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-3">
                  <p className="truncate font-semibold text-foreground">{b.fullName}</p>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {b.scheduledFor
                      ? formatDateTime(b.scheduledFor)
                      : `Week of ${formatWeekOf(b.preferredWeek)}`}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  {b.city} ·{" "}
                  <span className="rounded-md bg-surface px-1.5 py-0.5 font-semibold text-foreground/80">
                    {b.leadSource}
                  </span>
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

// ---------------------------------------------------------------------------
// Needs attention — derived from two real signals: subscribers whose status
// is PAYMENT_ISSUE, and PENDING customer reschedule requests. No other field
// exposed by the admin endpoints maps cleanly to "needs attention" without
// inventing content, so those are the only two categories shown here.
// ---------------------------------------------------------------------------

function NeedsAttentionPanel() {
  const {
    data: subscribers,
    isLoading: subsLoading,
    isError: subsError,
    refetch: refetchSubs,
  } = useAdminSubscribers({ limit: 100 });
  const {
    data: rescheduleRequests,
    isLoading: rrLoading,
    isError: rrError,
    refetch: refetchRR,
  } = useAdminRescheduleRequests();

  const isLoading = subsLoading || rrLoading;
  const isError = subsError || rrError;
  const paymentIssues = (subscribers ?? []).filter((s) => s.status === "PAYMENT_ISSUE");
  const pendingReschedules = rescheduleRequests ?? [];
  const totalOpen = paymentIssues.length + pendingReschedules.length;

  return (
    <article aria-labelledby="att-h" className="rounded-2xl border border-border bg-card shadow-sm">
      <header className="flex items-center justify-between border-b border-border p-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="size-4 text-destructive" aria-hidden="true" />
          <h2 id="att-h" className="font-display text-lg font-bold tracking-tight">
            Needs attention
          </h2>
        </div>
        {!isLoading && !isError && (
          <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-[11px] font-bold text-destructive">
            {totalOpen} open
          </span>
        )}
      </header>

      {isLoading && <PanelLoading label="Loading." />}
      {isError && !isLoading && (
        <PanelError
          label="We couldn't load these signals."
          onRetry={() => {
            void refetchSubs();
            void refetchRR();
          }}
        />
      )}

      {!isLoading && !isError && totalOpen === 0 && (
        <p className="p-6 text-sm text-muted-foreground">Nothing needs attention right now.</p>
      )}

      {!isLoading && !isError && totalOpen > 0 && (
        <ul className="divide-y divide-border">
          {paymentIssues.map((s) => (
            <li key={`sub-${s.id}`} className="flex items-start gap-3 p-4">
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-destructive/15 text-destructive">
                <CreditCard className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-foreground">Subscriber #{s.id}: payment issue</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {s.planCode ? `${PLAN_LABEL[s.planCode] ?? s.planCode} plan` : "No plan on file"}{" "}
                  · {formatCentsCAD(s.mrrCents)} MRR
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0" asChild>
                <Link to="/admin/subscribers">View</Link>
              </Button>
            </li>
          ))}
          {pendingReschedules.map((r) => (
            <li key={`rr-${r.id}`} className="flex items-start gap-3 p-4">
              <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <CalendarClock className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-foreground">
                  Reschedule request: visit #{r.visitId}
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Subscriber #{r.subscriberId} · Requested {formatDateShort(r.createdAt)}
                </p>
              </div>
              <Button size="sm" variant="outline" className="shrink-0" asChild>
                <Link to="/admin/visits">View</Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

// ---------------------------------------------------------------------------
// New booking sheet — logs a walk-through someone booked by phone or in
// person, through the same public endpoint the customer wizard uses
// (POST /api/bookings/walkthrough, see @/lib/booking). There is no plan or
// billing step here: a walk-through booking has neither, a plan is only
// chosen later at activation. Submitting creates a real PENDING walk-through
// booking that lands in the same pipeline as a self-serve request.
// ---------------------------------------------------------------------------

const CITY_OPTIONS = ["Oakville", "Mississauga", "Milton", "Other"] as const;

const PROPERTY_TYPE_OPTIONS: {
  value: WalkthroughBookingRequest["propertyType"];
  label: string;
}[] = [
  { value: "DETACHED", label: "Detached" },
  { value: "SEMI", label: "Semi" },
  { value: "TOWNHOUSE", label: "Townhouse" },
];

const TIME_OF_DAY_OPTIONS: { value: WalkthroughBookingRequest["timeOfDay"]; label: string }[] = [
  { value: "MORNING", label: "Morning (8 – 11 AM)" },
  { value: "AFTERNOON", label: "Afternoon (12 – 4 PM)" },
  { value: "EVENING", label: "Evening (5 – 7 PM)" },
];

const SQFT_OPTIONS: {
  value: NonNullable<WalkthroughBookingRequest["squareFootageRange"]>;
  label: string;
}[] = [
  { value: "<1500", label: "< 1,500 sq ft" },
  { value: "1500-2500", label: "1,500 – 2,500 sq ft" },
  { value: "2500-4000", label: "2,500 – 4,000 sq ft" },
  { value: ">4000", label: "4,000+ sq ft" },
];

/**
 * Every input on this form maps 1:1 onto a `WalkthroughBookingRequest` key,
 * so a backend `VALIDATION_FAILED` field name can be shown inline directly
 * with no separate mapping table. Fields with no dedicated input (e.g.
 * `contactConsent`, always sent `true`) fall back to the general form error.
 */
const BOOKABLE_FIELDS = new Set([
  "fullName",
  "email",
  "phone",
  "streetAddress",
  "city",
  "postalCode",
  "propertyType",
  "preferredWeek",
  "timeOfDay",
  "squareFootageRange",
  "notes",
]);

function getNextMonday(): Date {
  const d = new Date();
  const day = d.getDay(); // 0 = Sunday
  const daysUntilMon = day === 0 ? 1 : 8 - day;
  d.setDate(d.getDate() + daysUntilMon);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Same 4-week picking window offered by the customer booking wizard (`routes/book.tsx`). */
function getWeekOptions(count = 4): { iso: string; label: string }[] {
  const monday = getNextMonday();
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(monday);
    d.setDate(d.getDate() + i * 7);
    const iso = d.toISOString().slice(0, 10);
    return { iso, label: `Week of ${formatWeekOf(iso)}` };
  });
}

interface NewBookingFormData {
  fullName: string;
  email: string;
  phone: string;
  streetAddress: string;
  city: string;
  postalCode: string;
  propertyType: WalkthroughBookingRequest["propertyType"] | "";
  preferredWeek: string;
  timeOfDay: WalkthroughBookingRequest["timeOfDay"] | "";
  squareFootageRange: WalkthroughBookingRequest["squareFootageRange"] | "";
  notes: string;
}

const EMPTY_BOOKING_FORM: NewBookingFormData = {
  fullName: "",
  email: "",
  phone: "",
  streetAddress: "",
  city: "",
  postalCode: "",
  propertyType: "",
  preferredWeek: "",
  timeOfDay: "",
  squareFootageRange: "",
  notes: "",
};

const bookingEmailRe = /^\S+@\S+\.\S+$/;

type BookingFieldErrors = Partial<Record<string, string>>;

function validateBookingForm(f: NewBookingFormData): BookingFieldErrors {
  const errs: BookingFieldErrors = {};
  if (f.fullName.trim().length < 2) errs.fullName = "Enter the homeowner's name";
  if (!bookingEmailRe.test(f.email.trim())) errs.email = "Enter a valid email address";
  if (!f.phone.trim()) errs.phone = "Enter a phone number";
  if (!f.streetAddress.trim()) errs.streetAddress = "Enter a street address";
  if (!f.city) errs.city = "Pick a city";
  if (!f.postalCode.trim()) errs.postalCode = "Enter a postal code";
  if (!f.propertyType) errs.propertyType = "Pick a property type";
  if (!f.preferredWeek) errs.preferredWeek = "Pick a week";
  if (!f.timeOfDay) errs.timeOfDay = "Pick a time of day";
  return errs;
}

function NewBookingSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const [data, setData] = useState<NewBookingFormData>(EMPTY_BOOKING_FORM);
  const [errors, setErrors] = useState<BookingFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const mutation = useCreateWalkthroughBooking();
  const weeks = useMemo(() => getWeekOptions(4), []);

  // Reset to a blank form every time the sheet closes, however it closed
  // (Create, Cancel, Escape, overlay click) — a stale draft reappearing on
  // reopen would be confusing for a form this short-lived.
  useEffect(() => {
    if (!open) {
      setData(EMPTY_BOOKING_FORM);
      setErrors({});
      setFormError(null);
    }
  }, [open]);

  function patch(updates: Partial<NewBookingFormData>) {
    setData((d) => ({ ...d, ...updates }));
    const keys = Object.keys(updates);
    setErrors((prev) => {
      if (!keys.some((k) => prev[k])) return prev;
      const next = { ...prev };
      for (const k of keys) delete next[k];
      return next;
    });
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const validationErrors = validateBookingForm(data);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    setFormError(null);

    const payload: WalkthroughBookingRequest = {
      fullName: data.fullName.trim(),
      email: data.email.trim(),
      phone: data.phone.trim(),
      streetAddress: data.streetAddress.trim(),
      city: data.city,
      postalCode: data.postalCode.trim().toUpperCase(),
      propertyType: data.propertyType as WalkthroughBookingRequest["propertyType"],
      preferredWeek: data.preferredWeek,
      timeOfDay: data.timeOfDay as WalkthroughBookingRequest["timeOfDay"],
      notes: data.notes.trim() || undefined,
      squareFootageRange: data.squareFootageRange || undefined,
      // No tracked marketing channel applies to a staff-entered booking —
      // this is deliberately the catch-all enum value, not a fabricated one.
      leadSource: "OTHER",
      // Consent was given verbally on the call this booking is logging.
      contactConsent: true,
    };

    mutation.mutate(payload, {
      onSuccess: () => {
        toast.success("Walk-through booked");
        onOpenChange(false);
      },
      onError: (err) => {
        if (err instanceof ApiError && err.status === 429) {
          setFormError(
            "This device has hit the walk-through booking limit (3 per hour). Try again shortly.",
          );
          return;
        }
        if (err instanceof ApiError && err.status === 400 && err.fields) {
          const mapped: BookingFieldErrors = {};
          const unmapped: string[] = [];
          for (const [field, message] of Object.entries(err.fields)) {
            if (BOOKABLE_FIELDS.has(field)) mapped[field] = message;
            else unmapped.push(message);
          }
          setErrors(mapped);
          setFormError(unmapped.length > 0 ? unmapped.join(" ") : null);
          return;
        }
        if (err instanceof ApiError) {
          setFormError(err.message);
          return;
        }
        setFormError("That didn't go through. Please try again.");
      },
    });
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <div className="flex items-center justify-between">
            <SheetTitle className="font-display text-2xl font-extrabold tracking-tight">
              New booking
            </SheetTitle>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
              aria-label="Close"
              className="inline-flex size-8 items-center justify-center rounded-full hover:bg-surface disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="size-4" />
            </button>
          </div>
          <SheetDescription>
            Log a walk-through booked by phone or in person. It&rsquo;s created exactly like a
            self-serve request and enters the same pipeline.
          </SheetDescription>
        </SheetHeader>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit} noValidate>
          <fieldset
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
            className="space-y-4"
          >
            <legend className="sr-only">Walk-through booking details</legend>

            <Field label="Homeowner name" error={errors.fullName}>
              <Input
                placeholder="Jane Doe"
                value={data.fullName}
                onChange={(e) => patch({ fullName: e.target.value })}
                aria-invalid={!!errors.fullName}
              />
            </Field>
            <Field label="Email" error={errors.email}>
              <Input
                type="email"
                placeholder="jane@example.com"
                value={data.email}
                onChange={(e) => patch({ email: e.target.value })}
                aria-invalid={!!errors.email}
              />
            </Field>
            <Field label="Phone" error={errors.phone}>
              <Input
                type="tel"
                placeholder="(905) 555-0123"
                value={data.phone}
                onChange={(e) => patch({ phone: e.target.value })}
                aria-invalid={!!errors.phone}
              />
            </Field>
            <Field label="Street address" error={errors.streetAddress}>
              <Input
                placeholder="123 Example Rd"
                value={data.streetAddress}
                onChange={(e) => patch({ streetAddress: e.target.value })}
                aria-invalid={!!errors.streetAddress}
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="City" error={errors.city}>
                <Select value={data.city} onValueChange={(v) => patch({ city: v })}>
                  <SelectTrigger aria-invalid={!!errors.city}>
                    <SelectValue placeholder="Choose…" />
                  </SelectTrigger>
                  <SelectContent>
                    {CITY_OPTIONS.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Postal code" error={errors.postalCode}>
                <Input
                  placeholder="L5L 0A0"
                  value={data.postalCode}
                  onChange={(e) => patch({ postalCode: e.target.value.toUpperCase() })}
                  aria-invalid={!!errors.postalCode}
                />
              </Field>
            </div>
            <Field label="Property type" error={errors.propertyType}>
              <Select
                value={data.propertyType}
                onValueChange={(v) =>
                  patch({ propertyType: v as WalkthroughBookingRequest["propertyType"] })
                }
              >
                <SelectTrigger aria-invalid={!!errors.propertyType}>
                  <SelectValue placeholder="Choose…" />
                </SelectTrigger>
                <SelectContent>
                  {PROPERTY_TYPE_OPTIONS.map((p) => (
                    <SelectItem key={p.value} value={p.value}>
                      {p.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Preferred week" error={errors.preferredWeek}>
                <Select
                  value={data.preferredWeek}
                  onValueChange={(v) => patch({ preferredWeek: v })}
                >
                  <SelectTrigger aria-invalid={!!errors.preferredWeek}>
                    <SelectValue placeholder="Choose…" />
                  </SelectTrigger>
                  <SelectContent>
                    {weeks.map((w) => (
                      <SelectItem key={w.iso} value={w.iso}>
                        {w.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Time of day" error={errors.timeOfDay}>
                <Select
                  value={data.timeOfDay}
                  onValueChange={(v) =>
                    patch({ timeOfDay: v as WalkthroughBookingRequest["timeOfDay"] })
                  }
                >
                  <SelectTrigger aria-invalid={!!errors.timeOfDay}>
                    <SelectValue placeholder="Choose…" />
                  </SelectTrigger>
                  <SelectContent>
                    {TIME_OF_DAY_OPTIONS.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <Field label="Roughly how big? (optional)">
              <Select
                value={data.squareFootageRange}
                onValueChange={(v) =>
                  patch({
                    squareFootageRange: v as WalkthroughBookingRequest["squareFootageRange"],
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Not specified" />
                </SelectTrigger>
                <SelectContent>
                  {SQFT_OPTIONS.map((s) => (
                    <SelectItem key={s.value} value={s.value}>
                      {s.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Internal note (optional)">
              <textarea
                rows={3}
                placeholder="Anything ops should know about this booking…"
                value={data.notes}
                onChange={(e) => patch({ notes: e.target.value })}
                className="w-full resize-none rounded-md border border-input bg-background p-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </Field>
          </fieldset>

          {formError && (
            <p role="alert" className="text-sm font-semibold text-destructive">
              {formError}
            </p>
          )}

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              Create booking
            </Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {children}
      {error && (
        <span role="alert" className="mt-1 block text-xs font-semibold text-destructive">
          {error}
        </span>
      )}
    </label>
  );
}
