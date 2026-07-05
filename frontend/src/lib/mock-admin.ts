// Mock data for the internal admin console.
// Times are stored in ISO; formatting helpers normalize to America/Toronto.

export type Plan = "Essential" | "Complete" | "Premier";
export type SubscriberStatus = "active" | "first-visit" | "payment-issue" | "at-risk" | "paused";

export interface Subscriber {
  id: string;
  name: string;
  street: string;
  neighbourhood: string;
  city: "Mississauga" | "Oakville" | "Milton";
  plan: Plan;
  status: SubscriberStatus;
  mrr: number;
  nextVisit: { date: string; technician: string } | null;
}

const planPrice: Record<Plan, number> = {
  Essential: 129,
  Complete: 189,
  Premier: 289,
};

function isoDaysFromNow(n: number, hour = 13): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

export const subscribers: Subscriber[] = [
  {
    id: "sub_001",
    name: "Priya Sharma",
    street: "14 Maple Ridge Crt",
    neighbourhood: "Erin Mills",
    city: "Mississauga",
    plan: "Complete",
    status: "active",
    mrr: planPrice.Complete,
    nextVisit: { date: isoDaysFromNow(2), technician: "Marcus T." },
  },
  {
    id: "sub_002",
    name: "Mark & Helen Chen",
    street: "27 Bronte Creek Dr",
    neighbourhood: "Bronte Creek",
    city: "Oakville",
    plan: "Premier",
    status: "active",
    mrr: planPrice.Premier,
    nextVisit: { date: isoDaysFromNow(2, 15), technician: "Marcus T." },
  },
  {
    id: "sub_003",
    name: "Daniel Nguyen",
    street: "8 Whitehorn Pl",
    neighbourhood: "Beaty",
    city: "Milton",
    plan: "Essential",
    status: "first-visit",
    mrr: planPrice.Essential,
    nextVisit: { date: isoDaysFromNow(2, 17), technician: "Marcus T." },
  },
  {
    id: "sub_004",
    name: "Aiko Tanaka",
    street: "102 Lorne Park Rd",
    neighbourhood: "Lorne Park",
    city: "Mississauga",
    plan: "Complete",
    status: "payment-issue",
    mrr: planPrice.Complete,
    nextVisit: { date: isoDaysFromNow(9), technician: "Unassigned" },
  },
  {
    id: "sub_005",
    name: "Greg & Lisa Park",
    street: "55 Glenashton Dr",
    neighbourhood: "Iroquois Ridge",
    city: "Oakville",
    plan: "Premier",
    status: "active",
    mrr: planPrice.Premier,
    nextVisit: { date: isoDaysFromNow(5), technician: "Marcus T." },
  },
  {
    id: "sub_006",
    name: "Rohan Mehta",
    street: "311 Britannia Rd W",
    neighbourhood: "Beaty",
    city: "Milton",
    plan: "Essential",
    status: "at-risk",
    mrr: planPrice.Essential,
    nextVisit: { date: isoDaysFromNow(14), technician: "Unassigned" },
  },
  {
    id: "sub_007",
    name: "Olivia & Tom Ward",
    street: "9 Lakeshore Rd E",
    neighbourhood: "Old Oakville",
    city: "Oakville",
    plan: "Complete",
    status: "active",
    mrr: planPrice.Complete,
    nextVisit: { date: isoDaysFromNow(11), technician: "Sasha P." },
  },
  {
    id: "sub_008",
    name: "Marie Lemieux",
    street: "47 Falconridge Way",
    neighbourhood: "Erin Mills",
    city: "Mississauga",
    plan: "Complete",
    status: "paused",
    mrr: 0,
    nextVisit: null,
  },
];

// ---------------------------------------------------------------------------
// Formatting helpers — always Toronto time + CAD
// ---------------------------------------------------------------------------

const TZ = "America/Toronto";

export function formatCAD(n: number): string {
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
    maximumFractionDigits: 0,
  }).format(n);
}

export function formatDateShort(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    month: "short",
    day: "numeric",
  }).format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(iso));
}

export function formatTodayLong(date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

// ---------------------------------------------------------------------------
// Aggregate metrics
// ---------------------------------------------------------------------------

export const metrics = {
  mrr: subscribers.reduce((s, x) => s + x.mrr, 0),
  mrrDeltaPct: 8.4,
  activeCount: subscribers.filter((s) => s.status !== "paused").length,
  activeNetNew: 4,
  walkthroughsBooked: 14,
  walkthroughsWeekDelta: 3,
  atRiskCount: subscribers.filter((s) => s.status === "at-risk" || s.status === "payment-issue")
    .length,
};
