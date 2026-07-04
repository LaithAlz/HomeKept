import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Wordmark } from "@/components/brand/Wordmark";
import { cn } from "@/lib/utils";
import { BASE_URL, OG_IMAGE_DEFAULT, canonicalUrl } from "@/lib/seo";

export const Route = createFileRoute("/book")({
  head: () => ({
    meta: [
      { title: "Book your free walk-through: HomeKept" },
      {
        name: "description",
        content:
          "Book a free 90-minute walk-through. We assess your home and build a custom maintenance plan.",
      },
      {
        property: "og:title",
        content: "Book a free home walk-through: HomeKept",
      },
      {
        property: "og:description",
        content:
          "90 minutes, no obligation. We assess every system and send a written maintenance plan within a week.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: `${BASE_URL}/book` },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/book") }],
  }),
  component: BookPage,
});

/* -------------------------------------------------------------------------- */
/* Contract-accurate request shape — mirrors POST /api/bookings/walkthrough   */
/* Endpoint is unbuilt (issue #8). Swap the mock body in submitBooking()      */
/* for a real fetch() call when the backend is ready — one line.              */
/* -------------------------------------------------------------------------- */
interface BookingRequest {
  fullName: string;
  email: string;
  phone: string;
  streetAddress: string;
  city: string;
  postalCode: string;
  yearBuilt?: number;
  squareFootageRange?: "<1500" | "1500-2500" | "2500-4000" | ">4000";
  propertyType: "DETACHED" | "SEMI" | "TOWNHOUSE";
  preferredWeek: string; // ISO Monday date e.g. "2026-06-15"
  timeOfDay: "MORNING" | "AFTERNOON" | "EVENING";
  dayPreferences: ("MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN")[];
  notes?: string;
  leadSource?: "WEBSITE_ORGANIC";
  contactConsent: true;
}

/** Mock submission — structured to match the real contract exactly.
 *  To wire to the live endpoint, replace this function body with:
 *    const res = await fetch("/api/bookings/walkthrough", { method: "POST",
 *      headers: { "Content-Type": "application/json" },
 *      body: JSON.stringify(payload) });
 *    if (!res.ok) throw new Error("booking_failed");
 *    return await res.json() as { id: number; status: "PENDING" };
 */
async function submitBooking(payload: BookingRequest): Promise<{ id: number; status: "PENDING" }> {
  // eslint-disable-next-line no-console
  console.debug("[mock] POST /api/bookings/walkthrough", payload);
  await new Promise((r) => setTimeout(r, 600)); // simulate network
  return { id: Math.floor(Math.random() * 9000) + 1000, status: "PENDING" };
}

/* -------------------------------------------------------------------------- */
/* Constants                                                                   */
/* -------------------------------------------------------------------------- */

const CITIES = ["Oakville", "Mississauga", "Milton", "Other"] as const;
type City = (typeof CITIES)[number];

const SQFT_LABELS: Record<string, string> = {
  "<1500": "< 1,500 sq ft",
  "1500-2500": "1,500 – 2,500",
  "2500-4000": "2,500 – 4,000",
  ">4000": "4,000+",
};
const SQFT_KEYS = Object.keys(SQFT_LABELS) as (keyof typeof SQFT_LABELS)[];

type PropertyType = "DETACHED" | "SEMI" | "TOWNHOUSE";
const PROPERTY_CARDS: { type: PropertyType; emoji: string; label: string; sub: string }[] = [
  { type: "DETACHED", emoji: "🏡", label: "Detached", sub: "Stands on its own" },
  { type: "SEMI", emoji: "🏠", label: "Semi", sub: "Shares one wall" },
  { type: "TOWNHOUSE", emoji: "🏘️", label: "Townhouse", sub: "Row or condo town" },
];

type TimeOfDay = "MORNING" | "AFTERNOON" | "EVENING";
const TOD_CARDS: { id: TimeOfDay; emoji: string; label: string; range: string }[] = [
  { id: "MORNING", emoji: "🌅", label: "Morning", range: "8 – 11 AM" },
  { id: "AFTERNOON", emoji: "☀️", label: "Afternoon", range: "12 – 4 PM" },
  { id: "EVENING", emoji: "🌆", label: "Evening", range: "5 – 7 PM" },
];

type DayKey = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";
const DAYS: { key: DayKey; label: string }[] = [
  { key: "MON", label: "Mon" },
  { key: "TUE", label: "Tue" },
  { key: "WED", label: "Wed" },
  { key: "THU", label: "Thu" },
  { key: "FRI", label: "Fri" },
  { key: "SAT", label: "Sat" },
  { key: "SUN", label: "Sun" },
];

