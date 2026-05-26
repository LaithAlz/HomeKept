import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Check, ChevronLeft, ChevronRight, ClipboardCheck, Home, Mail, Sun } from "lucide-react";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/book")({
  head: () => ({
    meta: [
      { title: "Book your free walk-through — HomeKept" },
      {
        name: "description",
        content:
          "Book a free 90-minute walk-through. We assess your home and email a custom maintenance plan the next day.",
      },
    ],
  }),
  component: BookPage,
});

// ---------- Schemas ----------
const cities = ["Oakville", "Mississauga", "Milton", "Other"] as const;
const sqftRanges = ["<1500", "1500–2500", "2500–4000", ">4000"] as const;
const propertyTypes = ["Detached", "Semi", "Townhouse"] as const;
const timeWindows = [
  { id: "morning", label: "Morning", range: "8–11 AM" },
  { id: "afternoon", label: "Afternoon", range: "12–4 PM" },
  { id: "evening", label: "Evening", range: "5–7 PM" },
] as const;
const daysOfWeek = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;
const postalCodeRegex = /^[A-Za-z]\d[A-Za-z][ -]?\d[A-Za-z]\d$/;

const step1Schema = z.object({
  address: z.string().trim().min(3, "Enter your street address").max(200),
  city: z.enum([...cities] as [string, ...string[]], { error: "Pick a city" }),
  postalCode: z
    .string()
    .trim()
    .regex(postalCodeRegex, "Use a valid Canadian postal code (e.g. L6H 1A1)"),
  yearBuilt: z
    .string()
    .trim()
    .optional()
    .refine(
      (v) => !v || (/^\d{4}$/.test(v) && +v >= 1800 && +v <= new Date().getFullYear()),
      "Enter a 4-digit year",
    ),
  sqft: z.enum([...sqftRanges] as [string, ...string[]]).optional(),
  propertyType: z.enum([...propertyTypes] as [string, ...string[]], {
    error: "Pick a property type",
  }),
});

const step2Schema = z.object({
  weekStart: z.string().min(1, "Pick a preferred week"),
  timeOfDay: z.enum(["morning", "afternoon", "evening"], {
    error: "Pick a time of day",
  }),
  days: z
    .array(z.enum([...daysOfWeek] as [string, ...string[]]))
    .min(1, "Pick at least one day"),
  notes: z.string().max(1000).optional(),
});

const step3Schema = z.object({
  fullName: z.string().trim().min(2, "Enter your full name").max(120),
  email: z.string().trim().email("Enter a valid email").max(255),
  phone: z
    .string()
    .trim()
    .regex(/^[\d\s()+\-.]{10,}$/, "Enter a valid phone number"),
  consent: z.literal(true, { error: "We need your consent to follow up" }),
});

type FormState = {
  step1: Partial<z.infer<typeof step1Schema>>;
  step2: Partial<z.infer<typeof step2Schema>> & { days?: string[] };
  step3: Partial<z.infer<typeof step3Schema>> & { consent?: boolean };
};

const STORAGE_KEY = "homekept:book-draft";

const emptyState: FormState = {
  step1: {},
  step2: { days: [] },
  step3: {},
};

function loadState(): FormState {
  if (typeof window === "undefined") return emptyState;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return emptyState;
    return { ...emptyState, ...JSON.parse(raw) };
  } catch {
    return emptyState;
  }
}

// ---------- Week helpers ----------
function startOfWeek(d: Date): Date {
  const x = new Date(d);
  const day = x.getDay(); // 0=Sun
  const diff = (day + 6) % 7; // Mon = 0
  x.setDate(x.getDate() - diff);
  x.setHours(0, 0, 0, 0);
  return x;
}

function formatWeek(start: Date): string {
  const end = new Date(start);
  end.setDate(end.getDate() + 6);
  const fmt = (d: Date) =>
    d.toLocaleDateString("en-CA", { month: "short", day: "numeric" });
  return `${fmt(start)} – ${fmt(end)}`;
}

