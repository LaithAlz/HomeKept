/**
 * New booking sheet — logs a walk-through someone booked by phone or in
 * person, through the same public endpoint the customer wizard uses
 * (POST /api/bookings/walkthrough, see @/lib/booking). There is no plan or
 * billing step here: a walk-through booking has neither, a plan is only
 * chosen later at activation. Submitting creates a real PENDING walk-through
 * booking that lands in the same pipeline as a self-serve request.
 */
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Loader2, X } from "lucide-react";
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
import { ApiError } from "@/lib/api";
import { CITIES, EMAIL_RE, getNextMonday, type WalkthroughBookingRequest } from "@/lib/booking";
import { formatWeekOf, useCreateWalkthroughBooking } from "@/lib/admin";

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

type BookingFieldErrors = Partial<Record<string, string>>;

function validateBookingForm(f: NewBookingFormData): BookingFieldErrors {
  const errs: BookingFieldErrors = {};
  if (f.fullName.trim().length < 2) errs.fullName = "Enter the homeowner's name";
  if (!EMAIL_RE.test(f.email.trim())) errs.email = "Enter a valid email address";
  if (!f.phone.trim()) errs.phone = "Enter a phone number";
  if (!f.streetAddress.trim()) errs.streetAddress = "Enter a street address";
  if (!f.city) errs.city = "Pick a city";
  if (!f.postalCode.trim()) errs.postalCode = "Enter a postal code";
  if (!f.propertyType) errs.propertyType = "Pick a property type";
  if (!f.preferredWeek) errs.preferredWeek = "Pick a week";
  if (!f.timeOfDay) errs.timeOfDay = "Pick a time of day";
  return errs;
}

export function NewBookingSheet({
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
                    {CITIES.map((c) => (
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
