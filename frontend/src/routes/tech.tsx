import { useEffect, useMemo, useRef, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import {
  Camera,
  StickyNote,
  CheckCircle2,
  Circle,
  ChevronDown,
  ChevronUp,
  Clock,
  MapPin,
  Menu,
  Loader2,
  CheckCheck,
  Sparkles,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/tech")({
  head: () => ({
    meta: [
      { title: "Today — HomeKept Tech" },
      { name: "robots", content: "noindex" },
      { name: "viewport", content: "width=device-width, initial-scale=1, viewport-fit=cover" },
      { name: "theme-color", content: "#1a3c2a" },
    ],
  }),
  component: TechApp,
});

// ============================================================================
// Types & mock data
// ============================================================================

type Status = "in-progress" | "upcoming" | "done";
type PlanTier = "Essential" | "Complete" | "Premier";

interface ChecklistItem {
  id: string;
  label: string;
  done: boolean;
}

interface Visit {
  id: string;
  customer: string;
  address: string;
  city: string;
  start: string; // "10:00 AM"
  durationMin: number;
  plan: PlanTier;
  status: Status;
  checklist: ChecklistItem[];
  photos: string[]; // object URLs
  notes: string[];
}

const initialVisits: Visit[] = [
  {
    id: "v1",
    customer: "Priya Sharma",
    address: "14 Maple Ridge Crt, Erin Mills",
    city: "Mississauga",
    start: "10:00 AM",
    durationMin: 75,
    plan: "Complete",
    status: "in-progress",
    photos: [],
    notes: [],
    checklist: [
      { id: "c1", label: "Furnace filter swap", done: true },
      { id: "c2", label: "Smoke + CO detector test", done: true },
      { id: "c3", label: "AC startup check", done: false },
      { id: "c4", label: "Gutter clearing — front", done: false },
      { id: "c5", label: "Hose bib reconnection", done: false },
    ],
  },
  {
    id: "v2",
    customer: "Mark & Helen Chen",
    address: "27 Bronte Creek Dr",
    city: "Oakville",
    start: "12:30 PM",
    durationMin: 90,
    plan: "Premier",
    status: "upcoming",
    photos: [],
    notes: [],
    checklist: [
      { id: "c1", label: "Quarterly home walkaround", done: false },
      { id: "c2", label: "Dryer vent inspection", done: false },
      { id: "c3", label: "Sump pump test", done: false },
      { id: "c4", label: "Smart thermostat tune-up", done: false },
    ],
  },
  {
    id: "v3",
    customer: "Daniel Nguyen",
    address: "8 Whitehorn Pl",
    city: "Milton",
    start: "3:00 PM",
    durationMin: 60,
    plan: "Essential",
    status: "upcoming",
    photos: [],
    notes: [],
    checklist: [
      { id: "c1", label: "First-visit home assessment", done: false },
      { id: "c2", label: "Filter & detector inventory", done: false },
      { id: "c3", label: "Seasonal exterior check", done: false },
    ],
  },
];

const STORAGE_KEY = "homekept:tech:visits-v1";

// ============================================================================
// App shell
// ============================================================================

function TechApp() {
  return (
    <div className="min-h-dvh bg-background">
      {/* Phone frame on wider screens, full-bleed on phones */}
      <div className="mx-auto w-full max-w-[420px] md:my-8 md:overflow-hidden md:rounded-[2.5rem] md:border md:border-border md:shadow-2xl">
        <TechShell />
      </div>
    </div>
  );
}

