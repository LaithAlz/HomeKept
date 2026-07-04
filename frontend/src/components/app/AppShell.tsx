import { useEffect, useState } from "react";
import { Link, Outlet, useNavigate, useRouterState } from "@tanstack/react-router";
import {
  Home,
  CalendarCheck,
  HeartPulse,
  FileText,
  CreditCard,
  Settings,
  Menu,
  X,
  LogOut,
  Loader2,
} from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";
import { subscriber } from "@/lib/mock-account";
import { cn } from "@/lib/utils";
import { getSession, logout } from "@/lib/auth";

const navItems = [
  { to: "/app", label: "Home", icon: Home, exact: true },
  { to: "/app/visits", label: "Visits", icon: CalendarCheck, exact: false },
  { to: "/app/health", label: "Home health", icon: HeartPulse, exact: false },
  { to: "/app/reports", label: "Reports", icon: FileText, exact: false },
  { to: "/app/billing", label: "Billing", icon: CreditCard, exact: false },
  { to: "/app/settings", label: "Settings", icon: Settings, exact: false },
] as const;

type GuardStatus = "checking" | "authenticated" | "unauthenticated" | "error";

/**
 * Client-side auth guard for every `/app/*` route.
 *
 * This is intentionally NOT a `beforeLoad`: this is TanStack Start with SSR
 * on Cloudflare, and the auth cookies live on the API origin, so a
 * server-rendered request has nothing to send `GET /api/auth/me` with — a
 * server-side check would always look signed-out. Checking from a
 * component effect guarantees the request only ever happens in the
 * browser, where the cookie is present.
 */
export function AppShell() {
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [mobileOpen, setMobileOpen] = useState(false);
  const [guard, setGuard] = useState<GuardStatus>("checking");
  const [attempt, setAttempt] = useState(0);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setGuard("checking");
    getSession()
      .then((session) => {
        if (cancelled) return;
        setGuard(session ? "authenticated" : "unauthenticated");
      })
      .catch(() => {
        if (!cancelled) setGuard("error");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  useEffect(() => {
    if (guard !== "unauthenticated") return;
    navigate({ to: "/signin", search: { next: pathname }, replace: true });
  }, [guard, navigate, pathname]);

  async function handleSignOut() {
    setSigningOut(true);
    await logout();
    navigate({ to: "/signin", replace: true });
  }

  if (guard === "checking") {
    return <SessionLoading />;
  }

  if (guard === "error") {
    return <SessionError onRetry={() => setAttempt((n) => n + 1)} />;
  }

  if (guard === "unauthenticated") {
    // Redirect is in flight (see effect above) — render nothing so the
    // dashboard's mock content never flashes for a signed-out visitor.
    return <SessionLoading />;
  }

  const isActive = (to: string, exact: boolean) =>
    exact ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);

  const initials = `${subscriber.firstName[0] ?? ""}${subscriber.lastName[0] ?? ""}`.toUpperCase();

  return (
    <div className="min-h-dvh bg-background text-foreground">
      {/* Mobile top bar */}
      <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-border bg-background/90 px-4 backdrop-blur md:hidden">
        <Link to="/app" aria-label="HomeKept dashboard">
          <Wordmark size="md" />
        </Link>
        <button
          type="button"
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((v) => !v)}
          className="inline-flex size-11 items-center justify-center rounded-full text-foreground hover:bg-surface"
        >
          {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </header>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="border-b border-border bg-background md:hidden">
          <nav className="mx-auto max-w-7xl px-4 py-3">
            <ul className="flex flex-col gap-1">
              {navItems.map((item) => (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium",
                      isActive(item.to, item.exact)
                        ? "bg-primary text-primary-foreground"
                        : "text-foreground hover:bg-surface",
                    )}
                  >
                    <item.icon className="size-4" aria-hidden="true" />
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
            <div className="mt-4 flex items-center gap-3 rounded-2xl border border-border bg-surface px-3 py-3">
              <Avatar initials={initials} />
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold">
                  {subscriber.firstName} {subscriber.lastName}
                </div>
                <div className="truncate text-xs text-muted-foreground">
                  {subscriber.planName} plan
                </div>
              </div>
            </div>
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              className="mt-2 flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium text-foreground/80 hover:bg-surface hover:text-foreground disabled:pointer-events-none disabled:opacity-60"
            >
              <LogOut className="size-4" aria-hidden="true" />
              Sign out
            </button>
          </nav>
        </div>
      )}

      <div className="mx-auto flex w-full max-w-[1400px]">
        {/* Desktop sidebar */}
        <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-border bg-surface/40 md:flex">
          <div className="flex h-20 items-center px-6">
            <Link to="/app" aria-label="HomeKept dashboard">
              <Wordmark size="md" />
            </Link>
          </div>

          <nav className="flex-1 px-3 py-4">
            <ul className="flex flex-col gap-1">
              {navItems.map((item) => (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    className={cn(
                      "group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
                      isActive(item.to, item.exact)
                        ? "bg-primary text-primary-foreground shadow-sm"
                        : "text-foreground/80 hover:bg-surface hover:text-foreground",
                    )}
                  >
                    <item.icon
                      className={cn(
                        "size-4 shrink-0",
                        isActive(item.to, item.exact)
                          ? "text-primary-foreground"
                          : "text-muted-foreground group-hover:text-foreground",
                      )}
                      aria-hidden="true"
                    />
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>

          <div className="border-t border-border p-4">
            <div className="flex items-center gap-3 rounded-2xl px-2 py-2">
              <Avatar initials={initials} />
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold">
                  {subscriber.firstName} {subscriber.lastName}
                </div>
                <div className="truncate text-xs text-muted-foreground">
                  {subscriber.planName} plan
                </div>
              </div>
            </div>
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              className="mt-1 flex w-full items-center gap-3 rounded-xl px-2 py-2 text-sm font-medium text-foreground/70 transition-colors hover:bg-surface hover:text-foreground disabled:pointer-events-none disabled:opacity-60"
            >
              <LogOut className="size-4 shrink-0" aria-hidden="true" />
              Sign out
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main id="main" className="flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function Avatar({ initials }: { initials: string }) {
  return (
    <span
      aria-hidden="true"
      className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-accent text-sm font-bold text-accent-foreground"
    >
      {initials}
    </span>
  );
}

function SessionLoading() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-dvh items-center justify-center bg-background"
    >
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">Loading your account.</span>
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