const postalRe = /^[A-Za-z]\d[A-Za-z][ -]?\d[A-Za-z]\d$/;
const emailRe = /^\S+@\S+\.\S+$/;
const phoneRe = /^[\d\s()+\-.]{10,}$/;

/* -------------------------------------------------------------------------- */
/* Form state                                                                  */
/* -------------------------------------------------------------------------- */

interface FormData {
  // Step 1
  address: string;
  city: City | "";
  postalCode: string;
  yearBuilt: string;
  sqft: string;
  propertyType: PropertyType | "";
  // Step 2
  preferredWeek: string; // ISO Monday
  timeOfDay: TimeOfDay | "";
  days: DayKey[];
  notes: string;
  // Step 3
  fullName: string;
  email: string;
  phone: string;
  consent: boolean;
}

const EMPTY: FormData = {
  address: "",
  city: "",
  postalCode: "",
  yearBuilt: "",
  sqft: "",
  propertyType: "",
  preferredWeek: "",
  timeOfDay: "",
  days: [],
  notes: "",
  fullName: "",
  email: "",
  phone: "",
  consent: false,
};

const STORAGE_KEY = "homekept:book-draft-v2";

function loadDraft(): FormData {
  if (typeof window === "undefined") return EMPTY;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return EMPTY;
    return { ...EMPTY, ...JSON.parse(raw) };
  } catch {
    return EMPTY;
  }
}

/* -------------------------------------------------------------------------- */
/* Week helpers                                                                */
/* -------------------------------------------------------------------------- */

function getNextMonday(): Date {
  const d = new Date();
  const day = d.getDay(); // 0=Sun
  const daysUntilMon = day === 0 ? 1 : 8 - day;
  d.setDate(d.getDate() + daysUntilMon);
  d.setHours(0, 0, 0, 0);
  return d;
}

function getWeekChips(count = 4): { iso: string; label: string }[] {
  const mon = getNextMonday();
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(mon);
    d.setDate(d.getDate() + i * 7);
    return {
      iso: d.toISOString().slice(0, 10),
      label: "Week of " + d.toLocaleDateString("en-CA", { month: "short", day: "numeric" }),
    };
  });
}

/* -------------------------------------------------------------------------- */
/* Validation                                                                  */
/* -------------------------------------------------------------------------- */

type FieldErrors = Partial<Record<string, string>>;

function validateStep1(f: FormData): FieldErrors {
  const errs: FieldErrors = {};
  if (f.address.trim().length < 3) errs.address = "Please enter your street address";
  if (!f.city) errs.city = "Please pick a city";
  if (!postalRe.test(f.postalCode.trim())) errs.postalCode = "Use a valid Canadian postal code";
  if (!f.propertyType) errs.propertyType = "Pick the closest match";
  if (
    f.yearBuilt.trim() &&
    (!/^\d{4}$/.test(f.yearBuilt.trim()) ||
      +f.yearBuilt < 1800 ||
      +f.yearBuilt > new Date().getFullYear())
  )
    errs.yearBuilt = "Enter a 4-digit year";
  return errs;
}

function validateStep2(f: FormData): FieldErrors {
  const errs: FieldErrors = {};
  if (!f.preferredWeek) errs.preferredWeek = "Pick a week that could work";
  if (!f.timeOfDay) errs.timeOfDay = "Pick a time of day";
  if (f.days.length === 0) errs.days = "Pick at least one day";
  return errs;
}

function validateStep3(f: FormData): FieldErrors {
  const errs: FieldErrors = {};
  if (f.fullName.trim().length < 2) errs.fullName = "Please enter your name";
  if (!emailRe.test(f.email.trim())) errs.email = "Enter a valid email";
  if (!phoneRe.test(f.phone.trim())) errs.phone = "Enter a valid phone number";
  if (!f.consent) errs.consent = "We need your consent to follow up";
  return errs;
}

/* -------------------------------------------------------------------------- */
/* Page root                                                                   */
/* -------------------------------------------------------------------------- */

