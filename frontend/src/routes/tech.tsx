import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate, useRouterState } from "@tanstack/react-router";
import {
  Camera,
  Flag as FlagIcon,
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
  PlayCircle,
  Lock,
  AlertTriangle,
  Ban,
  RefreshCw,
  ImageOff,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api";
import { getSession, logout, useSessionExpiredRedirect, type Session } from "@/lib/auth";
import { formatFullDate, formatTime, getCalendarParts } from "@/lib/format";
import {
  useCompleteVisit,
  useCreateFlag,
  useIncompleteVisit,
  usePatchService,
  usePatchTodo,
  useStartVisit,
  useTechDaySheet,
  useUploadPhoto,
  type FlagResponse,
  type FlagSeverity,
  type TechIncompleteVisitResponse,
  type TechPatchTodoRequest,
  type TechVisitListItem,
  type TodoResponse,
  type VisitServiceItem,
  type VisitStatus,
  type VisitType,
} from "@/lib/tech";

export const Route = createFileRoute("/tech")({
  head: () => ({
    meta: [
      { title: "Today: HomeKept Tech" },
      { name: "robots", content: "noindex" },
      { name: "viewport", content: "width=device-width, initial-scale=1, viewport-fit=cover" },
      { name: "theme-color", content: "#123f34" },
    ],
  }),
  component: TechApp,
});

// ============================================================================
// App shell + auth/role guard
// ============================================================================

function TechApp() {
  return (
    <div className="min-h-dvh bg-background">
      {/* Phone-width column on wider screens, full-bleed on phones */}
      <div className="mx-auto w-full max-w-[460px] md:my-8 md:overflow-hidden md:rounded-[2.5rem] md:border md:border-border md:shadow-2xl">
        <TechGuard />
      </div>
    </div>
  );
}

type GuardStatus = "checking" | "authorized" | "unauthenticated" | "wrong-role" | "error";

/**
 * Client-side auth + role guard for `/tech`.
 *
 * Mirrors `AppShell`'s guard (`frontend/src/components/app/AppShell.tsx`):
 * this is deliberately NOT a `beforeLoad`. This is TanStack Start with SSR on
 * Cloudflare and the auth cookie lives on the API origin, so a server-side
 * check has nothing to send `GET /api/auth/me` with — it would always look
 * signed out. Checking from an effect guarantees the request only ever
 * happens in the browser, where the cookie is present. SSR (and the first
 * client render, before the effect resolves) renders only `GuardLoading` —
 * the real day sheet (decrypted access notes + customer PII) never mounts,
 * and its data query never fires, until the guard has confirmed both
 * "signed in" and "role === TECHNICIAN".
 *
 * Rules: signed out → `/signin?next=<current path>`; signed in but wrong
 * role → `/app` (mirrors `AppShell`'s destination for a rejected session).
 */
function TechGuard() {
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [status, setStatus] = useState<GuardStatus>("checking");
  const [session, setSession] = useState<Session | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setStatus("checking");
    getSession()
      .then((s) => {
        if (cancelled) return;
        if (!s) {
          setStatus("unauthenticated");
        } else if (s.role !== "TECHNICIAN") {
          setStatus("wrong-role");
        } else {
          setSession(s);
          setStatus("authorized");
        }
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  useEffect(() => {
    if (status === "unauthenticated") {
      navigate({ to: "/signin", search: { next: pathname }, replace: true });
    } else if (status === "wrong-role") {
      navigate({ to: "/app", replace: true });
    }
  }, [status, navigate, pathname]);

  if (status === "error") {
    return <GuardError onRetry={() => setAttempt((n) => n + 1)} />;
  }

  if (status === "authorized" && session) {
    return <TechShell technician={session} />;
  }

  // "checking" | "unauthenticated" | "wrong-role" — a redirect is in flight
  // for the latter two. Render only a loading placeholder so nothing from
  // the day sheet ever flashes for a signed-out or non-technician visitor.
  return <GuardLoading />;
}

function GuardLoading() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-dvh items-center justify-center bg-background"
    >
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">Loading today's visits.</span>
    </div>
  );
}

function GuardError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-background px-6 text-center">
      <div>
        <h1 className="font-display text-xl font-bold tracking-tight">
          We couldn't check your session.
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">Check your connection and try again.</p>
        <div className="mt-6">
          <Button onClick={onRetry}>Try again</Button>
        </div>
      </div>
    </div>
  );
}

function messageFor(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  return "Something went wrong. Try again.";
}

/** Scrolls a stop's card into view (route strip → stop card navigation). */
function scrollToStop(id: number) {
  if (typeof document === "undefined") return;
  const el = document.getElementById(`stop-${id}`);
  if (!el) return;
  const reduced =
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  el.scrollIntoView({ behavior: reduced ? "auto" : "smooth", block: "start" });
}

// ============================================================================
// Day sheet (authorized content only)
// ============================================================================

interface PhotoAttempt {
  key: string;
  previewUrl: string;
  status: "uploading" | "done" | "error";
  error?: string;
}