function TechShell() {
  const [visits, setVisits] = useState<Visit[]>(() => loadVisits());
  const [expandedId, setExpandedId] = useState<string>(() => {
    const current = (loadVisits().find((v) => v.status === "in-progress") ??
      loadVisits().find((v) => v.status === "upcoming"));
    return current?.id ?? "";
  });
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [noteFor, setNoteFor] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  // Persist locally — works offline by default.
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stripPhotos(visits)));
    } catch {
      // ignore quota
    }
  }, [visits]);

  const inProgress = visits.filter((v) => v.status === "in-progress");
  const upcoming = visits.filter((v) => v.status === "upcoming");
  const done = visits.filter((v) => v.status === "done");

  const totalCount = visits.length;
  const doneCount = done.length;
  const totalHours = useMemo(
    () => Math.round((visits.reduce((s, v) => s + v.durationMin, 0) / 60) * 10) / 10,
    [visits],
  );
  const routeSummary = useMemo(() => {
    const seen = new Set<string>();
    const ordered: string[] = [];
    for (const v of visits) {
      if (!seen.has(v.city)) {
        seen.add(v.city);
        ordered.push(v.city);
      }
    }
    return ordered.join(" → ");
  }, [visits]);

  const today = new Date().toLocaleDateString("en-CA", {
    weekday: "long",
    month: "short",
    day: "numeric",
  });

  function toggleItem(visitId: string, itemId: string) {
    setVisits((prev) =>
      prev.map((v) =>
        v.id === visitId
          ? {
              ...v,
              checklist: v.checklist.map((c) =>
                c.id === itemId ? { ...c, done: !c.done } : c,
              ),
            }
          : v,
      ),
    );
  }

  function addPhoto(visitId: string, file: File) {
    const url = URL.createObjectURL(file);
    setVisits((prev) =>
      prev.map((v) =>
        v.id === visitId ? { ...v, photos: [...v.photos, url] } : v,
      ),
    );
  }

  function addNote(visitId: string, text: string) {
    setVisits((prev) =>
      prev.map((v) =>
        v.id === visitId ? { ...v, notes: [...v.notes, text] } : v,
      ),
    );
  }

  function completeVisit(visitId: string) {
    setVisits((prev) => {
      const next = prev.map((v) =>
        v.id === visitId
          ? {
              ...v,
              status: "done" as Status,
              checklist: v.checklist.map((c) => ({ ...c, done: true })),
            }
          : v,
      );
      // auto-expand next upcoming and promote to in-progress
      const nextUp = next.find((v) => v.status === "upcoming");
      if (nextUp) {
        setExpandedId(nextUp.id);
        return next.map((v) =>
          v.id === nextUp.id ? { ...v, status: "in-progress" } : v,
        );
      }
      setExpandedId("");
      return next;
    });
    setConfirmId(null);
  }

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground [padding-top:env(safe-area-inset-top)]">
      {/* Header */}
      <header className="sticky top-0 z-30 border-b border-border bg-background/95 px-5 py-4 backdrop-blur">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              {today}
            </p>
            <h1 className="mt-0.5 font-display text-2xl font-extrabold tracking-tight">
              Hey, Marcus.
            </h1>
          </div>
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="Open menu"
            className="inline-flex size-11 items-center justify-center rounded-full border border-border bg-card text-foreground active:scale-95"
          >
            <Menu className="size-5" />
          </button>
        </div>
      </header>

      <main className="flex-1 space-y-4 px-4 pb-32 pt-4">
        {/* Today summary */}
        <TodaySummary
          totalCount={totalCount}
          doneCount={doneCount}
          totalHours={totalHours}
          route={routeSummary}
        />

        {/* In progress */}
        {inProgress.length > 0 && (
          <Group label="In progress" tone="primary">
            {inProgress.map((v) => (
              <VisitCard
                key={v.id}
                visit={v}
                expanded={expandedId === v.id}
                onToggleExpand={() =>
                  setExpandedId((id) => (id === v.id ? "" : v.id))
                }
                onToggleItem={(itemId) => toggleItem(v.id, itemId)}
                onAddPhoto={(f) => addPhoto(v.id, f)}
                onAddNote={() => setNoteFor(v.id)}
                onComplete={() => setConfirmId(v.id)}
              />
            ))}
          </Group>
        )}

        {/* Upcoming */}
        {upcoming.length > 0 && (
          <Group label="Up next" tone="muted">
            {upcoming.map((v) => (
              <VisitCard
                key={v.id}
                visit={v}
                expanded={expandedId === v.id}
                onToggleExpand={() =>
                  setExpandedId((id) => (id === v.id ? "" : v.id))
                }
                onToggleItem={(itemId) => toggleItem(v.id, itemId)}
                onAddPhoto={(f) => addPhoto(v.id, f)}
                onAddNote={() => setNoteFor(v.id)}
                onComplete={() => setConfirmId(v.id)}
              />
            ))}
          </Group>
        )}

        {/* Done */}
        {done.length > 0 && (
          <Group label="Done" tone="dim">
            {done.map((v) => (
              <VisitCard
                key={v.id}
                visit={v}
                dim
                expanded={expandedId === v.id}
                onToggleExpand={() =>
                  setExpandedId((id) => (id === v.id ? "" : v.id))
                }
                onToggleItem={(itemId) => toggleItem(v.id, itemId)}
                onAddPhoto={(f) => addPhoto(v.id, f)}
                onAddNote={() => setNoteFor(v.id)}
                onComplete={() => setConfirmId(v.id)}
              />
            ))}
          </Group>
        )}

        {done.length === totalCount && (
          <div className="mt-6 rounded-3xl border border-border bg-card p-6 text-center">
            <Sparkles className="mx-auto size-6 text-accent" />
            <p className="mt-2 font-display text-lg font-bold">Day complete.</p>
            <p className="text-sm text-muted-foreground">
              Reports queued. Drive safe.
            </p>
          </div>
        )}
      </main>

      {/* Bottom safe-area spacer */}
      <div
        aria-hidden="true"
        className="pointer-events-none h-[env(safe-area-inset-bottom)]"
      />

      {/* Modals */}
      {confirmId && (
        <ConfirmDialog
          visit={visits.find((v) => v.id === confirmId)!}
          onCancel={() => setConfirmId(null)}
          onConfirm={() => completeVisit(confirmId)}
        />
      )}
      {noteFor && (
        <NoteSheet
          visit={visits.find((v) => v.id === noteFor)!}
          onCancel={() => setNoteFor(null)}
          onSave={(text) => {
            addNote(noteFor, text);
            setNoteFor(null);
          }}
        />
      )}
      {menuOpen && <MenuSheet onClose={() => setMenuOpen(false)} />}
    </div>
  );
}

