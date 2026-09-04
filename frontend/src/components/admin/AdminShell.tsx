import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useNavigate, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  BarChart3,
  Users,
  Inbox,
  CalendarClock,
  Wrench,
  Map as MapIcon,
  HardHat,
  ListChecks,
  Tags,
  Settings,
  Loader2,
  LogOut,
} from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { getSession, homeFor, logout, type Role } from "@/lib/auth";
import { resetIdentity } from "@/lib/analytics";
import { useQueryClient } from "@tanstack/react-query";
import { useAdminDashboard } from "@/lib/admin";

type Item = {
  to:
    | "/admin"
    | "/admin/metrics"
    | "/admin/subscribers"
    | "/admin/leads"
    | "/admin/walkthroughs"
    | "/admin/visits"
    | "/admin/routes"
    | "/admin/technicians"
    | "/admin/catalog"
    | "/admin/plans"
    | "/admin/settings";
  label: string;
  icon: typeof LayoutDashboard;
  count?: number;
  exact?: boolean;
};

type GuardStatus = "checking" | "authorized" | "unauthenticated" | "forbidden" | "error";

/**
 * Client-side ADMIN role guard for every `/admin/*` route.
 *
 * Mirrors the pattern in `AppShell` (built for `/app` in #17): this is TanStack
 * Start with SSR on Cloudflare, and the session cookie lives on the API origin,
 * so a server-rendered request has nothing to send `GET /api/auth/me` with — a
 * server-side check would always look signed-out. Checking from a component
 * effect guarantees the request only ever happens in the browser, where the
 * cookie is present, and that SSR output never contains admin chrome or data.
 *
 * Two failure modes, two destinations:
 *   - no session at all → `/signin?next=<current path>` (so a customer or a
 *     signed-out visitor lands back here after signing in, if they're admin).
 *   - a session that isn't role ADMIN → that role's own shell (`homeFor`).
 *     A customer or technician must never see the admin console, not even a
 *     flash of the sidebar while the redirect is in flight — every
 *     non-"authorized" state below renders only a loading placeholder, never
 *     `<Outlet />` or the nav.
 */