function TechShell({ technician }: { technician: Session }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const dayQuery = useTechDaySheet(true);
  useSessionExpiredRedirect(dayQuery.error);

  const visits = useMemo(() => dayQuery.data ?? [], [dayQuery.data]);

  // Chronological order for the route strip and stop numbering. The backend
  // already returns the day sheet in schedule order; sorting here is a
  // presentation-only safeguard, not a change to what's fetched.
  const sorted = useMemo(
    () =>
      [...visits].sort(
        (a, b) => new Date(a.scheduledFor).getTime() - new Date(b.scheduledFor).getTime(),
      ),
    [visits],
  );

  // The "current" stop: the one in progress, or else the next scheduled one.
  // Mirrors the auto-expand rule the old layout used.
  const currentVisit = useMemo(
    () =>
      sorted.find((v) => v.status === "IN_PROGRESS") ??
      sorted.find((v) => v.status === "SCHEDULED") ??
      null,
    [sorted],
  );
  const stopNumber = currentVisit ? sorted.findIndex((v) => v.id === currentVisit.id) + 1 : null;
  const otherActive = sorted.filter(
    (v) => v.id !== currentVisit?.id && (v.status === "SCHEDULED" || v.status === "IN_PROGRESS"),
  );
  const otherClosed = sorted.filter(
    (v) => v.id !== currentVisit?.id && v.status !== "SCHEDULED" && v.status !== "IN_PROGRESS",
  );

  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [incompleteId, setIncompleteId] = useState<number | null>(null);
  const [flagForId, setFlagForId] = useState<number | null>(null);
  const [followUpNotice, setFollowUpNotice] = useState<string | null>(null);

  const [errorsByVisit, setErrorsByVisit] = useState<Record<number, string>>({});
  const [flagsByVisit, setFlagsByVisit] = useState<Record<number, FlagResponse[]>>({});
  const [photosByVisit, setPhotosByVisit] = useState<Record<number, PhotoAttempt[]>>({});
  const previewUrlsRef = useRef<string[]>([]);

  // Object URLs used for local photo previews aren't persistable and must be
  // released when the page goes away. This ref intentionally accumulates
  // across renders (it isn't a DOM node ref) — the cleanup below needs the
  // value *at unmount*, not a snapshot captured at mount time.
  useEffect(() => {
    return () => {
      // eslint-disable-next-line react-hooks/exhaustive-deps
      previewUrlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  function setVisitError(id: number, message: string) {
    setErrorsByVisit((prev) => ({ ...prev, [id]: message }));
  }
  function clearVisitError(id: number) {
    setErrorsByVisit((prev) => {
      if (!(id in prev)) return prev;
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }

  const startMutation = useStartVisit();
  const patchServiceMutation = usePatchService();
  const patchTodoMutation = usePatchTodo();
  const uploadPhotoMutation = useUploadPhoto();
  const completeMutation = useCompleteVisit();
  const incompleteMutation = useIncompleteVisit();
  const createFlagMutation = useCreateFlag();

  function handleStart(visit: TechVisitListItem) {
    clearVisitError(visit.id);
    startMutation.mutate(visit.id, {
      onSuccess: () => setExpandedId(visit.id),
      onError: (err) => setVisitError(visit.id, messageFor(err)),
    });
  }

  function handleToggleItem(visit: TechVisitListItem, item: VisitServiceItem) {
    clearVisitError(visit.id);
    patchServiceMutation.mutate(
      {
        visitId: visit.id,
        visitServiceId: item.id,
        request: { completed: !item.completed, technicianNotes: item.technicianNotes },
      },
      { onError: (err) => setVisitError(visit.id, messageFor(err)) },
    );
  }

  function handlePatchTodo(
    visit: TechVisitListItem,
    todo: TodoResponse,
    request: TechPatchTodoRequest,
  ) {
    clearVisitError(visit.id);
    patchTodoMutation.mutate(
      { todoId: todo.id, request },
      { onError: (err) => setVisitError(visit.id, messageFor(err)) },
    );
  }

  function handlePhotoSelected(visit: TechVisitListItem, file: File) {
    const key = crypto.randomUUID();
    const previewUrl = URL.createObjectURL(file);
    previewUrlsRef.current.push(previewUrl);
    setPhotosByVisit((prev) => ({
      ...prev,
      [visit.id]: [...(prev[visit.id] ?? []), { key, previewUrl, status: "uploading" }],
    }));
    uploadPhotoMutation.mutate(
      { visitId: visit.id, file },
      {
        onSuccess: () => {
          setPhotosByVisit((prev) => ({
            ...prev,
            [visit.id]: (prev[visit.id] ?? []).map((p) =>
              p.key === key ? { ...p, status: "done" } : p,
            ),
          }));
        },
        onError: (err) => {
          setPhotosByVisit((prev) => ({
            ...prev,
            [visit.id]: (prev[visit.id] ?? []).map((p) =>
              p.key === key ? { ...p, status: "error", error: messageFor(err) } : p,
            ),
          }));
        },
      },
    );
  }

  async function handleSignOut() {
    await logout();
    // Drop every cached query (day sheet, visit/service data, photos) so
    // nothing lingers in memory for the next technician on this device.
    queryClient.clear();
    navigate({ to: "/signin", replace: true });
  }

  const totalCount = visits.length;
  const doneCount = visits.filter(
    (v) => v.status !== "IN_PROGRESS" && v.status !== "SCHEDULED",
  ).length;
  const pct = totalCount === 0 ? 0 : Math.round((doneCount / totalCount) * 100);

  const totalHours = useMemo(
    () => Math.round((visits.reduce((s, v) => s + v.durationMinutes, 0) / 60) * 10) / 10,
    [visits],
  );
  const routeSummary = useMemo(() => {
    const seen = new Set<string>();
    const ordered: string[] = [];
    for (const v of sorted) {
      if (!seen.has(v.city)) {
        seen.add(v.city);
        ordered.push(v.city);
      }
    }
    return ordered.join(" → ");
  }, [sorted]);

  // Rendered in America/Toronto — the timezone the day sheet is computed in
  // on the backend — rather than the device's local timezone.
  const { weekday, month, day } = getCalendarParts(new Date().toISOString());
  const todayLabel = `${weekday}, ${month} ${day}`;

  const togglingTodoId = patchTodoMutation.isPending
    ? (patchTodoMutation.variables?.todoId ?? null)
    : null;

  const confirmVisit = confirmId ? visits.find((v) => v.id === confirmId) : undefined;
  const incompleteVisit = incompleteId ? visits.find((v) => v.id === incompleteId) : undefined;
  const flagVisit = flagForId ? visits.find((v) => v.id === flagForId) : undefined;

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      {/* Header: identity + day progress, always visible */}
      <header className="sticky top-0 z-30 border-b border-border bg-background/95 px-5 pb-4 pt-[calc(1rem+env(safe-area-inset-top))] backdrop-blur">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              Today
            </p>
            <h1 className="mt-0.5 truncate font-display text-2xl font-extrabold tracking-tight">
              {todayLabel}
            </h1>
            <p className="mt-0.5 truncate text-sm text-muted-foreground">
              {technician.firstName} {technician.lastName.charAt(0)}.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="Open menu"
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-card text-foreground active:scale-95"
          >
            <Menu className="size-5" aria-hidden="true" />
          </button>
        </div>

        {totalCount > 0 && (
          <div className="mt-4">
            <div className="flex items-center justify-between text-xs font-semibold text-muted-foreground">
              <span className="tabular-nums">
                {currentVisit
                  ? `Stop ${stopNumber} of ${totalCount}`
                  : `${totalCount} ${totalCount === 1 ? "visit" : "visits"} today`}
              </span>
              <span className="tabular-nums">
                {doneCount}/{totalCount} done
              </span>
            </div>
            <div
              className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface"
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
        )}
      </header>

      <main className="flex-1 space-y-5 px-4 pb-6 pt-4">
        {followUpNotice && (
          <div
            role="status"
            className="flex items-start justify-between gap-3 rounded-2xl border border-border bg-card px-4 py-3 text-sm text-foreground/90"
          >
            <span>{followUpNotice}</span>
            <button
              type="button"
              onClick={() => setFollowUpNotice(null)}
              aria-label="Dismiss"
              className="shrink-0 text-muted-foreground hover:text-foreground"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>
        )}

        {dayQuery.isLoading && <DaySheetLoading />}

        {dayQuery.isError && <DaySheetError onRetry={() => dayQuery.refetch()} />}

        {!dayQuery.isLoading && !dayQuery.isError && totalCount === 0 && <NoVisitsToday />}

        {!dayQuery.isLoading && !dayQuery.isError && totalCount > 0 && (
          <>
            <RouteStrip
              visits={sorted}
              currentId={currentVisit?.id ?? null}
              routeSummary={routeSummary}
              totalHours={totalHours}
            />

            {currentVisit && (
              <CurrentStopCard
                visit={currentVisit}
                onToggleItem={(item) => handleToggleItem(currentVisit, item)}
                togglingServiceId={
                  patchServiceMutation.isPending &&
                  patchServiceMutation.variables?.visitId === currentVisit.id
                    ? patchServiceMutation.variables.visitServiceId
                    : null
                }
                onPhotoSelected={(file) => handlePhotoSelected(currentVisit, file)}
                photoAttempts={photosByVisit[currentVisit.id] ?? []}
                onFlag={() => setFlagForId(currentVisit.id)}
                flags={flagsByVisit[currentVisit.id] ?? []}
                onPatchTodo={(todo, request) => handlePatchTodo(currentVisit, todo, request)}
                togglingTodoId={togglingTodoId}
                errorMessage={errorsByVisit[currentVisit.id] ?? null}
                onDismissError={() => clearVisitError(currentVisit.id)}
              />
            )}

            {otherActive.length > 0 && (
              <Group label="Up next" tone="muted">
                {otherActive.map((v) => (
                  <StopCard
                    key={v.id}
                    visit={v}
                    index={sorted.findIndex((x) => x.id === v.id) + 1}
                    expanded={expandedId === v.id}
                    onToggleExpand={() => setExpandedId((id) => (id === v.id ? null : v.id))}
                    onStart={() => handleStart(v)}
                    starting={startMutation.isPending && startMutation.variables === v.id}
                    onToggleItem={(item) => handleToggleItem(v, item)}
                    togglingServiceId={
                      patchServiceMutation.isPending &&
                      patchServiceMutation.variables?.visitId === v.id
                        ? patchServiceMutation.variables.visitServiceId
                        : null
                    }
                    onPhotoSelected={(file) => handlePhotoSelected(v, file)}
                    photoAttempts={photosByVisit[v.id] ?? []}
                    onFlag={() => setFlagForId(v.id)}
                    flags={flagsByVisit[v.id] ?? []}
                    onPatchTodo={(todo, request) => handlePatchTodo(v, todo, request)}
                    togglingTodoId={togglingTodoId}
                    onComplete={() => setConfirmId(v.id)}
                    errorMessage={errorsByVisit[v.id] ?? null}
                    onDismissError={() => clearVisitError(v.id)}
                  />
                ))}
              </Group>
            )}

            {otherClosed.length > 0 && (
              <Group label="Done" tone="dim">
                {otherClosed.map((v) => (
                  <StopCard
                    key={v.id}
                    visit={v}
                    index={sorted.findIndex((x) => x.id === v.id) + 1}
                    dim
                    expanded={expandedId === v.id}
                    onToggleExpand={() => setExpandedId((id) => (id === v.id ? null : v.id))}
                    onStart={() => handleStart(v)}
                    starting={false}
                    onToggleItem={(item) => handleToggleItem(v, item)}
                    togglingServiceId={null}
                    onPhotoSelected={(file) => handlePhotoSelected(v, file)}
                    photoAttempts={photosByVisit[v.id] ?? []}
                    onFlag={() => setFlagForId(v.id)}
                    flags={flagsByVisit[v.id] ?? []}
                    onPatchTodo={(todo, request) => handlePatchTodo(v, todo, request)}
                    togglingTodoId={togglingTodoId}
                    onComplete={() => setConfirmId(v.id)}
                    errorMessage={errorsByVisit[v.id] ?? null}
                    onDismissError={() => clearVisitError(v.id)}
                  />
                ))}
              </Group>
            )}

            {doneCount === totalCount && (
              <div className="rounded-3xl border border-border bg-card p-6 text-center">
                <Sparkles className="mx-auto size-6 text-accent" aria-hidden="true" />
                <p className="mt-2 font-display text-lg font-bold">Day complete.</p>
                <p className="text-sm text-muted-foreground">Reports queued. Drive safe.</p>
              </div>
            )}
          </>
        )}
      </main>

      {/* Sticky, always-visible primary action for the current stop. */}
      {currentVisit ? (
        <BottomActionBar
          visit={currentVisit}
          onStart={() => handleStart(currentVisit)}
          starting={startMutation.isPending && startMutation.variables === currentVisit.id}
          onComplete={() => setConfirmId(currentVisit.id)}
        />
      ) : (
        <div aria-hidden="true" className="pointer-events-none h-[env(safe-area-inset-bottom)]" />
      )}

      {/* Modals */}
      {confirmVisit && (
        <CompleteVisitSheet
          visit={confirmVisit}
          mutation={completeMutation}
          onCancel={() => setConfirmId(null)}
          onCompleted={() => setConfirmId(null)}
          onSwitchToIncomplete={() => {
            setConfirmId(null);
            setIncompleteId(confirmVisit.id);
          }}
        />
      )}
      {incompleteVisit && (
        <IncompleteSheet
          visit={incompleteVisit}
          mutation={incompleteMutation}
          onCancel={() => setIncompleteId(null)}
          onCompleted={(res) => {
            setIncompleteId(null);
            setFollowUpNotice(
              `Follow-up visit scheduled for ${formatFullDate(res.followUpScheduledFor)}.`,
            );
          }}
        />
      )}
      {flagVisit && (
        <FlagSheet
          visit={flagVisit}
          mutation={createFlagMutation}
          onCancel={() => setFlagForId(null)}
          onSaved={(flag) => {
            setFlagsByVisit((prev) => ({
              ...prev,
              [flagVisit.id]: [...(prev[flagVisit.id] ?? []), flag],
            }));
            setFlagForId(null);
          }}
        />
      )}
      {menuOpen && (
        <MenuSheet
          technician={technician}
          onClose={() => setMenuOpen(false)}
          onSignOut={handleSignOut}
        />
      )}
    </div>
  );
}

// ============================================================================
// Day-sheet loading / error / empty states
// ============================================================================

function DaySheetLoading() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center gap-3 rounded-3xl border border-border bg-card p-10 text-center"
    >
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <p className="text-sm text-muted-foreground">Loading today's visits…</p>
    </div>
  );
}

function DaySheetError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="rounded-3xl border border-border bg-card p-6 text-center">
      <AlertTriangle className="mx-auto size-6 text-destructive" aria-hidden="true" />
      <p className="mt-2 font-display text-lg font-bold">Couldn't load today's visits.</p>
      <p className="mt-1 text-sm text-muted-foreground">Check your connection and try again.</p>
      <div className="mt-4">
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
      </div>
    </div>
  );
}

