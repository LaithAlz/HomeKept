// Mock subscriber data for the customer dashboard.
// Replace with real data fetching once auth + backend are wired up.

export type ActivityType =
  | "visit-report"
  | "visit-completed"
  | "reminder"
  | "photos"
  | "billing";

export interface ActivityItem {
  id: string;
  type: ActivityType;
  title: string;
  detail: string;
  actor: string; // who/what generated it
  timestamp: string; // ISO
  href:
    | { to: "/app/visits" }
    | { to: "/app/reports" }
    | { to: "/app/billing" }
    | { to: "/app/health" };
}

export interface ScheduledService {
  id: string;
  label: string;
}

export interface NextVisit {
  date: string; // ISO, used for the calendar block
  window: string; // e.g. "1:00 – 4:00 PM"
  technicianFirstName: string;
  services: ScheduledService[];
}

export interface HomeHealth {
  score: number; // 0-100
  delta: number; // change from previous quarter
  note: string; // human-readable interpretation
}

export interface Subscriber {
  firstName: string;
  lastName: string;
  email: string;
  planName: "Essential" | "Complete" | "Premier";
  address: {
    street: string;
    neighbourhood: string;
    city: string;
  };
  nextVisit: NextVisit;
  health: HomeHealth;
  activity: ActivityItem[];
}

// Compute a next-Wednesday date so the mock always feels current.
function nextWednesdayAfternoon(): string {
  const d = new Date();
  d.setHours(13, 0, 0, 0);
  const day = d.getDay(); // 0=Sun
  const delta = (3 - day + 7) % 7 || 7; // always in the future
  d.setDate(d.getDate() + delta);
  return d.toISOString();
}

function daysAgo(n: number, hour = 9): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

export const subscriber: Subscriber = {
  firstName: "Priya",
  lastName: "Sharma",
  email: "priya.sharma@example.com",
  planName: "Complete",
  address: {
    street: "14 Maple Ridge Crt",
    neighbourhood: "Erin Mills",
    city: "Mississauga",
  },
  nextVisit: {
    date: nextWednesdayAfternoon(),
    window: "1:00 – 4:00 PM",
    technicianFirstName: "Marcus",
    services: [
      { id: "s1", label: "Furnace filter swap" },
      { id: "s2", label: "AC startup check" },
      { id: "s3", label: "Gutter clearing" },
      { id: "s4", label: "Hose bib reconnection" },
    ],
  },
  health: {
    score: 84,
    delta: 6,
    note: "Up 6 from last quarter. One item flagged for attention: dryer vent cleaning.",
  },
  activity: [
    {
      id: "a1",
      type: "reminder",
      title: "Reminder sent",
      detail: "We confirmed your upcoming Wednesday visit with Marcus.",
      actor: "HomeKept",
      timestamp: daysAgo(1, 8),
      href: { to: "/app/visits" },
    },
    {
      id: "a2",
      type: "visit-report",
      title: "Visit report uploaded",
      detail: "Q1 spring readiness report — 12 checkpoints, 1 flagged.",
      actor: "Marcus T.",
      timestamp: daysAgo(6, 16),
      href: { to: "/app/reports" },
    },
    {
      id: "a3",
      type: "photos",
      title: "12 photos added",
      detail: "Roof, gutters, and exterior trim from the last walkaround.",
      actor: "Marcus T.",
      timestamp: daysAgo(6, 15),
      href: { to: "/app/reports" },
    },
    {
      id: "a4",
      type: "visit-completed",
      title: "Visit completed",
      detail:
        "Replaced 2 HVAC filters, tested 4 smoke detectors, cleared front gutters.",
      actor: "Marcus T.",
      timestamp: daysAgo(6, 14),
      href: { to: "/app/visits" },
    },
    {
      id: "a5",
      type: "billing",
      title: "Payment processed",
      detail: "Complete plan — $189.00 CAD billed to Visa •• 4321.",
      actor: "Billing",
      timestamp: daysAgo(12, 9),
      href: { to: "/app/billing" },
    },
    {
      id: "a6",
      type: "reminder",
      title: "Seasonal checklist updated",
      detail: "Spring items added: hose bib reconnect, AC startup, gutter clear.",
      actor: "HomeKept",
      timestamp: daysAgo(20, 10),
      href: { to: "/app/health" },
    },
    {
      id: "a7",
      type: "billing",
      title: "Plan renewed",
      detail: "Complete plan renewed for another month. No changes to coverage.",
      actor: "Billing",
      timestamp: daysAgo(42, 9),
      href: { to: "/app/billing" },
    },
  ],
};

export function greetingFor(date: Date = new Date()): "morning" | "afternoon" | "evening" {
  const h = date.getHours();
  if (h < 12) return "morning";
  if (h < 17) return "afternoon";
  return "evening";
}

export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffMs = now.getTime() - then.getTime();
  const minutes = Math.round(diffMs / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return then.toLocaleDateString("en-CA", { month: "short", day: "numeric" });
}