export function AdminShell() {
  const navigate = useNavigate();
  const [guard, setGuard] = useState<GuardStatus>("checking");
  const [attempt, setAttempt] = useState(0);
  const [forbiddenRole, setForbiddenRole] = useState<Role | null>(null);

  useEffect(() => {
    let cancelled = false;
    setGuard("checking");
    getSession()
      .then((session) => {
        if (cancelled) return;
        if (!session) {
          setGuard("unauthenticated");
        } else if (session.role !== "ADMIN") {
          setForbiddenRole(session.role);
          setGuard("forbidden");
        } else {
          setGuard("authorized");
        }
      })
      .catch(() => {
        if (!cancelled) setGuard("error");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  useEffect(() => {
    if (guard === "unauthenticated") {
      // Read the path directly rather than depending on the router's
      // reactive pathname: this effect must fire exactly once per
      // unauthenticated guard resolution. If pathname were a dep, the
      // effect re-runs once the navigate below lands on `/signin` (still
      // mounted mid-transition) and re-navigates with `next=/signin`.
      navigate({ to: "/signin", search: { next: window.location.pathname }, replace: true });
    } else if (guard === "forbidden" && forbiddenRole) {
      navigate({ to: homeFor(forbiddenRole), replace: true });
    }
  }, [guard, navigate, forbiddenRole]);

  if (guard === "checking") {
    return <SessionLoading />;
  }

  if (guard === "error") {
    return <SessionError onRetry={() => setAttempt((n) => n + 1)} />;
  }

  if (guard !== "authorized") {
    // Redirect is in flight (see effect above) — render nothing so the admin
    // console never flashes for a signed-out visitor or a non-admin session.
    return <SessionLoading />;
  }

  return <AdminConsole />;
}

function SessionLoading() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-dvh items-center justify-center bg-background"
    >
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">Checking your session.</span>
    </div>
  );
}

function SessionError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-background px-4">
      <div className="max-w-sm text-center">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
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

/**
 * Sidebar badge counts come from `GET /api/admin/dashboard` — the same aggregate
 * the dashboard home page renders — so a badge can never disagree with what the
 * dashboard cards show. This hook only ever mounts here, inside `AdminConsole`,
 * which `AdminShell` renders exclusively once `guard === "authorized"` — so the
 * request never fires for a signed-out visitor or a non-admin session.
 */
function AdminConsole() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { data: dashboard } = useAdminDashboard();

  const groups: { label: string; items: Item[] }[] = useMemo(
    () => [
      {
        label: "Overview",
        items: [
          { to: "/admin", label: "Dashboard", icon: LayoutDashboard, exact: true },
          { to: "/admin/metrics", label: "Metrics", icon: BarChart3 },
        ],
      },
      {
        label: "Customers",
        items: [
          {
            to: "/admin/subscribers",
            label: "Subscribers",
            icon: Users,
            count: dashboard?.activeSubscribers,
          },
          { to: "/admin/leads", label: "Leads", icon: Inbox },
          {
            to: "/admin/walkthroughs",
            label: "Walk-throughs",
            icon: CalendarClock,
            count: dashboard?.pendingWalkthroughs,
          },
        ],
      },
      {
        label: "Operations",
        items: [
          {
            to: "/admin/visits",
            label: "Visits",
            icon: Wrench,
            count: dashboard?.upcomingVisits,
          },
          { to: "/admin/routes", label: "Routes", icon: MapIcon },
          { to: "/admin/technicians", label: "Technicians", icon: HardHat },
        ],
      },
      {
        label: "Setup",
        items: [
          { to: "/admin/catalog", label: "Service catalog", icon: ListChecks },
          { to: "/admin/plans", label: "Plans", icon: Tags },
          { to: "/admin/settings", label: "Settings", icon: Settings },
        ],
      },
    ],
    [dashboard],
  );

  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [signingOut, setSigningOut] = useState(false);

  async function handleSignOut() {
    setSigningOut(true);
    await logout();
    resetIdentity();
    // Drop every cached query (subscriber PII, booking contact info) so nothing
    // lingers in memory for the next person on this device.
    queryClient.clear();
    navigate({ to: "/signin", replace: true });
  }

  const isActive = (to: string, exact?: boolean) =>
    exact ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);

  return (
    <div className="min-h-dvh bg-surface/30 text-foreground">
      <Toaster />
      <div className="flex min-h-dvh">
        <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-border bg-card md:flex">
          <div className="flex h-16 items-center gap-2 border-b border-border px-5">
            <Link to="/admin" aria-label="HomeKept Admin">
              <Wordmark size="sm" />
            </Link>
            <span className="rounded-md border border-border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
              Admin
            </span>
          </div>

          <nav className="flex-1 overflow-y-auto px-3 py-4">
            {groups.map((g) => (
              <div key={g.label} className="mb-4">
                <h3 className="px-2 pb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                  {g.label}
                </h3>
                <ul className="flex flex-col gap-0.5">
                  {g.items.map((item) => {
                    const active = isActive(item.to, item.exact);
                    return (
                      <li key={item.to}>
                        <Link
                          to={item.to}
                          className={cn(
                            "group flex items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors",
                            active
                              ? "bg-primary text-primary-foreground"
                              : "text-foreground/80 hover:bg-surface hover:text-foreground",
                          )}
                        >
                          <span className="flex min-w-0 items-center gap-2.5">
                            <item.icon
                              className={cn(
                                "size-4 shrink-0",
                                active
                                  ? "text-primary-foreground"
                                  : "text-muted-foreground group-hover:text-foreground",
                              )}
                            />
                            <span className="truncate">{item.label}</span>
                          </span>
                          {typeof item.count === "number" && item.count > 0 && (
                            <span
                              className={cn(
                                "rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                                active
                                  ? "bg-primary-foreground/15 text-primary-foreground"
                                  : "bg-surface text-foreground/70",
                              )}
                            >
                              {item.count}
                            </span>
                          )}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </nav>

          <div className="border-t border-border p-3">
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium text-foreground/80 transition-colors hover:bg-surface hover:text-foreground disabled:pointer-events-none disabled:opacity-60"
            >
              <LogOut className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
              Sign out
            </button>
            <p className="px-2.5 pt-2 text-xs text-muted-foreground">
              Internal tool · America/Toronto
            </p>
          </div>
        </aside>

        <main id="main" className="min-w-0 flex-1">
          {/* Mobile/tablet header */}
          <div className="flex h-14 items-center gap-2 border-b border-border bg-card px-4 md:hidden">
            <Wordmark size="sm" />
            <span className="rounded-md border border-border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
              Admin
            </span>
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              aria-label="Sign out"
              className="ml-auto inline-flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-surface hover:text-foreground disabled:opacity-60"
            >
              <LogOut className="size-4" aria-hidden="true" />
            </button>
          </div>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
