import { Link, Outlet, useRouterState } from "@tanstack/react-router";
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
} from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { cn } from "@/lib/utils";
import { pendingWalkthroughs, subscribers, attention } from "@/lib/mock-admin";

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

const groups: { label: string; items: Item[] }[] = [
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
        count: subscribers.length,
      },
      { to: "/admin/leads", label: "Leads", icon: Inbox, count: 9 },
      {
        to: "/admin/walkthroughs",
        label: "Walk-throughs",
        icon: CalendarClock,
        count: pendingWalkthroughs.length,
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
        count: attention.filter((a) => a.kind === "unassigned-visit").length,
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
];

export function AdminShell() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  const isActive = (to: string, exact?: boolean) =>
    exact ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);

  return (
    <div className="min-h-dvh bg-surface/30 text-foreground">
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

          <div className="border-t border-border p-4 text-xs text-muted-foreground">
            Internal tool · America/Toronto
          </div>
        </aside>

        <main id="main" className="min-w-0 flex-1">
          {/* Mobile/tablet header */}
          <div className="flex h-14 items-center gap-2 border-b border-border bg-card px-4 md:hidden">
            <Wordmark size="sm" />
            <span className="rounded-md border border-border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-[0.18em] text-muted-foreground">
              Admin
            </span>
          </div>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