function NoVisitsToday() {
  return (
    <div className="rounded-3xl border border-dashed border-border bg-card/60 p-8 text-center">
      <p className="font-display text-lg font-bold">No visits today.</p>
      <p className="mt-1 text-sm text-muted-foreground">Check back tomorrow morning.</p>
    </div>
  );
}

// ============================================================================
// Route strip
// ============================================================================

const STATUS_LABEL: Record<VisitStatus, string> = {
  SCHEDULED: "Up next",
  IN_PROGRESS: "In progress",
  COMPLETED: "Done",
  INCOMPLETE: "Incomplete",
  CANCELLED: "Cancelled",
  RESCHEDULED: "Rescheduled",
};

function RouteStrip({
  visits,
  currentId,
  routeSummary,
  totalHours,
}: {
  visits: TechVisitListItem[];
  currentId: number | null;
  routeSummary: string;
  totalHours: number;
}) {
  return (
    <section aria-label="Today's route">
      <div className="mb-2 flex items-center justify-between gap-3 text-xs text-muted-foreground">
        <span className="inline-flex min-w-0 items-center gap-1.5">
          <MapPin className="size-3.5 shrink-0" aria-hidden="true" />
          <span className="truncate">{routeSummary}</span>
        </span>
        <span className="shrink-0 tabular-nums">{totalHours}h est.</span>
      </div>
      <div role="list" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
        {visits.map((v, i) => (
          <div role="listitem" key={v.id}>
            <RouteChip
              visit={v}
              index={i + 1}
              isCurrent={v.id === currentId}
              onSelect={() => scrollToStop(v.id)}
            />
          </div>
        ))}
      </div>
    </section>
  );
}

