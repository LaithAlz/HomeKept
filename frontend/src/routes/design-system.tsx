import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { ArrowRight, Loader2, Bell, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Card } from "@/components/ui/card";
import { Wordmark } from "@/components/brand/Wordmark";
import { StatusPill, type StatusKind } from "@/components/brand/StatusPill";

export const Route = createFileRoute("/design-system")({
  head: () => ({
    meta: [
      { title: "Design system — HomeKept" },
      { name: "description", content: "HomeKept's foundational design system." },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: DesignSystemPage,
});

const swatches = [
  { name: "Background / Paper", token: "bg-background", text: "text-foreground" },
  { name: "Surface / Sage", token: "bg-surface", text: "text-foreground" },
  { name: "Card", token: "bg-card", text: "text-card-foreground" },
  { name: "Primary / Forest", token: "bg-primary", text: "text-primary-foreground" },
  { name: "Accent / Clay", token: "bg-accent", text: "text-accent-foreground" },
  { name: "Muted", token: "bg-muted", text: "text-muted-foreground" },
  { name: "Success", token: "bg-success", text: "text-success-foreground" },
  { name: "Warning", token: "bg-warning", text: "text-warning-foreground" },
  { name: "Info", token: "bg-info", text: "text-info-foreground" },
  { name: "Destructive", token: "bg-destructive", text: "text-destructive-foreground" },
];

const statuses: StatusKind[] = ["active", "paused", "at-risk", "payment-issue"];

function DesignSystemPage() {
  const [agree, setAgree] = useState(false);
  const [notify, setNotify] = useState(true);

  return (
    <div className="min-h-dvh bg-background">
      <header className="border-b border-border bg-background/80 backdrop-blur sticky top-0 z-40">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
          <div className="flex items-center gap-4">
            <Wordmark size="md" />
            <span className="rounded-full bg-surface px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
              Design system
            </span>
          </div>
          <a
            href="/"
            className="text-sm font-medium text-muted-foreground hover:text-accent"
          >
            ← Back to site
          </a>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-7xl space-y-24 px-6 py-16">
        <Section title="01 — Wordmark" eyebrow="Brand">
          <div className="grid items-end gap-10 rounded-3xl border border-border bg-card p-10 md:grid-cols-4">
            <div className="space-y-2">
              <Wordmark size="sm" />
              <Caption>Small · 18px</Caption>
            </div>
            <div className="space-y-2">
              <Wordmark size="md" />
              <Caption>Medium · 24px</Caption>
            </div>
            <div className="space-y-2">
              <Wordmark size="lg" />
              <Caption>Large · 36px</Caption>
            </div>
            <div className="space-y-2">
              <Wordmark size="xl" />
              <Caption>Display · 60–72px</Caption>
            </div>
          </div>
          <div className="mt-4 grid gap-4 sm:grid-cols-4">
            <FaviconTile size={16} />
            <FaviconTile size={32} />
            <FaviconTile size={64} />
            <FaviconTile size={128} />
          </div>
        </Section>

        <Section title="02 — Color" eyebrow="Tokens">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {swatches.map((s) => (
              <div
                key={s.name}
                className={`${s.token} ${s.text} flex aspect-square flex-col justify-between rounded-2xl border border-border p-4`}
              >
                <span className="text-[11px] font-bold uppercase tracking-wider opacity-80">
                  {s.name}
                </span>
                <span className="font-mono text-xs opacity-80">{s.token}</span>
              </div>
            ))}
          </div>
        </Section>

        <Section title="03 — Typography" eyebrow="Type scale">
          <div className="space-y-6 rounded-3xl border border-border bg-card p-10">
            <TypeRow label="Display · 800" className="font-display text-7xl font-extrabold tracking-tight">
              Home maintenance.
            </TypeRow>
            <TypeRow label="H1 · 800" className="font-display text-5xl font-extrabold tracking-tight">
              Section heading
            </TypeRow>
            <TypeRow label="H2 · 700" className="font-display text-3xl font-bold tracking-tight">
              Subsection heading
            </TypeRow>
            <TypeRow label="H3 · 700" className="font-display text-xl font-bold">
              Card heading
            </TypeRow>
            <TypeRow label="Body · 400" className="text-base text-foreground">
              We schedule the visits, send vetted technicians, and report back with photos.
            </TypeRow>
            <TypeRow label="Small · 400" className="text-sm text-muted-foreground">
              Insured & bonded. Background-checked technicians.
            </TypeRow>
            <TypeRow label="Label · 700 uppercase" className="text-[11px] font-bold uppercase tracking-[0.18em] text-accent">
              How it works
            </TypeRow>
            <TypeRow label="Mono · 500" className="font-mono text-sm text-foreground">
              $189.00 CAD / mo
            </TypeRow>
          </div>
        </Section>

        <Section title="04 — Buttons" eyebrow="Components">
          <div className="space-y-8 rounded-3xl border border-border bg-card p-10">
            <Row label="Variants">
              <Button>Primary</Button>
              <Button variant="accent">Accent</Button>
              <Button variant="outline">Outline</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="destructive">Destructive</Button>
              <Button variant="link">Link style</Button>
            </Row>
            <Row label="Sizes">
              <Button size="sm">Small</Button>
              <Button>Default</Button>
              <Button size="lg">Large</Button>
              <Button size="xl">
                Book walk-through <ArrowRight />
              </Button>
              <Button size="icon" aria-label="Add">
                <Plus />
              </Button>
            </Row>
            <Row label="States">
              <Button>
                <ArrowRight /> With icon
              </Button>
              <Button disabled>
                <Loader2 className="animate-spin" /> Loading
              </Button>
              <Button disabled>Disabled</Button>
            </Row>
          </div>
        </Section>

        <Section title="05 — Forms" eyebrow="Components">
          <div className="grid gap-6 rounded-3xl border border-border bg-card p-10 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="ds-email">Email</Label>
              <Input id="ds-email" type="email" placeholder="you@example.com" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-postal">Postal code</Label>
              <Input id="ds-postal" placeholder="L6H 0A1" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-error">With error</Label>
              <Input
                id="ds-error"
                aria-invalid
                defaultValue="bad@"
                className="border-destructive focus-visible:ring-destructive"
              />
              <p className="text-xs text-destructive">Enter a valid email address.</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-disabled">Disabled</Label>
              <Input id="ds-disabled" disabled defaultValue="Not editable" />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="ds-notes">Notes for your walk-through</Label>
              <Textarea
                id="ds-notes"
                rows={4}
                placeholder="Anything we should know? E.g. dog on premises, side gate code..."
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-city">City</Label>
              <Select>
                <SelectTrigger id="ds-city">
                  <SelectValue placeholder="Select a city" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="oakville">Oakville</SelectItem>
                  <SelectItem value="mississauga">Mississauga</SelectItem>
                  <SelectItem value="milton">Milton</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <fieldset className="space-y-3">
              <legend className="text-sm font-medium">Preferred plan</legend>
              <RadioGroup defaultValue="complete" className="space-y-2">
                {(["essential", "complete", "premier"] as const).map((id) => (
                  <div key={id} className="flex items-center gap-2">
                    <RadioGroupItem id={`ds-${id}`} value={id} />
                    <Label htmlFor={`ds-${id}`} className="capitalize">
                      {id}
                    </Label>
                  </div>
                ))}
              </RadioGroup>
            </fieldset>
            <div className="flex items-center gap-3">
              <Checkbox
                id="ds-agree"
                checked={agree}
                onCheckedChange={(v) => setAgree(v === true)}
              />
              <Label htmlFor="ds-agree">I agree to be contacted by HomeKept</Label>
            </div>
            <div className="flex items-center gap-3">
              <Switch id="ds-notify" checked={notify} onCheckedChange={setNotify} />
              <Label htmlFor="ds-notify" className="flex items-center gap-2">
                <Bell className="size-4 text-muted-foreground" /> Email reminders before visits
              </Label>
            </div>
          </div>
        </Section>

        <Section title="06 — Cards" eyebrow="Components">
          <div className="grid gap-6 md:grid-cols-3">
            <Card className="rounded-3xl border-border p-6">
              <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Default card
              </p>
              <h3 className="mt-2 font-display text-xl font-bold">Next visit</h3>
              <p className="mt-2 text-sm text-muted-foreground">
                Thursday, Nov 14 · 10:00 AM
              </p>
            </Card>

            <div className="relative rounded-3xl bg-primary p-6 text-primary-foreground shadow-lift ring-8 ring-surface">
              <span className="absolute -top-3 left-6 rounded-full bg-accent px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-accent-foreground">
                Featured
              </span>
              <p className="text-xs font-bold uppercase tracking-wider text-primary-foreground/70">
                Featured card
              </p>
              <h3 className="mt-2 font-display text-xl font-bold">Complete plan</h3>
              <p className="mt-2 text-sm text-primary-foreground/80">
                Monthly visits, year-round coverage.
              </p>
            </div>

            <div className="rounded-3xl border border-border bg-card p-6">
              <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Stat card
              </p>
              <p className="mt-3 font-display text-5xl font-extrabold tabular-nums tracking-tight">
                98
                <span className="text-xl text-muted-foreground">/100</span>
              </p>
              <p className="mt-1 text-sm text-muted-foreground">Home health score</p>
            </div>
          </div>
        </Section>

        <Section title="07 — Status pills" eyebrow="Components">
          <div className="flex flex-wrap gap-3 rounded-3xl border border-border bg-card p-10">
            {statuses.map((s) => (
              <StatusPill key={s} status={s} />
            ))}
          </div>
        </Section>

        <Section title="08 — Navigation patterns" eyebrow="Layout">
          <div className="space-y-4">
            <Caption>Marketing top nav</Caption>
            <div className="overflow-hidden rounded-3xl border border-border">
              <div className="flex h-16 items-center justify-between bg-background px-6">
                <Wordmark size="md" />
                <div className="hidden items-center gap-6 md:flex">
                  <span className="text-sm font-medium text-muted-foreground">How it works</span>
                  <span className="text-sm font-medium text-muted-foreground">Plans</span>
                  <span className="text-sm font-medium text-muted-foreground">Sign in</span>
                  <Button size="sm">Book walk-through</Button>
                </div>
              </div>
            </div>

            <Caption>In-app sidebar (subscriber)</Caption>
            <div className="overflow-hidden rounded-3xl border border-border">
              <div className="flex">
                <aside className="hidden w-56 flex-col gap-1 bg-surface p-4 md:flex">
                  {["Overview", "Visits", "Reports", "Billing", "Settings"].map((l, i) => (
                    <div
                      key={l}
                      className={
                        "rounded-xl px-3 py-2 text-sm font-medium " +
                        (i === 0
                          ? "bg-primary text-primary-foreground"
                          : "text-foreground/70 hover:bg-card")
                      }
                    >
                      {l}
                    </div>
                  ))}
                </aside>
                <div className="flex-1 bg-background p-8">
                  <p className="text-xs font-bold uppercase tracking-wider text-accent">Overview</p>
                  <h3 className="mt-2 font-display text-2xl font-bold">Welcome back, Sarah.</h3>
                </div>
              </div>
            </div>

            <Caption>Mobile bottom nav (technician PWA)</Caption>
            <div className="mx-auto max-w-sm overflow-hidden rounded-3xl border border-border bg-card">
              <div className="flex items-center justify-around p-3">
                {["Today", "Routes", "Reports", "Me"].map((l, i) => (
                  <div
                    key={l}
                    className={
                      "rounded-xl px-4 py-2 text-xs font-bold " +
                      (i === 0
                        ? "bg-accent text-accent-foreground"
                        : "text-muted-foreground")
                    }
                  >
                    {l}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Section>
      </main>
    </div>
  );
}

/* ------- helpers ------- */

function Section({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow: string;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-6">
      <div>
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-accent">{eyebrow}</p>
        <h2 className="mt-2 font-display text-3xl font-extrabold tracking-tight">{title}</h2>
      </div>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <Caption className="mb-3">{label}</Caption>
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

function TypeRow({
  label,
  className,
  children,
}: {
  label: string;
  className: string;
  children: React.ReactNode;
}) {
  return (
    <div className="grid items-baseline gap-3 border-b border-border pb-6 last:border-0 last:pb-0 md:grid-cols-[160px_1fr]">
      <Caption>{label}</Caption>
      <div className={className}>{children}</div>
    </div>
  );
}

function Caption({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span
      className={
        "block text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground " + className
      }
    >
      {children}
    </span>
  );
}

function FaviconTile({ size }: { size: number }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-2xl border border-border bg-card p-6">
      <div
        className="grid place-items-center rounded-md bg-primary text-primary-foreground"
        style={{ width: size, height: size }}
      >
        <span
          className="font-display font-extrabold leading-none"
          style={{ fontSize: Math.max(8, size * 0.55) }}
        >
          H<span className="text-accent">.</span>
        </span>
      </div>
      <Caption>{size}px</Caption>
    </div>
  );
}