// ============================================================================
// Today summary
// ============================================================================

function TodaySummary({
  totalCount,
  doneCount,
  totalHours,
  route,
}: {
  totalCount: number;
  doneCount: number;
  totalHours: number;
  route: string;
}) {
  const pct = totalCount === 0 ? 0 : Math.round((doneCount / totalCount) * 100);
  return (
    <section
      aria-labelledby="today-label"
      className="rounded-3xl bg-primary p-5 text-primary-foreground shadow-lg"
    >
      <div className="flex items-center justify-between">
        <p
          id="today-label"
          className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary-foreground/70"
        >
          Today
        </p>
        <span className="rounded-full bg-primary-foreground/10 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-primary-foreground">
          {doneCount}/{totalCount} done
        </span>
      </div>

      <div className="mt-3 flex items-end gap-4">
        <div>
          <div className="font-display text-5xl font-extrabold leading-none tracking-tight">
            {totalCount}
          </div>
          <div className="mt-1 text-xs text-primary-foreground/70">
            visit{totalCount === 1 ? "" : "s"}
          </div>
        </div>
        <div className="ml-2 h-10 w-px bg-primary-foreground/20" />
        <div>
          <div className="font-display text-2xl font-bold leading-none">
            {totalHours}h
          </div>
          <div className="mt-1 text-xs text-primary-foreground/70">est.</div>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-2 text-sm text-primary-foreground/85">
        <MapPin className="size-4" aria-hidden="true" />
        <span className="truncate">{route}</span>
      </div>

      <div className="mt-4">
        <div
          className="h-1.5 w-full overflow-hidden rounded-full bg-primary-foreground/15"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={pct}
          aria-label="Day progress"
        >
          <div
            className="h-full rounded-full bg-accent transition-all duration-500"
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>
    </section>
  );
}

// ============================================================================
// Group + visit card
// ============================================================================

function Group({
  label,
  tone,
  children,
}: {
  label: string;
  tone: "primary" | "muted" | "dim";
  children: React.ReactNode;
}) {
  return (
    <section className="mt-2">
      <h2
        className={cn(
          "px-1 text-[11px] font-bold uppercase tracking-[0.18em]",
          tone === "primary" && "text-accent",
          tone === "muted" && "text-foreground/70",
          tone === "dim" && "text-muted-foreground",
        )}
      >
        {label}
      </h2>
      <div className="mt-2 space-y-3">{children}</div>
    </section>
  );
}