function BookPage() {
  return (
    <div className="min-h-dvh overflow-x-clip bg-background">
      {/* Ambient glows — decorative */}
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute -right-36 -top-44 size-[520px] animate-drift rounded-full bg-sage/35 blur-[90px]" />
        <div className="absolute -left-52 bottom-[-120px] size-[420px] animate-drift rounded-full bg-honey-soft/45 blur-[90px] [animation-direction:alternate-reverse]" />
      </div>

      {/* Slim booking nav — matches mockup header */}
      <header className="sticky top-4 z-50 px-4 sm:px-5">
        <nav
          className="mx-auto flex w-full max-w-6xl animate-reveal items-center justify-between gap-2 rounded-full border border-border bg-card/85 px-5 py-2 shadow-soft backdrop-blur-xl"
          aria-label="Site navigation"
        >
          <Link to="/" className="flex items-center" aria-label="HomeKept home">
            <Wordmark size="sm" />
          </Link>
          <span className="hidden text-sm text-muted-foreground sm:block">
            Free walk-through &middot;{" "}
            <strong className="text-moss font-semibold">90 min · no obligation</strong>
          </span>
        </nav>
      </header>

      <main id="main" className="relative z-10">
        <div className="mx-auto max-w-6xl px-5 pb-24 pt-10 sm:px-8 sm:pt-14">
          <BookFlow />
        </div>
      </main>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Main flow                                                                   */
/* -------------------------------------------------------------------------- */

function BookFlow() {
  const [data, setData] = useState<FormData>(EMPTY);
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [shaking, setShaking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState<{
    firstName: string;
    email: string;
    timeOfDay: TimeOfDay;
    weekLabel: string;
  } | null>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const weeks = useMemo(() => getWeekChips(4), []);

  // Hydrate draft on mount
  useEffect(() => {
    setData(loadDraft());
  }, []);

  // Persist draft
  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      /* ignore */
    }
  }, [data]);

  function patch(updates: Partial<FormData>) {
    setData((d) => ({ ...d, ...updates }));
    // Clear errors for patched fields
    const cleared: FieldErrors = { ...errors };
    for (const k of Object.keys(updates)) delete cleared[k];
    setErrors(cleared);
  }

  function shake() {
    setShaking(false);
    // Double rAF to force re-render before re-adding class
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        setShaking(true);
        setTimeout(() => setShaking(false), 500);
      }),
    );
  }

  function goNext() {
    const errs = step === 1 ? validateStep1(data) : validateStep2(data);
    if (Object.keys(errs).length) {
      setErrors(errs);
      shake();
      return;
    }
    setErrors({});
    setStep((s) => (s < 3 ? ((s + 1) as 1 | 2 | 3) : s));
    cardRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function goBack() {
    setErrors({});
    setStep((s) => (s > 1 ? ((s - 1) as 1 | 2 | 3) : s));
    cardRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  async function submit() {
    const errs = validateStep3(data);
    if (Object.keys(errs).length) {
      setErrors(errs);
      shake();
      return;
    }
    setErrors({});
    setSubmitting(true);
    setSubmitError(null);

    // Build the contract-accurate payload
    const sqftMap: Record<string, BookingRequest["squareFootageRange"]> = {
      "<1500": "<1500",
      "1500-2500": "1500-2500",
      "2500-4000": "2500-4000",
      ">4000": ">4000",
    };

    const payload: BookingRequest = {
      fullName: data.fullName.trim(),
      email: data.email.trim(),
      phone: data.phone.trim(),
      streetAddress: data.address.trim(),
      city: data.city as City,
      postalCode: data.postalCode.trim().toUpperCase(),
      propertyType: data.propertyType as PropertyType,
      preferredWeek: data.preferredWeek,
      timeOfDay: data.timeOfDay as TimeOfDay,
      dayPreferences: data.days,
      notes: data.notes.trim() || undefined,
      leadSource: "WEBSITE_ORGANIC",
      contactConsent: true,
      ...(data.yearBuilt.trim() ? { yearBuilt: parseInt(data.yearBuilt, 10) } : {}),
      ...(data.sqft ? { squareFootageRange: sqftMap[data.sqft] } : {}),
    };

    try {
      await submitBooking(payload);
      const weekObj = weeks.find((w) => w.iso === data.preferredWeek);
      setSubmitted({
        firstName: data.fullName.trim().split(" ")[0],
        email: data.email.trim(),
        timeOfDay: data.timeOfDay as TimeOfDay,
        weekLabel: weekObj?.label ?? "the week you picked",
      });
      try {
        window.localStorage.removeItem(STORAGE_KEY);
      } catch {
        /* ignore */
      }
    } catch {
      setSubmitError("Something went wrong. Please try again in a moment.");
      setSubmitting(false);
    }
  }

  const weekLabel = weeks.find((w) => w.iso === data.preferredWeek)?.label;
  const todLabel = TOD_CARDS.find((t) => t.id === data.timeOfDay)?.label;
  const daysLabel = data.days.map((d) => DAYS.find((x) => x.key === d)?.label).join(" · ");

  if (submitted) {
    return <SuccessScreen {...submitted} />;
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1.45fr_1fr] lg:gap-12 lg:items-start">
      {/* ── Wizard column ── */}
      <div>
        <div className="animate-reveal">
          <h1 className="font-display text-[clamp(30px,4vw,46px)] font-[560] leading-[1.1] tracking-[-0.02em] text-primary">
            Let&rsquo;s walk <em className="font-[480] italic text-moss">your home.</em>
          </h1>
          <p className="mt-2.5 text-[15px] text-muted-foreground">
            Three quick steps, about a minute. We&rsquo;ll confirm your time within one business
            day.
          </p>
        </div>

        {/* Stepper */}
        <StepIndicator step={step} />

        {/* Wizard card */}
        <div
          ref={cardRef}
          className={cn(
            "animate-reveal mt-6 rounded-[34px] border border-border bg-card p-7 shadow-[0_24px_50px_-30px_rgba(30,58,43,0.35)] sm:p-10",
            "[animation-delay:180ms]",
            shaking && "animate-shake",
          )}
          style={{ "--reveal-index": 0 } as React.CSSProperties}
        >
          {step === 1 && <Step1 data={data} errors={errors} onChange={patch} />}
          {step === 2 && <Step2 data={data} errors={errors} onChange={patch} weeks={weeks} />}
          {step === 3 && <Step3 data={data} errors={errors} onChange={patch} />}

          {submitError && (
            <p role="alert" className="mt-4 text-sm font-semibold text-destructive">
              {submitError}
            </p>
          )}

          {/* Footer nav */}
          <div className="mt-8 flex items-center justify-between gap-4 border-t border-dashed border-border/60 pt-6">
            {step > 1 ? (
              <Button
                type="button"
                variant="outline"
                onClick={goBack}
                className="gap-2 border-border/40 text-muted-foreground hover:text-primary"
              >
                ← Back
              </Button>
            ) : (
              <Button asChild variant="ghost" className="text-muted-foreground hover:text-primary">
                <Link to="/">Cancel</Link>
              </Button>
            )}

            {step < 3 ? (
              <Button type="button" variant="accent" size="lg" onClick={goNext}>
                Continue <span aria-hidden="true">→</span>
              </Button>
            ) : (
              <Button
                type="button"
                variant="accent"
                size="lg"
                onClick={submit}
                disabled={submitting}
                aria-busy={submitting}
              >
                {submitting ? "Sending…" : "Book my walk-through ✿"}
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* ── Live summary sidebar ── */}
      <aside
        className="relative animate-reveal overflow-hidden rounded-[30px] bg-primary p-7 text-primary-foreground shadow-[0_28px_56px_-28px_rgba(30,58,43,0.55)] lg:sticky lg:top-28 lg:self-start"
        style={{ "--reveal-index": 2 } as React.CSSProperties}
        aria-label="Walk-through summary"
      >
        {/* decorative orb */}
        <div
          aria-hidden="true"
          className="absolute -right-16 -top-20 size-60 rounded-full bg-accent/20 blur-[54px]"
        />

        <p className="relative text-[11.5px] font-bold uppercase tracking-[0.12em] text-sage">
          Your walk-through
        </p>
        <h2 className="relative mt-2 font-display text-2xl font-[600] leading-tight">
          Building your{" "}
          <em className="font-[480] italic" style={{ color: "var(--honey-soft)" }}>
            visit…
          </em>
        </h2>

        {/* Summary rows */}
        <dl className="relative mt-5 flex flex-col gap-px overflow-hidden rounded-2xl">
          <SumRow
            label="Home"
            value={
              data.address.trim()
                ? `${data.address.trim()}${data.city ? ", " + data.city : ""}`
                : null
            }
          />
          <SumRow
            label="Type"
            value={
              data.propertyType
                ? (PROPERTY_CARDS.find((p) => p.type === data.propertyType)?.label ?? null)
                : null
            }
          />
          <SumRow label="Week" value={weekLabel ?? null} />
          <SumRow label="Time" value={todLabel ?? null} />
          <SumRow label="Days" value={daysLabel || null} />
          <SumRow label="For" value={data.fullName.trim() || null} />
        </dl>

        {/* Fact pills */}
        <div className="relative mt-5 flex flex-wrap gap-2" aria-label="Walk-through details">
          <span className="rounded-full border border-primary-foreground/16 bg-primary-foreground/10 px-3 py-1.5 text-[12.5px]">
            💸 $0: completely free
          </span>
          <span className="rounded-full border border-primary-foreground/16 bg-primary-foreground/10 px-3 py-1.5 text-[12.5px]">
            ⏱️ About 90 minutes
          </span>
          <span className="rounded-full border border-primary-foreground/16 bg-primary-foreground/10 px-3 py-1.5 text-[12.5px]">
            🤝 No obligation
          </span>
        </div>
      </aside>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Summary row                                                                 */
/* -------------------------------------------------------------------------- */

function SumRow({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex items-center justify-between gap-3 bg-primary-foreground/[0.07] px-4 py-2.5 text-[13.5px]">
      <dt className="text-sage">{label}</dt>
      <dd
        className={cn(
          "text-right font-semibold transition-all duration-300",
          value ? "text-primary-foreground" : "font-normal text-primary-foreground/35",
        )}
      >
        {value ?? "Not yet"}
      </dd>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Step indicator                                                              */
/* -------------------------------------------------------------------------- */

function StepIndicator({ step }: { step: 1 | 2 | 3 }) {
  const nodes = [
    { n: 1, label: "Your home" },
    { n: 2, label: "Timing" },
    { n: 3, label: "You" },
  ];

  return (
    <ol
      className="animate-reveal mt-7 flex items-center gap-0"
      aria-label="Booking progress"
      style={{ "--reveal-index": 1 } as React.CSSProperties}
    >
      {nodes.map((node, i) => {
        const active = step === node.n;
        const done = step > node.n;
        return (
          <li key={node.n} className="flex items-center">
            <div
              className={cn(
                "flex items-center gap-2.5 text-[13.5px] font-semibold",
                active ? "text-primary" : "text-muted-foreground",
              )}
            >
              <span
                className={cn(
                  "grid size-9 place-items-center font-display text-[15px] font-semibold transition-all duration-300",
                  "[border-radius:50%_50%_50%_11px]",
                  done && "bg-moss text-white scale-100",
                  active && "bg-primary text-primary-foreground scale-110 -rotate-6",
                  !done && !active && "bg-surface text-muted-foreground",
                )}
                aria-current={active ? "step" : undefined}
              >
                {done ? "✓" : node.n}
              </span>
              <span className="hidden sm:inline">{node.label}</span>
            </div>
            {i < nodes.length - 1 && (
              <div className="relative mx-3.5 h-[2.5px] min-w-[30px] flex-1 overflow-hidden rounded-full bg-surface sm:min-w-[48px]">
                <span
                  className={cn(
                    "absolute inset-0 origin-left rounded-full bg-moss transition-transform duration-500",
                    done ? "scale-x-100" : "scale-x-0",
                  )}
                />
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}

/* -------------------------------------------------------------------------- */
/* Step 1 — About the home                                                     */
/* -------------------------------------------------------------------------- */

function Step1({
  data,
  errors,
  onChange,
}: {
  data: FormData;
  errors: FieldErrors;
  onChange: (p: Partial<FormData>) => void;
}) {
  return (
    <div>
      <h2 className="font-display text-[25px] font-[600] text-primary">Tell us about the home.</h2>
      <p className="mt-1.5 text-[14px] text-muted-foreground">
        Just enough to plan the visit, no measuring tape required.
      </p>

      <div className="mt-6 grid gap-5 sm:grid-cols-2">
        {/* Street address — full width */}
        <div className="sm:col-span-2">
          <FieldWrap error={errors.address}>
            <label htmlFor="f-address" className="field-label">
              Street address
            </label>
            <input
              id="f-address"
              type="text"
              autoComplete="street-address"
              placeholder="14 Maple Ridge Crt"
              value={data.address}
              onChange={(e) => onChange({ address: e.target.value })}
              aria-invalid={!!errors.address}
              aria-describedby={errors.address ? "err-address" : undefined}
              className={fieldCls(!!errors.address)}
            />
            <FieldError id="err-address" msg={errors.address} />
          </FieldWrap>
        </div>

        {/* City */}
        <FieldWrap error={errors.city}>
          <label htmlFor="f-city" className="field-label">
            City
          </label>
          <select
            id="f-city"
            value={data.city}
            onChange={(e) => onChange({ city: e.target.value as City | "" })}
            aria-invalid={!!errors.city}
            aria-describedby={errors.city ? "err-city" : undefined}
            className={fieldCls(!!errors.city)}
          >
            <option value="">Choose…</option>
            {CITIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <FieldError id="err-city" msg={errors.city} />
        </FieldWrap>

        {/* Postal code */}
        <FieldWrap error={errors.postalCode}>
          <label htmlFor="f-postal" className="field-label">
            Postal code
          </label>
          <input
            id="f-postal"
            type="text"
            autoComplete="postal-code"
            placeholder="L6H 1A1"
            value={data.postalCode}
            onChange={(e) => onChange({ postalCode: e.target.value.toUpperCase() })}
            aria-invalid={!!errors.postalCode}
            aria-describedby={errors.postalCode ? "err-postal" : undefined}
            className={fieldCls(!!errors.postalCode)}
          />
          <FieldError id="err-postal" msg={errors.postalCode} />
        </FieldWrap>
      </div>

      {/* Property type */}
      <div className="mt-5" role="group" aria-labelledby="ptype-label">
        <p id="ptype-label" className="field-label">
          Property type
        </p>
        <div className="mt-2 grid grid-cols-1 gap-3 min-[420px]:grid-cols-3">
          {PROPERTY_CARDS.map((p) => {
            const sel = data.propertyType === p.type;
            return (
              <button
                key={p.type}
                type="button"
                onClick={() => onChange({ propertyType: p.type })}
                aria-pressed={sel}
                className={cn(
                  "rounded-[20px] border-[1.5px] p-4 text-left transition-all duration-200 hover:-translate-y-[3px]",
                  sel
                    ? "border-transparent bg-primary text-primary-foreground"
                    : "border-transparent bg-background hover:border-sage",
                  errors.propertyType && !sel && "border-destructive/45",
                )}
              >
                <span className="text-[22px]" aria-hidden="true">
                  {p.emoji}
                </span>
                <strong
                  className={cn(
                    "mt-1.5 block text-[14.5px] font-semibold",
                    sel ? "text-primary-foreground" : "text-primary",
                  )}
                >
                  {p.label}
                </strong>
                <span className={cn("text-[12.5px]", sel ? "text-sage" : "text-muted-foreground")}>
                  {p.sub}
                </span>
              </button>
            );
          })}
        </div>
        {errors.propertyType && (
          <p className="mt-1.5 text-[12.5px] font-semibold text-destructive" role="alert">
            {errors.propertyType}
          </p>
        )}
      </div>

      {/* Size chips — optional */}
      <div className="mt-5" role="group" aria-labelledby="sqft-label">
        <p id="sqft-label" className="field-label">
          Roughly how big? <span className="font-normal text-muted-foreground">(optional)</span>
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {SQFT_KEYS.map((k) => {
            const sel = data.sqft === k;
            return (
              <button
                key={k}
                type="button"
                onClick={() => onChange({ sqft: sel ? "" : k })}
                aria-pressed={sel}
                className={cn("chip", sel && "chip-on")}
              >
                {SQFT_LABELS[k]}
              </button>
            );
          })}
        </div>
      </div>

      {/* Year built — optional */}
      <div className="mt-5">
        <FieldWrap error={errors.yearBuilt}>
          <label htmlFor="f-year" className="field-label">
            Year built <span className="font-normal text-muted-foreground">(optional)</span>
          </label>
          <input
            id="f-year"
            type="text"
            inputMode="numeric"
            placeholder="1998"
            maxLength={4}
            value={data.yearBuilt}
            onChange={(e) => onChange({ yearBuilt: e.target.value })}
            aria-invalid={!!errors.yearBuilt}
            aria-describedby={errors.yearBuilt ? "err-year" : undefined}
            className={fieldCls(!!errors.yearBuilt)}
          />
          <FieldError id="err-year" msg={errors.yearBuilt} />
        </FieldWrap>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Step 2 — Timing                                                             */
/* -------------------------------------------------------------------------- */

function Step2({
  data,
  errors,
  onChange,
  weeks,
}: {
  data: FormData;
  errors: FieldErrors;
  onChange: (p: Partial<FormData>) => void;
  weeks: { iso: string; label: string }[];
}) {
  return (
    <div>
      <h2 className="font-display text-[25px] font-[600] text-primary">When suits you?</h2>
      <p className="mt-1.5 text-[14px] text-muted-foreground">
        Pick a week and the times that usually work, we&rsquo;ll confirm an exact slot.
      </p>

      {/* Preferred week */}
      <div className="mt-6" role="group" aria-labelledby="week-label">
        <p id="week-label" className="field-label">
          Preferred week
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {weeks.map((w) => {
            const sel = data.preferredWeek === w.iso;
            return (
              <button
                key={w.iso}
                type="button"
                onClick={() => onChange({ preferredWeek: w.iso })}
                aria-pressed={sel}
                className={cn("chip", sel && "chip-on")}
              >
                {w.label}
              </button>
            );
          })}
        </div>
        {errors.preferredWeek && (
          <p className="mt-1.5 text-[12.5px] font-semibold text-destructive" role="alert">
            {errors.preferredWeek}
          </p>
        )}
      </div>

      {/* Time of day */}
      <div className="mt-6" role="group" aria-labelledby="tod-label">
        <p id="tod-label" className="field-label">
          Time of day
        </p>
        <div className="mt-2 grid grid-cols-1 gap-3 min-[420px]:grid-cols-3">
          {TOD_CARDS.map((t) => {
            const sel = data.timeOfDay === t.id;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => onChange({ timeOfDay: t.id })}
                aria-pressed={sel}
                className={cn(
                  "rounded-[20px] border-[1.5px] p-4 text-left transition-all duration-200 hover:-translate-y-[3px]",
                  sel
                    ? "border-transparent bg-primary text-primary-foreground"
                    : "border-transparent bg-background hover:border-sage",
                  errors.timeOfDay && !sel && "border-destructive/45",
                )}
              >
                <span className="text-[22px]" aria-hidden="true">
                  {t.emoji}
                </span>
                <strong
                  className={cn(
                    "mt-1.5 block text-[14.5px] font-semibold",
                    sel ? "text-primary-foreground" : "text-primary",
                  )}
                >
                  {t.label}
                </strong>
                <span className={cn("text-[12.5px]", sel ? "text-sage" : "text-muted-foreground")}>
                  {t.range}
                </span>
              </button>
            );
          })}
        </div>
        {errors.timeOfDay && (
          <p className="mt-1.5 text-[12.5px] font-semibold text-destructive" role="alert">
            {errors.timeOfDay}
          </p>
        )}
      </div>

      {/* Day chips */}
      <div className="mt-6" role="group" aria-labelledby="days-label">
        <p id="days-label" className="field-label">
          Days that usually work{" "}
          <span className="font-normal text-muted-foreground">(pick any)</span>
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {DAYS.map((d) => {
            const sel = data.days.includes(d.key);
            return (
              <button
                key={d.key}
                type="button"
                onClick={() => {
                  const next = sel ? data.days.filter((x) => x !== d.key) : [...data.days, d.key];
                  onChange({ days: next });
                }}
                aria-pressed={sel}
                className={cn("chip", sel && "chip-on")}
              >
                {d.label}
              </button>
            );
          })}
        </div>
        {errors.days && (
          <p className="mt-1.5 text-[12.5px] font-semibold text-destructive" role="alert">
            {errors.days}
          </p>
        )}
      </div>

      {/* Notes */}
      <div className="mt-6">
        <label htmlFor="f-notes" className="field-label">
          Anything we should know?{" "}
          <span className="font-normal text-muted-foreground">(optional)</span>
        </label>
        <textarea
          id="f-notes"
          rows={3}
          placeholder="Gate code, a dog who loves visitors, the furnace that makes that noise…"
          value={data.notes}
          onChange={(e) => onChange({ notes: e.target.value })}
          maxLength={1000}
          className={cn(fieldCls(false), "resize-y min-h-[88px]")}
        />
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Step 3 — Contact                                                            */
/* -------------------------------------------------------------------------- */

function Step3({
  data,
  errors,
  onChange,
}: {
  data: FormData;
  errors: FieldErrors;
  onChange: (p: Partial<FormData>) => void;
}) {
  return (
    <div>
      <h2 className="font-display text-[25px] font-[600] text-primary">And you are&hellip;</h2>
      <p className="mt-1.5 text-[14px] text-muted-foreground">
        So we know who&rsquo;s opening the door.
      </p>

      <div className="mt-6 space-y-5">
        <FieldWrap error={errors.fullName}>
          <label htmlFor="f-name" className="field-label">
            Full name
          </label>
          <input
            id="f-name"
            type="text"
            autoComplete="name"
            placeholder="Priya Sharma"
            value={data.fullName}
            onChange={(e) => onChange({ fullName: e.target.value })}
            aria-invalid={!!errors.fullName}
            aria-describedby={errors.fullName ? "err-name" : undefined}
            className={fieldCls(!!errors.fullName)}
          />
          <FieldError id="err-name" msg={errors.fullName} />
        </FieldWrap>

        <div className="grid gap-5 sm:grid-cols-2">
          <FieldWrap error={errors.email}>
            <label htmlFor="f-email" className="field-label">
              Email
            </label>
            <input
              id="f-email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={data.email}
              onChange={(e) => onChange({ email: e.target.value })}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? "err-email" : undefined}
              className={fieldCls(!!errors.email)}
            />
            <FieldError id="err-email" msg={errors.email} />
          </FieldWrap>

          <FieldWrap error={errors.phone}>
            <label htmlFor="f-phone" className="field-label">
              Phone
            </label>
            <input
              id="f-phone"
              type="tel"
              autoComplete="tel"
              placeholder="(905) 555-0123"
              value={data.phone}
              onChange={(e) => onChange({ phone: e.target.value })}
              aria-invalid={!!errors.phone}
              aria-describedby={errors.phone ? "err-phone" : undefined}
              className={fieldCls(!!errors.phone)}
            />
            <FieldError id="err-phone" msg={errors.phone} />
          </FieldWrap>
        </div>

        {/* Consent */}
        <ConsentToggle
          checked={data.consent}
          error={!!errors.consent}
          onChange={(v) => onChange({ consent: v })}
        />
        {errors.consent && (
          <p id="err-consent" className="text-[12.5px] font-semibold text-destructive" role="alert">
            {errors.consent}
          </p>
        )}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Consent toggle (custom checkbox to match mockup)                           */
/* -------------------------------------------------------------------------- */

function ConsentToggle({
  checked,
  error,
  onChange,
}: {
  checked: boolean;
  error: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3.5 rounded-[18px] border-[1.5px] bg-background p-4 transition-colors duration-200",
        checked ? "border-moss/60" : error ? "border-destructive" : "border-transparent",
      )}
    >
      <input
        type="checkbox"
        className="sr-only"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        aria-describedby="err-consent"
      />
      {/* Visual checkbox */}
      <span
        aria-hidden="true"
        className={cn(
          "mt-0.5 grid size-6 shrink-0 place-items-center rounded-[8px] border-2 bg-white transition-all duration-300",
          checked ? "border-moss bg-moss scale-105 -rotate-5" : "border-sage",
        )}
      >
        {checked && (
          <svg
            viewBox="0 0 14 14"
            className="size-3.5 stroke-white"
            fill="none"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <polyline points="2,7.5 5.5,11 12,3.5" />
          </svg>
        )}
      </span>
      <span className="text-[13.5px] text-muted-foreground">
        It&rsquo;s okay for HomeKept to contact me about my walk-through. No spam, no third parties,
        just scheduling.
      </span>
    </label>
  );
}

/* -------------------------------------------------------------------------- */
/* Success screen                                                              */
/* -------------------------------------------------------------------------- */

function SuccessScreen({
  firstName,
  email,
  timeOfDay,
  weekLabel,
}: {
  firstName: string;
  email: string;
  timeOfDay: TimeOfDay;
  weekLabel: string;
}) {
  const timeLabel =
    timeOfDay === "MORNING" ? "morning" : timeOfDay === "AFTERNOON" ? "afternoon" : "evening";

  return (
    <div className="mx-auto max-w-2xl animate-reveal text-center">
      {/* Animated check SVG */}
      <svg
        viewBox="0 0 110 110"
        className="mx-auto size-[110px]"
        aria-hidden="true"
        focusable="false"
      >
        <circle
          cx="55"
          cy="55"
          r="50"
          pathLength="1"
          className="fill-moss/10 stroke-moss"
          strokeWidth="3"
          style={{
            strokeDasharray: 1,
            strokeDashoffset: 1,
            animation: "draw 0.8s ease forwards",
          }}
        />
        <polyline
          points="34,57 49,72 78,40"
          pathLength="1"
          className="stroke-moss"
          fill="none"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{
            strokeDasharray: 1,
            strokeDashoffset: 1,
            animation: "draw 0.5s ease 0.55s forwards",
          }}
        />
      </svg>

      <h1 className="mt-6 font-display text-[clamp(26px,4vw,32px)] font-[600] text-primary">
        Request received, <em className="font-[480] italic text-moss">{firstName}.</em>
      </h1>

      <p className="mx-auto mt-3 max-w-[42ch] text-[15px] text-muted-foreground">
        We&rsquo;ll email <strong className="font-medium text-foreground">{email}</strong> to
        confirm a {timeLabel} slot during{" "}
        <strong className="font-medium text-foreground">{weekLabel.toLowerCase()}</strong>.
      </p>

      <ul className="mx-auto mt-8 max-w-sm space-y-3 text-left" aria-label="What happens next">
        {[
          "We confirm your exact time within one business day.",
          "Your technician walks the home with you: 90 minutes.",
          "Your written maintenance plan arrives within a week.",
        ].map((text, i) => (
          <li
            key={i}
            className="flex items-start gap-3.5 text-[14px] text-foreground"
            style={{
              opacity: 0,
              transform: "translateY(12px)",
              animation: `reveal 0.5s cubic-bezier(0.2,0.8,0.2,1) ${0.9 + i * 0.15}s forwards`,
            }}
          >
            <span
              aria-hidden="true"
              className="grid size-6 shrink-0 place-items-center rounded-[9px] bg-honey-soft text-[12px] font-bold text-primary mt-0.5"
            >
              {i + 1}
            </span>
            {text}
          </li>
        ))}
      </ul>

      <div className="mt-10">
        <Button asChild variant="default" size="lg">
          <Link to="/">Back to home</Link>
        </Button>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Field helpers                                                               */
/* -------------------------------------------------------------------------- */

function fieldCls(invalid: boolean) {
  return cn(
    "w-full rounded-2xl border-[1.5px] bg-background px-4 py-3 text-[15px] text-foreground outline-none transition-all duration-200",
    "placeholder:text-muted-foreground/60",
    "focus:border-moss focus:bg-white focus:shadow-[0_0_0_4px_rgba(94,125,98,0.15)]",
    invalid && "border-destructive bg-[#FDF6F3] focus:border-destructive",
    !invalid && "border-transparent",
  );
}

function FieldWrap({ children, error }: { children: React.ReactNode; error?: string }) {
  return <div className={cn("space-y-1.5", error && "has-error")}>{children}</div>;
}

function FieldError({ id, msg }: { id: string; msg?: string }) {
  if (!msg) return null;
  return (
    <p id={id} role="alert" className="text-[12.5px] font-semibold text-destructive">
      {msg}
    </p>
  );
}