function nextWeeks(n: number): { iso: string; label: string }[] {
  const base = startOfWeek(new Date());
  base.setDate(base.getDate() + 7); // start next week
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(base);
    d.setDate(d.getDate() + i * 7);
    return { iso: d.toISOString().slice(0, 10), label: formatWeek(d) };
  });
}

// ---------- Page ----------
function BookPage() {
  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main" className="mx-auto max-w-6xl px-6 py-12 md:py-20">
        <BookFlow />
      </main>
      <SiteFooter />
    </div>
  );
}

function BookFlow() {
  const [state, setState] = useState<FormState>(emptyState);
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState<null | {
    step1: z.infer<typeof step1Schema>;
    step2: z.infer<typeof step2Schema>;
    step3: z.infer<typeof step3Schema>;
  }>(null);

  // hydrate from localStorage on mount
  useEffect(() => {
    setState(loadState());
  }, []);

  // persist on change
  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* ignore */
    }
  }, [state]);

  function update<K extends keyof FormState>(key: K, patch: Partial<FormState[K]>) {
    setState((s) => ({ ...s, [key]: { ...s[key], ...patch } }));
  }

  function goNext() {
    if (step === 1) {
      const r = step1Schema.safeParse(state.step1);
      if (!r.success) return setErrors(flatten(r.error));
      setErrors({});
      setStep(2);
    } else if (step === 2) {
      const r = step2Schema.safeParse(state.step2);
      if (!r.success) return setErrors(flatten(r.error));
      setErrors({});
      setStep(3);
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function goBack() {
    setErrors({});
    setStep((s) => (s === 3 ? 2 : 1));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function submit() {
    const r1 = step1Schema.safeParse(state.step1);
    const r2 = step2Schema.safeParse(state.step2);
    const r3 = step3Schema.safeParse(state.step3);
    if (!r3.success) return setErrors(flatten(r3.error));
    if (!r1.success || !r2.success) {
      setStep(!r1.success ? 1 : 2);
      setErrors(flatten((!r1.success ? r1.error : r2.error)!));
      return;
    }
    setErrors({});
    setSubmitted({ step1: r1.data, step2: r2.data, step3: r3.data });
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  if (submitted) {
    return <Confirmation data={submitted} />;
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_360px]">
      <div>
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">
          Free walk-through
        </p>
        <h1 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
          Book your 90-minute home visit.
        </h1>
        <p className="mt-3 max-w-xl text-muted-foreground">
          Tell us about your home and when you're around. We'll confirm by email and follow up the next day with a custom maintenance plan.
        </p>

        <StepIndicator step={step} />

        <div className="mt-8 rounded-3xl border border-border bg-card p-6 shadow-soft md:p-8">
          {step === 1 && (
            <Step1
              value={state.step1}
              errors={errors}
              onChange={(p) => update("step1", p)}
            />
          )}
          {step === 2 && (
            <Step2
              value={state.step2}
              errors={errors}
              onChange={(p) => update("step2", p)}
            />
          )}
          {step === 3 && (
            <Step3
              value={state.step3}
              errors={errors}
              onChange={(p) => update("step3", p)}
            />
          )}

          <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
            {step > 1 ? (
              <Button variant="outline" onClick={goBack}>
                <ChevronLeft className="size-4" />
                Back
              </Button>
            ) : (
              <Button asChild variant="ghost">
                <Link to="/">Cancel</Link>
              </Button>
            )}
            {step < 3 ? (
              <Button onClick={goNext} size="lg">
                Continue
                <ChevronRight className="size-4" />
              </Button>
            ) : (
              <Button onClick={submit} size="lg" variant="accent">
                Book my walk-through
              </Button>
            )}
          </div>
        </div>
      </div>

      <Aside />
    </div>
  );
}

function flatten(err: z.ZodError): Record<string, string> {
  const out: Record<string, string> = {};
  for (const i of err.issues) {
    const k = i.path.join(".");
    if (!out[k]) out[k] = i.message;
  }
  return out;
}

// ---------- Steps ----------
function StepIndicator({ step }: { step: 1 | 2 | 3 }) {
  const items = [
    { n: 1, label: "About the home" },
    { n: 2, label: "Pick a time" },
    { n: 3, label: "Contact details" },
  ];
  return (
    <ol className="mt-8 flex items-center gap-2 md:gap-4" aria-label="Booking progress">
      {items.map((it, i) => {
        const active = step === it.n;
        const done = step > it.n;
        return (
          <li key={it.n} className="flex flex-1 items-center gap-2 md:gap-3">
            <span
              className={cn(
                "flex size-8 shrink-0 items-center justify-center rounded-full border text-sm font-semibold transition-colors",
                done && "border-primary bg-primary text-primary-foreground",
                active && "border-accent bg-accent text-accent-foreground",
                !done && !active && "border-border bg-background text-muted-foreground",
              )}
              aria-current={active ? "step" : undefined}
            >
              {done ? <Check className="size-4" /> : it.n}
            </span>
            <span
              className={cn(
                "hidden text-sm font-medium md:inline",
                active ? "text-foreground" : "text-muted-foreground",
              )}
            >
              {it.label}
            </span>
            {i < items.length - 1 && (
              <span
                className={cn(
                  "ml-1 hidden h-px flex-1 md:block",
                  done ? "bg-primary/40" : "bg-border",
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}

function FieldError({ id, msg }: { id: string; msg?: string }) {
  if (!msg) return null;
  return (
    <p id={id} className="mt-1.5 text-sm text-destructive">
      {msg}
    </p>
  );
}

function Step1({
  value,
  errors,
  onChange,
}: {
  value: FormState["step1"];
  errors: Record<string, string>;
  onChange: (p: Partial<FormState["step1"]>) => void;
}) {
  return (
    <div className="space-y-5">
      <h2 className="font-display text-xl font-bold">About the home</h2>

      <div>
        <Label htmlFor="address">Street address</Label>
        <Input
          id="address"
          autoComplete="street-address"
          placeholder="123 Maple Ave"
          value={value.address ?? ""}
          onChange={(e) => onChange({ address: e.target.value })}
          aria-invalid={!!errors.address}
          aria-describedby={errors.address ? "err-address" : undefined}
        />
        <FieldError id="err-address" msg={errors.address} />
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <div>
          <Label>City</Label>
          <RadioGroup
            className="mt-2 grid grid-cols-2 gap-2"
            value={value.city ?? ""}
            onValueChange={(v) => onChange({ city: v as (typeof cities)[number] })}
          >
            {cities.map((c) => (
              <label
                key={c}
                className={cn(
                  "flex cursor-pointer items-center gap-2 rounded-xl border border-border bg-background px-3 py-2 text-sm transition-colors hover:border-accent/60",
                  value.city === c && "border-accent bg-accent/5",
                )}
              >
                <RadioGroupItem value={c} />
                {c}
              </label>
            ))}
          </RadioGroup>
          <FieldError id="err-city" msg={errors.city} />
        </div>

        <div>
          <Label htmlFor="postal">Postal code</Label>
          <Input
            id="postal"
            autoComplete="postal-code"
            placeholder="L6H 1A1"
            value={value.postalCode ?? ""}
            onChange={(e) => onChange({ postalCode: e.target.value.toUpperCase() })}
            aria-invalid={!!errors.postalCode}
            aria-describedby={errors.postalCode ? "err-postal" : undefined}
          />
          <FieldError id="err-postal" msg={errors.postalCode} />
        </div>
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <div>
          <Label htmlFor="year">
            Year built <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id="year"
            inputMode="numeric"
            placeholder="1998"
            maxLength={4}
            value={value.yearBuilt ?? ""}
            onChange={(e) => onChange({ yearBuilt: e.target.value })}
            aria-invalid={!!errors.yearBuilt}
            aria-describedby={errors.yearBuilt ? "err-year" : undefined}
          />
          <FieldError id="err-year" msg={errors.yearBuilt} />
        </div>

        <div>
          <Label>
            Square footage <span className="text-muted-foreground">(optional)</span>
          </Label>
          <div className="mt-2 flex flex-wrap gap-2">
            {sqftRanges.map((r) => {
              const selected = value.sqft === r;
              return (
                <button
                  key={r}
                  type="button"
                  onClick={() => onChange({ sqft: selected ? undefined : r })}
                  className={cn(
                    "rounded-full border border-border bg-background px-3 py-1.5 text-sm transition-colors hover:border-accent/60",
                    selected && "border-accent bg-accent/5 text-foreground",
                  )}
                >
                  {r}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div>
        <Label>Property type</Label>
        <RadioGroup
          className="mt-2 grid grid-cols-3 gap-2"
          value={value.propertyType ?? ""}
          onValueChange={(v) =>
            onChange({ propertyType: v as (typeof propertyTypes)[number] })
          }
        >
          {propertyTypes.map((p) => (
            <label
              key={p}
              className={cn(
                "flex cursor-pointer items-center gap-2 rounded-xl border border-border bg-background px-3 py-2 text-sm transition-colors hover:border-accent/60",
                value.propertyType === p && "border-accent bg-accent/5",
              )}
            >
              <RadioGroupItem value={p} />
              {p}
            </label>
          ))}
        </RadioGroup>
        <FieldError id="err-prop" msg={errors.propertyType} />
      </div>
    </div>
  );
}

function Step2({
  value,
  errors,
  onChange,
}: {
  value: FormState["step2"];
  errors: Record<string, string>;
  onChange: (p: Partial<FormState["step2"]>) => void;
}) {
  const weeks = useMemo(() => nextWeeks(4), []);
  const selectedDays = (value.days ?? []) as string[];

  function toggleDay(d: (typeof daysOfWeek)[number]) {
    const next = selectedDays.includes(d)
      ? selectedDays.filter((x) => x !== d)
      : [...selectedDays, d];
    onChange({ days: next as FormState["step2"]["days"] });
  }

  return (
    <div className="space-y-5">
      <h2 className="font-display text-xl font-bold">Pick a time</h2>

      <div>
        <Label>Preferred week</Label>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {weeks.map((w) => {
            const selected = value.weekStart === w.iso;
            return (
              <button
                key={w.iso}
                type="button"
                onClick={() => onChange({ weekStart: w.iso })}
                className={cn(
                  "rounded-xl border border-border bg-background px-3 py-3 text-left text-sm transition-colors hover:border-accent/60",
                  selected && "border-accent bg-accent/5",
                )}
              >
                <span className="block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Week of
                </span>
                <span className="mt-0.5 block font-medium">{w.label}</span>
              </button>
            );
          })}
        </div>
        <FieldError id="err-week" msg={errors.weekStart} />
      </div>

      <div>
        <Label>Time of day</Label>
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          {timeWindows.map((t) => {
            const selected = value.timeOfDay === t.id;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => onChange({ timeOfDay: t.id })}
                className={cn(
                  "rounded-xl border border-border bg-background px-3 py-3 text-left text-sm transition-colors hover:border-accent/60",
                  selected && "border-accent bg-accent/5",
                )}
              >
                <span className="block font-medium">{t.label}</span>
                <span className="mt-0.5 block text-xs text-muted-foreground">
                  {t.range}
                </span>
              </button>
            );
          })}
        </div>
        <FieldError id="err-time" msg={errors.timeOfDay} />
      </div>

      <div>
        <Label>Day-of-week preference</Label>
        <div className="mt-2 flex flex-wrap gap-2">
          {daysOfWeek.map((d) => {
            const selected = selectedDays.includes(d);
            return (
              <button
                key={d}
                type="button"
                onClick={() => toggleDay(d)}
                aria-pressed={selected}
                className={cn(
                  "rounded-full border border-border bg-background px-4 py-1.5 text-sm transition-colors hover:border-accent/60",
                  selected && "border-accent bg-accent/5",
                )}
              >
                {d}
              </button>
            );
          })}
        </div>
        <FieldError id="err-days" msg={errors.days} />
      </div>

      <div>
        <Label htmlFor="notes">
          Anything we should know? <span className="text-muted-foreground">(optional)</span>
        </Label>
        <Textarea
          id="notes"
          rows={4}
          placeholder="Gate code, dogs, side entrance, things on your mind…"
          value={value.notes ?? ""}
          onChange={(e) => onChange({ notes: e.target.value })}
          maxLength={1000}
        />
      </div>
    </div>
  );
}

function Step3({
  value,
  errors,
  onChange,
}: {
  value: FormState["step3"];
  errors: Record<string, string>;
  onChange: (p: Partial<FormState["step3"]>) => void;
}) {
  return (
    <div className="space-y-5">
      <h2 className="font-display text-xl font-bold">Contact details</h2>

      <div>
        <Label htmlFor="name">Full name</Label>
        <Input
          id="name"
          autoComplete="name"
          value={value.fullName ?? ""}
          onChange={(e) => onChange({ fullName: e.target.value })}
          aria-invalid={!!errors.fullName}
          aria-describedby={errors.fullName ? "err-name" : undefined}
        />
        <FieldError id="err-name" msg={errors.fullName} />
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <div>
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={value.email ?? ""}
            onChange={(e) => onChange({ email: e.target.value })}
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? "err-email" : undefined}
          />
          <FieldError id="err-email" msg={errors.email} />
        </div>
        <div>
          <Label htmlFor="phone">Phone</Label>
          <Input
            id="phone"
            type="tel"
            autoComplete="tel"
            placeholder="(905) 555-0142"
            value={value.phone ?? ""}
            onChange={(e) => onChange({ phone: e.target.value })}
            aria-invalid={!!errors.phone}
            aria-describedby={errors.phone ? "err-phone" : undefined}
          />
          <FieldError id="err-phone" msg={errors.phone} />
        </div>
      </div>

      <label className="flex items-start gap-3 rounded-xl border border-border bg-surface/60 p-4">
        <Checkbox
          id="consent"
          checked={!!value.consent}
          onCheckedChange={(c) => onChange({ consent: (c === true) as true })}
          aria-describedby={errors.consent ? "err-consent" : undefined}
        />
        <span className="text-sm text-foreground/90">
          It's okay to contact me about scheduling this walk-through and the maintenance plan that follows. No marketing spam.
        </span>
      </label>
      <FieldError id="err-consent" msg={errors.consent} />
    </div>
  );
}

// ---------- Aside ----------
function Aside() {
  const items = [
    {
      icon: Home,
      title: "Inside walk",
      body: "We catalog HVAC, plumbing, smoke detectors, and the everyday systems homeowners forget about.",
    },
    {
      icon: Sun,
      title: "Exterior check",
      body: "Gutters, downspouts, weather seals, the roof line from the ground — a seasonal once-over.",
    },
    {
      icon: ClipboardCheck,
      title: "Written plan",
      body: "A custom 12-month maintenance schedule emailed the next day. No phone tag, no pressure.",
    },
  ];
  return (
    <aside className="lg:sticky lg:top-28 lg:self-start">
      <div className="rounded-3xl border border-border bg-surface p-6 md:p-7">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">
          What to expect
        </p>
        <h2 className="mt-2 font-display text-2xl font-bold">
          90 minutes. Then a plan.
        </h2>
        <ul className="mt-5 space-y-5">
          {items.map((it) => (
            <li key={it.title} className="flex gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-background text-accent">
                <it.icon className="size-4" />
              </span>
              <div>
                <p className="font-semibold">{it.title}</p>
                <p className="mt-1 text-sm text-muted-foreground">{it.body}</p>
              </div>
            </li>
          ))}
        </ul>
        <p className="mt-6 border-t border-border pt-5 text-sm text-muted-foreground">
          The walk-through is genuinely free. There's no sales pitch at the door — just an honest read on your home and a plan you can use whether you subscribe or not.
        </p>
      </div>
    </aside>
  );
}

// ---------- Confirmation ----------
function Confirmation({
  data,
}: {
  data: {
    step1: z.infer<typeof step1Schema>;
    step2: z.infer<typeof step2Schema>;
    step3: z.infer<typeof step3Schema>;
  };
}) {
  function downloadIcs() {
    const start = new Date(data.step2.weekStart + "T00:00:00");
    // pick first selected day in week, default Monday
    const dayIndex: Record<string, number> = {
      Mon: 0,
      Tue: 1,
      Wed: 2,
      Thu: 3,
      Fri: 4,
      Sat: 5,
      Sun: 6,
    };
    const firstDay = [...data.step2.days].sort(
      (a, b) => dayIndex[a] - dayIndex[b],
    )[0];
    start.setDate(start.getDate() + (dayIndex[firstDay] ?? 0));
    const hour =
      data.step2.timeOfDay === "morning"
        ? 9
        : data.step2.timeOfDay === "afternoon"
          ? 13
          : 17;
    start.setHours(hour, 0, 0, 0);
    const end = new Date(start);
    end.setMinutes(end.getMinutes() + 90);

    const fmt = (d: Date) =>
      d.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");

    const ics = [
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//HomeKept//Walk-through//EN",
      "BEGIN:VEVENT",
      `UID:${Date.now()}@homekept.ca`,
      `DTSTAMP:${fmt(new Date())}`,
      `DTSTART:${fmt(start)}`,
      `DTEND:${fmt(end)}`,
      "SUMMARY:HomeKept walk-through (tentative)",
      `DESCRIPTION:90-minute home assessment. We'll confirm the exact time by email.`,
      `LOCATION:${data.step1.address}\\, ${data.step1.city}`,
      "END:VEVENT",
      "END:VCALENDAR",
    ].join("\r\n");

    const blob = new Blob([ics], { type: "text/calendar" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "homekept-walkthrough.ics";
    a.click();
    URL.revokeObjectURL(url);
  }

  const weekLabel = formatWeek(new Date(data.step2.weekStart + "T00:00:00"));
  const timeLabel =
    timeWindows.find((t) => t.id === data.step2.timeOfDay)?.label +
    " (" +
    timeWindows.find((t) => t.id === data.step2.timeOfDay)?.range +
    ")";

  return (
    <div className="mx-auto max-w-2xl">
      <div className="flex size-14 items-center justify-center rounded-full bg-accent/10 text-accent">
        <Check className="size-7" />
      </div>
      <h1 className="mt-5 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
        You're booked. Sort of.
      </h1>
      <p className="mt-3 text-muted-foreground">
        We'll email <span className="font-medium text-foreground">{data.step3.email}</span> within one business day to confirm an exact time that works for both of us.
      </p>

      <div className="mt-8 rounded-3xl border border-border bg-card p-6 shadow-soft md:p-8">
        <h2 className="font-display text-lg font-bold">Walk-through details</h2>
        <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Address</dt>
            <dd className="mt-1 font-medium">
              {data.step1.address}
              <br />
              {data.step1.city}, {data.step1.postalCode}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Property</dt>
            <dd className="mt-1 font-medium">
              {data.step1.propertyType}
              {data.step1.sqft ? ` · ${data.step1.sqft} sq ft` : ""}
              {data.step1.yearBuilt ? ` · built ${data.step1.yearBuilt}` : ""}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Preferred week</dt>
            <dd className="mt-1 font-medium">{weekLabel}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Time of day</dt>
            <dd className="mt-1 font-medium">{timeLabel}</dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-muted-foreground">Days that work</dt>
            <dd className="mt-1 font-medium">{data.step2.days.join(", ")}</dd>
          </div>
          {data.step2.notes && (
            <div className="sm:col-span-2">
              <dt className="text-muted-foreground">Notes</dt>
              <dd className="mt-1 whitespace-pre-wrap">{data.step2.notes}</dd>
            </div>
          )}
        </dl>

        <div className="mt-6 flex flex-col gap-3 border-t border-border pt-6 sm:flex-row">
          <Button onClick={downloadIcs} variant="accent">
            <Mail className="size-4" />
            Add to calendar
          </Button>
          <Button asChild variant="outline">
            <Link to="/">Back home</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