function VisitCard({
  visit,
  expanded,
  dim,
  onToggleExpand,
  onToggleItem,
  onAddPhoto,
  onAddNote,
  onComplete,
}: {
  visit: Visit;
  expanded: boolean;
  dim?: boolean;
  onToggleExpand: () => void;
  onToggleItem: (id: string) => void;
  onAddPhoto: (f: File) => void;
  onAddNote: () => void;
  onComplete: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const completedCount = visit.checklist.filter((c) => c.done).length;

  return (
    <article
      className={cn(
        "overflow-hidden rounded-3xl border bg-card shadow-sm transition-opacity",
        visit.status === "in-progress" && "border-accent ring-1 ring-accent/40",
        visit.status !== "in-progress" && "border-border",
        dim && "opacity-70",
      )}
    >
      <button
        type="button"
        onClick={onToggleExpand}
        aria-expanded={expanded}
        aria-controls={`visit-body-${visit.id}`}
        className="w-full px-4 py-4 text-left active:bg-surface/60"
      >
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-bold">
            <Clock className="size-4 text-muted-foreground" aria-hidden="true" />
            {visit.start}
          </div>
          <StatusPill status={visit.status} />
        </div>

        <div className="mt-2 flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="truncate font-display text-lg font-bold tracking-tight">
              {visit.customer}
            </h3>
            <p className="mt-0.5 truncate text-sm text-muted-foreground">
              {visit.address} · {visit.city}
            </p>
          </div>
          <span
            aria-hidden="true"
            className="mt-1 inline-flex size-6 shrink-0 items-center justify-center rounded-full text-muted-foreground"
          >
            {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
          </span>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          <span>{visit.durationMin} min</span>
          <span aria-hidden="true">·</span>
          <span>
            {completedCount}/{visit.checklist.length} items
          </span>
          <span aria-hidden="true">·</span>
          <PlanChip plan={visit.plan} />
        </div>
      </button>

      {expanded && (
        <div
          id={`visit-body-${visit.id}`}
          className="border-t border-border px-4 pb-4 pt-3"
        >
          <ul className="space-y-1">
            {visit.checklist.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => onToggleItem(c.id)}
                  aria-pressed={c.done}
                  className="flex w-full items-center gap-3 rounded-2xl px-2 py-3 text-left text-base active:bg-surface/70"
                >
                  {c.done ? (
                    <CheckCircle2
                      className="size-6 shrink-0 text-accent"
                      aria-hidden="true"
                    />
                  ) : (
                    <Circle
                      className="size-6 shrink-0 text-muted-foreground"
                      aria-hidden="true"
                    />
                  )}
                  <span
                    className={cn(
                      "min-w-0 flex-1",
                      c.done && "text-muted-foreground line-through",
                    )}
                  >
                    {c.label}
                  </span>
                </button>
              </li>
            ))}
          </ul>

          {visit.photos.length > 0 && (
            <div className="mt-4">
              <p className="mb-2 text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                Photos ({visit.photos.length})
              </p>
              <div className="grid grid-cols-4 gap-2">
                {visit.photos.map((src, i) => (
                  <img
                    key={i}
                    src={src}
                    alt=""
                    className="aspect-square w-full rounded-xl object-cover"
                  />
                ))}
              </div>
            </div>
          )}

          {visit.notes.length > 0 && (
            <div className="mt-4 space-y-2">
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                Notes ({visit.notes.length})
              </p>
              {visit.notes.map((n, i) => (
                <p
                  key={i}
                  className="rounded-2xl bg-surface px-3 py-2 text-sm text-foreground/90"
                >
                  {n}
                </p>
              ))}
            </div>
          )}

          {/* Action bar */}
          <div className="mt-5 grid grid-cols-3 gap-2">
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              capture="environment"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) onAddPhoto(f);
                e.target.value = "";
              }}
            />
            <Button
              type="button"
              size="lg"
              variant="outline"
              onClick={() => fileRef.current?.click()}
              className="h-14 flex-col gap-0.5 rounded-2xl text-xs"
            >
              <Camera className="size-5" />
              Photo
            </Button>
            <Button
              type="button"
              size="lg"
              variant="outline"
              onClick={onAddNote}
              className="h-14 flex-col gap-0.5 rounded-2xl text-xs"
            >
              <StickyNote className="size-5" />
              Note
            </Button>
            <Button
              type="button"
              size="lg"
              variant="accent"
              onClick={onComplete}
              disabled={visit.status === "done"}
              className="h-14 flex-col gap-0.5 rounded-2xl text-xs"
            >
              <CheckCheck className="size-5" />
              {visit.status === "done" ? "Done" : "Complete"}
            </Button>
          </div>
        </div>
      )}
    </article>
  );
}

// ============================================================================
// Pills
// ============================================================================