function RouteChip({
  visit,
  index,
  isCurrent,
  onSelect,
}: {
  visit: TechVisitListItem;
  index: number;
  isCurrent: boolean;
  onSelect: () => void;
}) {
  const isSettled =
    visit.status === "COMPLETED" ||
    visit.status === "CANCELLED" ||
    visit.status === "INCOMPLETE" ||
    visit.status === "RESCHEDULED";
  const statusWord = STATUS_LABEL[visit.status];

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`Stop ${index}: ${visit.streetAddress}, ${formatTime(visit.scheduledFor)}, ${statusWord}`}
      className={cn(
        "flex min-w-[164px] shrink-0 items-center gap-2.5 rounded-2xl border px-3 py-2.5 text-left transition-colors",
        isCurrent && "border-accent bg-accent/10",
        !isCurrent && isSettled && "border-border bg-surface opacity-70",
        !isCurrent && !isSettled && "border-border bg-card",
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-bold tabular-nums",
          isCurrent && "bg-accent text-accent-foreground",
          !isCurrent && "bg-surface text-foreground",
        )}
      >
        {index}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-semibold text-foreground">
          {visit.streetAddress}
        </span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {formatTime(visit.scheduledFor)} · {statusWord}
        </span>
      </span>
    </button>
  );
}

// ============================================================================
// Current stop (featured card)
// ============================================================================

function CurrentStopCard({
  visit,
  onToggleItem,
  togglingServiceId,
  onPhotoSelected,
  photoAttempts,
  onFlag,
  flags,
  onPatchTodo,
  togglingTodoId,
  errorMessage,
  onDismissError,
}: {
  visit: TechVisitListItem;
  onToggleItem: (item: VisitServiceItem) => void;
  togglingServiceId: number | null;
  onPhotoSelected: (file: File) => void;
  photoAttempts: PhotoAttempt[];
  onFlag: () => void;
  flags: FlagResponse[];
  onPatchTodo: (todo: TodoResponse, request: TechPatchTodoRequest) => void;
  togglingTodoId: number | null;
  errorMessage: string | null;
  onDismissError: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  // Context flags: OPEN flags for this subscriber, minus any raised earlier
  // this session (those already show in the "Flags" section below and would
  // otherwise render twice once the day sheet refetches).
  const contextFlags = visit.flags.filter((f) => !flags.some((raised) => raised.id === f.id));
  const canActOnTodos = visit.status === "IN_PROGRESS";

  return (
    <section
      id={`stop-${visit.id}`}
      aria-labelledby={`stop-${visit.id}-heading`}
      className="scroll-mt-28 overflow-hidden rounded-3xl border-2 border-accent/60 bg-card shadow-lg"
    >
      <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0">
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-accent">
            Current stop
          </p>
          <h2
            id={`stop-${visit.id}-heading`}
            className="mt-0.5 truncate font-display text-xl font-bold tracking-tight"
          >
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""}
          </h2>
          <p className="mt-0.5 truncate text-sm text-muted-foreground">
            {visit.city} · {visit.postalCode}
          </p>
        </div>
        <StatusPill status={visit.status} />
      </div>

      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-border px-5 py-3 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1 tabular-nums">
          <Clock className="size-3.5" aria-hidden="true" />
          {formatTime(visit.scheduledFor)} · {visit.durationMinutes} min
        </span>
        <span aria-hidden="true">·</span>
        <VisitTypeChip type={visit.type} name={visit.name} />
      </div>

      <div className="space-y-4 px-5 py-4">
        <AccessCard notes={visit.accessNotes} />

        {contextFlags.length > 0 && (
          <FlagGroup
            title={`Open items for this home (${contextFlags.length})`}
            flags={contextFlags}
          />
        )}

        <ChecklistSection
          services={visit.services}
          onToggleItem={onToggleItem}
          togglingServiceId={togglingServiceId}
        />

        <TodoList
          todos={visit.todos}
          canAct={canActOnTodos}
          onPatch={onPatchTodo}
          togglingTodoId={togglingTodoId}
        />

        {photoAttempts.length > 0 && <PhotoGrid attempts={photoAttempts} />}

        {flags.length > 0 && <FlagGroup title={`Flags (${flags.length})`} flags={flags} />}

        {errorMessage && <ErrorBanner message={errorMessage} onDismiss={onDismissError} />}

        {/* Quick actions — only legal once the visit is under way. The
            primary "Complete this visit" action lives in the sticky bottom
            bar (see `BottomActionBar`), not here. */}
        {visit.status === "IN_PROGRESS" && (
          <div className="grid grid-cols-2 gap-3">
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              capture="environment"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) onPhotoSelected(f);
                e.target.value = "";
              }}
            />
            <Button
              type="button"
              size="lg"
              variant="outline"
              onClick={() => fileRef.current?.click()}
              className="h-14 rounded-2xl"
            >
              <Camera className="size-5" aria-hidden="true" />
              Add photo
            </Button>
            <Button
              type="button"
              size="lg"
              variant="outline"
              onClick={onFlag}
              className="h-14 rounded-2xl"
            >
              <FlagIcon className="size-5" aria-hidden="true" />
              Raise a flag
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}