function StatusPill({ status }: { status: Status }) {
  const map = {
    "in-progress": {
      label: "In progress",
      cls: "bg-accent text-accent-foreground",
      icon: <Loader2 className="size-3 animate-spin" aria-hidden="true" />,
    },
    upcoming: {
      label: "Up next",
      cls: "bg-surface text-foreground border border-border",
      icon: <Clock className="size-3" aria-hidden="true" />,
    },
    done: {
      label: "Done",
      cls: "bg-primary/10 text-primary",
      icon: <CheckCheck className="size-3" aria-hidden="true" />,
    },
  } as const;
  const s = map[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider",
        s.cls,
      )}
    >
      {s.icon}
      {s.label}
    </span>
  );
}

function PlanChip({ plan }: { plan: PlanTier }) {
  const cls =
    plan === "Premier"
      ? "bg-primary text-primary-foreground"
      : plan === "Complete"
        ? "bg-accent/15 text-accent"
        : "bg-surface text-foreground border border-border";
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider",
        cls,
      )}
    >
      {plan}
    </span>
  );
}

// ============================================================================
// Modals
// ============================================================================

function ConfirmDialog({
  visit,
  onCancel,
  onConfirm,
}: {
  visit: Visit;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const remaining = visit.checklist.filter((c) => !c.done).length;
  return (
    <Overlay onClose={onCancel}>
      <div className="space-y-4">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">
            Complete this visit?
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {visit.customer} · {visit.address}
          </p>
          {remaining > 0 && (
            <p className="mt-3 rounded-2xl bg-surface px-3 py-2 text-sm text-foreground/90">
              {remaining} item{remaining === 1 ? "" : "s"} still unchecked. They'll
              be marked complete.
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-12 flex-1 rounded-2xl"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="accent"
            className="h-12 flex-1 rounded-2xl"
            onClick={onConfirm}
          >
            Complete
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

function NoteSheet({
  visit,
  onCancel,
  onSave,
}: {
  visit: Visit;
  onCancel: () => void;
  onSave: (text: string) => void;
}) {
  const [text, setText] = useState("");
  return (
    <Overlay onClose={onCancel}>
      <div className="space-y-3">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">
            Add a note
          </h3>
          <p className="mt-1 text-xs text-muted-foreground">
            For {visit.customer}'s file.
          </p>
        </div>
        <textarea
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={5}
          placeholder="Anything the office or next tech should know…"
          className="w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-12 flex-1 rounded-2xl"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button
            type="button"
            className="h-12 flex-1 rounded-2xl"
            onClick={() => text.trim() && onSave(text.trim())}
            disabled={!text.trim()}
          >
            Save
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

function MenuSheet({ onClose }: { onClose: () => void }) {
  return (
    <Overlay onClose={onClose}>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="font-display text-xl font-bold tracking-tight">
            Marcus T.
          </h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close menu"
            className="inline-flex size-10 items-center justify-center rounded-full hover:bg-surface"
          >
            <X className="size-5" />
          </button>
        </div>
        <p className="text-sm text-muted-foreground">
          HomeKept Technician · GTA route
        </p>
        <ul className="mt-2 divide-y divide-border rounded-2xl border border-border">
          {["My week", "Saved photos", "Help & contact", "Sign out"].map((l) => (
            <li
              key={l}
              className="px-4 py-3 text-sm font-medium text-foreground/90"
            >
              {l}
            </li>
          ))}
        </ul>
      </div>
    </Overlay>
  );
}

function Overlay({
  children,
  onClose,
}: {
  children: React.ReactNode;
  onClose: () => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-foreground/40 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[420px] rounded-t-3xl border-t border-border bg-card p-5 shadow-2xl [padding-bottom:calc(1.25rem+env(safe-area-inset-bottom))]"
      >
        {children}
      </div>
    </div>
  );
}

// ============================================================================
// Persistence helpers
// ============================================================================

function stripPhotos(visits: Visit[]): Visit[] {
  // Object URLs aren't persistable; drop them from storage.
  return visits.map((v) => ({ ...v, photos: [] }));
}

function loadVisits(): Visit[] {
  if (typeof window === "undefined") return initialVisits;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return initialVisits;
    const parsed = JSON.parse(raw) as Visit[];
    if (!Array.isArray(parsed) || parsed.length === 0) return initialVisits;
    return parsed;
  } catch {
    return initialVisits;
  }
}