/** The sticky, always-visible primary action for the current stop. */
function BottomActionBar({
  visit,
  onStart,
  starting,
  onComplete,
}: {
  visit: TechVisitListItem;
  onStart: () => void;
  starting: boolean;
  onComplete: () => void;
}) {
  if (visit.status !== "SCHEDULED" && visit.status !== "IN_PROGRESS") return null;

  return (
    <div className="sticky bottom-0 z-20 shrink-0 border-t border-border bg-background/95 px-4 py-3 backdrop-blur [padding-bottom:calc(0.75rem+env(safe-area-inset-bottom))]">
      {visit.status === "SCHEDULED" ? (
        <Button
          type="button"
          size="lg"
          variant="accent"
          onClick={onStart}
          disabled={starting}
          className="h-14 w-full rounded-2xl text-base"
        >
          {starting ? (
            <Loader2 className="size-5 animate-spin" aria-hidden="true" />
          ) : (
            <>
              <PlayCircle className="size-5" aria-hidden="true" />
              Start visit
            </>
          )}
        </Button>
      ) : (
        <Button
          type="button"
          size="lg"
          variant="accent"
          onClick={onComplete}
          className="h-14 w-full rounded-2xl text-base"
        >
          <CheckCheck className="size-5" aria-hidden="true" />
          Complete this visit
        </Button>
      )}
    </div>
  );
}

// ============================================================================
// Access card — impossible-to-miss lockbox / entry notes
// ============================================================================

function AccessCard({ notes }: { notes: string }) {
  const hasNotes = notes.trim().length > 0;
  if (!hasNotes) {
    return <p className="text-xs text-muted-foreground">No access notes on file.</p>;
  }
  return (
    <div className="flex items-start gap-3 rounded-2xl border-2 border-warning/50 bg-warning/15 px-4 py-3.5">
      <span
        aria-hidden="true"
        className="mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-warning/25 text-warning-foreground"
      >
        <Lock className="size-4" />
      </span>
      <div className="min-w-0">
        <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-warning-foreground/70">
          Access notes
        </p>
        <p className="mt-1 whitespace-pre-line text-sm font-medium text-warning-foreground">
          {notes}
        </p>
      </div>
    </div>
  );
}

// ============================================================================
// Checklist (services)
// ============================================================================

function ChecklistSection({
  services,
  onToggleItem,
  togglingServiceId,
}: {
  services: VisitServiceItem[];
  onToggleItem: (item: VisitServiceItem) => void;
  togglingServiceId: number | null;
}) {
  const completedCount = services.filter((s) => s.completed).length;
  return (
    <div>
      <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        Checklist ({completedCount}/{services.length})
      </p>
      <ul className="space-y-1">
        {services.map((s) => {
          const isToggling = togglingServiceId === s.id;
          return (
            <li key={s.id}>
              <button
                type="button"
                onClick={() => onToggleItem(s)}
                disabled={isToggling}
                aria-pressed={s.completed}
                aria-label={
                  s.completed ? `Mark "${s.serviceName}" not done` : `Mark "${s.serviceName}" done`
                }
                className="flex min-h-11 w-full items-center gap-3 rounded-2xl px-3 py-3 text-left text-base transition-colors hover:bg-surface active:bg-surface disabled:opacity-60"
              >
                {isToggling ? (
                  <Loader2
                    className="size-6 shrink-0 animate-spin text-muted-foreground"
                    aria-hidden="true"
                  />
                ) : s.completed ? (
                  <CheckCircle2 className="size-6 shrink-0 text-accent" aria-hidden="true" />
                ) : (
                  <Circle className="size-6 shrink-0 text-muted-foreground" aria-hidden="true" />
                )}
                <span
                  className={cn(
                    "min-w-0 flex-1",
                    s.completed && "text-muted-foreground line-through",
                  )}
                >
                  {s.serviceName}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

// ============================================================================
// Your list (customer-submitted todos folded into this visit)
// ============================================================================

function TodoList({
  todos,
  canAct,
  onPatch,
  togglingTodoId,
}: {
  todos: TodoResponse[];
  canAct: boolean;
  onPatch: (todo: TodoResponse, request: TechPatchTodoRequest) => void;
  togglingTodoId: number | null;
}) {
  const [decliningId, setDecliningId] = useState<number | null>(null);
  const [declineNote, setDeclineNote] = useState("");

  if (todos.length === 0) {
    return <p className="text-xs text-muted-foreground">No list items for this visit.</p>;
  }

  return (
    <div>
      <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        Your list ({todos.length})
      </p>
      <ul className="space-y-1">
        {todos.map((t) => {
          const isToggling = togglingTodoId === t.id;
          const isDone = t.status === "DONE";
          const isDeclined = t.status === "DECLINED";
          const isSettled = isDone || isDeclined;
          const isDeclining = decliningId === t.id;

          return (
            <li key={t.id}>
              <div className="flex min-h-11 items-center gap-3 rounded-2xl px-2 py-3">
                {isSettled ? (
                  <span aria-hidden="true" className="shrink-0">
                    {isDone ? (
                      <CheckCircle2 className="size-6 text-accent" />
                    ) : (
                      <Ban className="size-6 text-muted-foreground" />
                    )}
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={() => onPatch(t, { status: "DONE" })}
                    disabled={!canAct || isToggling}
                    aria-label={`Mark "${t.body}" done`}
                    className="shrink-0 disabled:opacity-60"
                  >
                    {isToggling ? (
                      <Loader2
                        className="size-6 animate-spin text-muted-foreground"
                        aria-hidden="true"
                      />
                    ) : (
                      <Circle className="size-6 text-muted-foreground" aria-hidden="true" />
                    )}
                  </button>
                )}

                <div className="min-w-0 flex-1">
                  <p className={cn("text-base", isSettled && "text-muted-foreground line-through")}>
                    {t.body}
                  </p>
                  {isSettled && <span className="sr-only">{isDone ? "Done" : "Declined"}</span>}
                  {isDeclined && t.declineNote && (
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      Declined: {t.declineNote}
                    </p>
                  )}
                </div>

                {canAct && !isSettled && !isDeclining && (
                  <button
                    type="button"
                    onClick={() => {
                      setDecliningId(t.id);
                      setDeclineNote("");
                    }}
                    aria-label={`Decline "${t.body}"`}
                    className="shrink-0 text-xs font-medium text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
                  >
                    Decline
                  </button>
                )}
              </div>

              {isDeclining && (
                <div className="mb-2 ml-9 space-y-2 rounded-2xl bg-surface p-3">
                  <label
                    htmlFor={`decline-note-${t.id}`}
                    className="text-xs font-semibold text-muted-foreground"
                  >
                    Why couldn't this be done?
                  </label>
                  <textarea
                    id={`decline-note-${t.id}`}
                    autoFocus
                    value={declineNote}
                    onChange={(e) => setDeclineNote(e.target.value)}
                    rows={2}
                    className="w-full resize-none rounded-xl border border-border bg-background p-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  />
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="flex-1 rounded-xl"
                      onClick={() => setDecliningId(null)}
                    >
                      Cancel
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      className="flex-1 rounded-xl"
                      disabled={!declineNote.trim() || isToggling}
                      onClick={() => {
                        onPatch(t, { status: "DECLINED", note: declineNote.trim() });
                        setDecliningId(null);
                      }}
                    >
                      Save
                    </Button>
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

// ============================================================================
// Shared: flags, photos, error banner
// ============================================================================

function FlagGroup({ title, flags }: { title: string; flags: FlagResponse[] }) {
  return (
    <div className="space-y-2">
      <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        {title}
      </p>
      {flags.map((f) => (
        <div key={f.id} className="rounded-2xl bg-surface px-3 py-2 text-sm text-foreground/90">
          <SeverityTag severity={f.severity} />
          <p className="mt-1">{f.body}</p>
        </div>
      ))}
    </div>
  );
}

function PhotoGrid({ attempts }: { attempts: PhotoAttempt[] }) {
  return (
    <div>
      <p className="mb-2 text-[11px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
        Photos ({attempts.length})
      </p>
      <div className="grid grid-cols-4 gap-2">
        {attempts.map((p) => (
          <div key={p.key} className="relative aspect-square w-full overflow-hidden rounded-xl">
            <img src={p.previewUrl} alt="" className="size-full object-cover" />
            {p.status === "uploading" && (
              <div className="absolute inset-0 flex items-center justify-center bg-foreground/40">
                <Loader2 className="size-4 animate-spin text-background" aria-hidden="true" />
              </div>
            )}
            {p.status === "error" && (
              <div
                className="absolute inset-0 flex items-center justify-center bg-destructive/70"
                title={p.error ?? "Upload failed."}
              >
                <ImageOff className="size-4 text-destructive-foreground" aria-hidden="true" />
              </div>
            )}
          </div>
        ))}
      </div>
      {attempts.some((p) => p.status === "error") && (
        <p className="mt-2 text-xs text-muted-foreground">
          Some photos couldn't be saved. Photo storage isn't turned on yet.
        </p>
      )}
    </div>
  );
}

function ErrorBanner({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  return (
    <div
      role="alert"
      className="flex items-start justify-between gap-2 rounded-2xl bg-destructive/10 px-3 py-2 text-sm text-destructive"
    >
      <span>{message}</span>
      <button type="button" onClick={onDismiss} aria-label="Dismiss" className="shrink-0">
        <X className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}

// ============================================================================
// Group + compact stop card (other stops: up next / done)
// ============================================================================

function Group({
  label,
  tone,
  children,
}: {
  label: string;
  tone: "muted" | "dim";
  children: React.ReactNode;
}) {
  return (
    <section>
      <h2
        className={cn(
          "px-1 text-[11px] font-bold uppercase tracking-[0.18em]",
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

function StopCard({
  visit,
  index,
  dim,
  expanded,
  onToggleExpand,
  onStart,
  starting,
  onToggleItem,
  togglingServiceId,
  onPhotoSelected,
  photoAttempts,
  onFlag,
  flags,
  onPatchTodo,
  togglingTodoId,
  onComplete,
  errorMessage,
  onDismissError,
}: {
  visit: TechVisitListItem;
  index: number;
  dim?: boolean;
  expanded: boolean;
  onToggleExpand: () => void;
  onStart: () => void;
  starting: boolean;
  onToggleItem: (item: VisitServiceItem) => void;
  togglingServiceId: number | null;
  onPhotoSelected: (file: File) => void;
  photoAttempts: PhotoAttempt[];
  onFlag: () => void;
  flags: FlagResponse[];
  onPatchTodo: (todo: TodoResponse, request: TechPatchTodoRequest) => void;
  togglingTodoId: number | null;
  onComplete: () => void;
  errorMessage: string | null;
  onDismissError: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const contextFlags = visit.flags.filter((f) => !flags.some((raised) => raised.id === f.id));
  const canActOnTodos = visit.status === "IN_PROGRESS";

  return (
    <article
      id={`stop-${visit.id}`}
      className={cn(
        "scroll-mt-28 overflow-hidden rounded-2xl border border-border bg-card",
        dim && "opacity-70",
      )}
    >
      <button
        type="button"
        onClick={onToggleExpand}
        aria-expanded={expanded}
        aria-controls={`stop-body-${visit.id}`}
        className="flex min-h-11 w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface/60"
      >
        <span
          aria-hidden="true"
          className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-surface text-xs font-bold tabular-nums text-foreground"
        >
          {index}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-foreground">
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""}
          </p>
          <p className="truncate text-xs text-muted-foreground">
            {formatTime(visit.scheduledFor)} · {visit.city}
          </p>
        </div>
        <StatusPill status={visit.status} />
        <span aria-hidden="true" className="shrink-0 text-muted-foreground">
          {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
        </span>
      </button>

      {expanded && (
        <div
          id={`stop-body-${visit.id}`}
          className="space-y-4 border-t border-border px-4 pb-4 pt-3"
        >
          <AccessCard notes={visit.accessNotes} />

          {contextFlags.length > 0 && (
            <FlagGroup
              title={`Open items for this home (${contextFlags.length})`}
              flags={contextFlags}
            />
          )}

          <ChecklistSection
            services={visit.services}
            onToggleItem={onToggleItem}
            togglingServiceId={togglingServiceId}
          />

          <TodoList
            todos={visit.todos}
            canAct={canActOnTodos}
            onPatch={onPatchTodo}
            togglingTodoId={togglingTodoId}
          />

          {photoAttempts.length > 0 && <PhotoGrid attempts={photoAttempts} />}

          {flags.length > 0 && <FlagGroup title={`Flags (${flags.length})`} flags={flags} />}

          {errorMessage && <ErrorBanner message={errorMessage} onDismiss={onDismissError} />}

          {/* Action bar — only offer a transition control legal for this
              visit's current status: Start only from SCHEDULED, and
              Photo/Flag/Complete only once IN_PROGRESS. */}
          {visit.status === "SCHEDULED" && (
            <Button
              type="button"
              size="lg"
              variant="accent"
              onClick={onStart}
              disabled={starting}
              className="h-12 w-full rounded-2xl"
            >
              {starting ? (
                <Loader2 className="size-5 animate-spin" aria-hidden="true" />
              ) : (
                <>
                  <PlayCircle className="size-5" aria-hidden="true" />
                  Start visit
                </>
              )}
            </Button>
          )}

          {visit.status === "IN_PROGRESS" && (
            <div className="grid grid-cols-3 gap-2">
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                capture="environment"
                className="sr-only"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) onPhotoSelected(f);
                  e.target.value = "";
                }}
              />
              <Button
                type="button"
                size="lg"
                variant="outline"
                onClick={() => fileRef.current?.click()}
                className="h-12 flex-col gap-0.5 rounded-2xl text-xs"
              >
                <Camera className="size-5" />
                Photo
              </Button>
              <Button
                type="button"
                size="lg"
                variant="outline"
                onClick={onFlag}
                className="h-12 flex-col gap-0.5 rounded-2xl text-xs"
              >
                <FlagIcon className="size-5" />
                Flag
              </Button>
              <Button
                type="button"
                size="lg"
                variant="accent"
                onClick={onComplete}
                className="h-12 flex-col gap-0.5 rounded-2xl text-xs"
              >
                <CheckCheck className="size-5" />
                Complete
              </Button>
            </div>
          )}
        </div>
      )}
    </article>
  );
}

// ============================================================================
// Pills / chips
// ============================================================================

function StatusPill({ status }: { status: VisitStatus }) {
  const iconFor: Record<VisitStatus, React.ReactNode> = {
    SCHEDULED: <Clock className="size-3" aria-hidden="true" />,
    IN_PROGRESS: <Loader2 className="size-3 animate-spin" aria-hidden="true" />,
    COMPLETED: <CheckCheck className="size-3" aria-hidden="true" />,
    INCOMPLETE: <AlertTriangle className="size-3" aria-hidden="true" />,
    CANCELLED: <Ban className="size-3" aria-hidden="true" />,
    RESCHEDULED: <RefreshCw className="size-3" aria-hidden="true" />,
  };
  const clsFor: Record<VisitStatus, string> = {
    SCHEDULED: "bg-surface text-foreground border border-border",
    IN_PROGRESS: "bg-accent text-accent-foreground",
    COMPLETED: "bg-primary/10 text-primary",
    INCOMPLETE: "border border-warning/40 bg-warning/15 text-foreground",
    CANCELLED: "bg-surface text-muted-foreground border border-border",
    RESCHEDULED: "bg-surface text-muted-foreground border border-border",
  };
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider",
        clsFor[status],
      )}
    >
      {iconFor[status]}
      {STATUS_LABEL[status]}
    </span>
  );
}

function VisitTypeChip({ type, name }: { type: VisitType; name: string }) {
  const cls =
    type === "WARRANTY"
      ? "bg-primary text-primary-foreground"
      : type === "EXTRA"
        ? "bg-accent/15 text-accent"
        : type === "WALKTHROUGH"
          ? "bg-info/15 text-info"
          : "bg-surface text-foreground border border-border"; // ROUTINE
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider",
        cls,
      )}
    >
      {name}
    </span>
  );
}

function SeverityTag({ severity }: { severity: FlagSeverity }) {
  const cls =
    severity === "URGENT"
      ? "bg-destructive text-destructive-foreground"
      : severity === "ATTENTION"
        ? "bg-warning text-warning-foreground"
        : "bg-info text-info-foreground";
  const label = severity === "URGENT" ? "Urgent" : severity === "ATTENTION" ? "Attention" : "Info";
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider",
        cls,
      )}
    >
      {label}
    </span>
  );
}

// ============================================================================
// Modals
// ============================================================================

function CompleteVisitSheet({
  visit,
  mutation,
  onCancel,
  onCompleted,
  onSwitchToIncomplete,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useCompleteVisit>;
  onCancel: () => void;
  onCompleted: () => void;
  onSwitchToIncomplete: () => void;
}) {
  const [durationMinutes, setDurationMinutes] = useState(String(visit.durationMinutes));
  const [materialsCost, setMaterialsCost] = useState("0");
  const [materialsNotes, setMaterialsNotes] = useState("");
  const [completionNotes, setCompletionNotes] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const remaining = visit.services.filter((s) => !s.completed).length;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const duration = Number.parseInt(durationMinutes, 10);
    if (!Number.isFinite(duration) || duration < 1) {
      setFormError("Enter the time actually spent on site, in minutes.");
      return;
    }
    const costDollars = Number.parseFloat(materialsCost || "0");
    if (!Number.isFinite(costDollars) || costDollars < 0) {
      setFormError("Enter a materials cost of 0 or more.");
      return;
    }
    setFormError(null);
    mutation.mutate(
      {
        visitId: visit.id,
        request: {
          completionNotes: completionNotes.trim() || null,
          actualDurationMinutes: duration,
          materialsCostCents: Math.round(costDollars * 100),
          materialsNotes: materialsNotes.trim() || null,
        },
      },
      {
        onSuccess: () => onCompleted(),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">Complete this visit?</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""} · {visit.city}
          </p>
          {remaining > 0 && (
            <p className="mt-3 rounded-2xl bg-surface px-3 py-2 text-sm text-foreground/90">
              {remaining} item{remaining === 1 ? "" : "s"} still unchecked. They'll be marked
              complete.
            </p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="actual-duration"
              className="text-xs font-semibold text-muted-foreground"
            >
              Time on site (min)
            </label>
            <input
              id="actual-duration"
              type="number"
              min={1}
              inputMode="numeric"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          <div>
            <label htmlFor="materials-cost" className="text-xs font-semibold text-muted-foreground">
              Materials cost ($)
            </label>
            <input
              id="materials-cost"
              type="number"
              min={0}
              step="0.01"
              inputMode="decimal"
              value={materialsCost}
              onChange={(e) => setMaterialsCost(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
        </div>

        <div>
          <label htmlFor="materials-notes" className="text-xs font-semibold text-muted-foreground">
            What materials? (optional)
          </label>
          <input
            id="materials-notes"
            type="text"
            value={materialsNotes}
            onChange={(e) => setMaterialsNotes(e.target.value)}
            placeholder="e.g. furnace filter, 2 AA batteries"
            className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        <div>
          <label htmlFor="completion-notes" className="text-xs font-semibold text-muted-foreground">
            Notes for the report (optional)
          </label>
          <textarea
            id="completion-notes"
            rows={3}
            value={completionNotes}
            onChange={(e) => setCompletionNotes(e.target.value)}
            className="mt-1 w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        {formError && (
          <p
            role="alert"
            className="rounded-2xl bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {formError}
          </p>
        )}

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
            type="submit"
            variant="accent"
            className="h-12 flex-1 rounded-2xl"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Complete"
            )}
          </Button>
        </div>

        <button
          type="button"
          onClick={onSwitchToIncomplete}
          className="mx-auto block text-xs font-medium text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
        >
          Can't finish this visit? Mark it incomplete instead.
        </button>
      </form>
    </Overlay>
  );
}

function IncompleteSheet({
  visit,
  mutation,
  onCancel,
  onCompleted,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useIncompleteVisit>;
  onCancel: () => void;
  onCompleted: (response: TechIncompleteVisitResponse) => void;
}) {
  const [reason, setReason] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reason.trim()) {
      setFormError("Add a short reason so the office knows what happened.");
      return;
    }
    setFormError(null);
    mutation.mutate(
      { visitId: visit.id, request: { reason: reason.trim() } },
      {
        onSuccess: (res) => onCompleted(res),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">
            Mark this visit incomplete
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""} · {visit.city}
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            A follow-up visit will be scheduled automatically, about a week from now.
          </p>
        </div>
        <label htmlFor="incomplete-reason" className="sr-only">
          Reason
        </label>
        <textarea
          id="incomplete-reason"
          autoFocus
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={4}
          placeholder="What kept this visit from being finished?"
          className="w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        {formError && (
          <p
            role="alert"
            className="rounded-2xl bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {formError}
          </p>
        )}
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-12 flex-1 rounded-2xl"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button type="submit" className="h-12 flex-1 rounded-2xl" disabled={mutation.isPending}>
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Mark incomplete"
            )}
          </Button>
        </div>
      </form>
    </Overlay>
  );
}

const SEVERITY_OPTIONS: { value: FlagSeverity; label: string }[] = [
  { value: "INFO", label: "Info" },
  { value: "ATTENTION", label: "Attention" },
  { value: "URGENT", label: "Urgent" },
];

function FlagSheet({
  visit,
  mutation,
  onCancel,
  onSaved,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useCreateFlag>;
  onCancel: () => void;
  onSaved: (flag: FlagResponse) => void;
}) {
  const [body, setBody] = useState("");
  const [severity, setSeverity] = useState<FlagSeverity>("INFO");
  const [formError, setFormError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!body.trim()) return;
    setFormError(null);
    mutation.mutate(
      { visitId: visit.id, request: { body: body.trim(), severity } },
      {
        onSuccess: (flag) => onSaved(flag),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">Raise a flag</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Shared with the office. May carry forward to the next visit.
          </p>
        </div>

        <div role="radiogroup" aria-label="Severity" className="grid grid-cols-3 gap-2">
          {SEVERITY_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={severity === opt.value}
              onClick={() => setSeverity(opt.value)}
              className={cn(
                "h-10 rounded-xl border text-sm font-semibold transition-colors",
                severity === opt.value
                  ? "border-transparent bg-primary text-primary-foreground"
                  : "border-border bg-background text-foreground/80 hover:bg-surface",
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <label htmlFor="flag-body" className="sr-only">
          What did you notice?
        </label>
        <textarea
          id="flag-body"
          autoFocus
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={5}
          placeholder="What did you notice?"
          className="w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />

        {formError && (
          <p
            role="alert"
            className="rounded-2xl bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {formError}
          </p>
        )}

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
            type="submit"
            className="h-12 flex-1 rounded-2xl"
            disabled={!body.trim() || mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Save flag"
            )}
          </Button>
        </div>
      </form>
    </Overlay>
  );
}

function MenuSheet({
  technician,
  onClose,
  onSignOut,
}: {
  technician: Session;
  onClose: () => void;
  onSignOut: () => void;
}) {
  return (
    <Overlay onClose={onClose}>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="font-display text-xl font-bold tracking-tight">
            {technician.firstName} {technician.lastName.charAt(0)}.
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
        <p className="text-sm text-muted-foreground">HomeKept Technician · GTA route</p>
        <ul className="mt-2 divide-y divide-border rounded-2xl border border-border">
          {["My week", "Saved photos", "Help & contact"].map((l) => (
            <li key={l} className="px-4 py-3 text-sm font-medium text-foreground/90">
              {l}
            </li>
          ))}
          <li>
            <button
              type="button"
              onClick={onSignOut}
              className="w-full px-4 py-3 text-left text-sm font-medium text-foreground/90 hover:bg-surface"
            >
              Sign out
            </button>
          </li>
        </ul>
      </div>
    </Overlay>
  );
}

function Overlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
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
        className="w-full max-w-[460px] rounded-t-3xl border-t border-border bg-card p-5 shadow-2xl [padding-bottom:calc(1.25rem+env(safe-area-inset-bottom))]"
      >
        {children}
      </div>
    </div>
  );
}
