/**
 * Canonical plan data — single source of truth for every pricing/plan display
 * on the public site (landing page, /plans, and JSON-LD offers).
 *
 * Values are ported from docs/pricing-and-visits.md (the commercial spec).
 * Do not hardcode prices, visit counts, or pick allowances anywhere else —
 * import from here so the numbers can never drift or diverge again.
 */

export type PlanId = "essential" | "complete" | "premier";

export interface Plan {
  id: PlanId;
  emoji: string;
  name: string;
  /** Short marketing line shown under the plan name. */
  tagline: string;
  /** Who the plan is built for, matching the spec's "For" column. */
  forWho: string;
  monthlyPriceCad: number;
  /** Total charged once per year under annual billing (2 months free). */
  annualPriceCad: number;
  visitsPerYear: number;
  /** One-line description of the visit cadence. */
  visitsDescription: string;
  includedPicks: number;
  maxPremiumPicks: number;
  yourListTime: string;
  technician: string;
  scheduling: string;
  repairs: string;
  /** Extra items unique to this tier beyond the standard visit rubric. */
  extras: string[];
  priorityScheduling: boolean;
  sameWeekEmergency: boolean;
  dedicatedTechnician: boolean;
  gasTuneupCoordination: boolean;
  smartHomeSupport: boolean;
  annualHomePlan: boolean;
  repairsIncluded: boolean;
  /** 5-6 short bullets for plan cards, shared between landing and /plans. */
  features: string[];
  recommended?: boolean;
}

export const PLANS: Plan[] = [
  {
    id: "essential",
    emoji: "🌱",
    name: "Essential",
    tagline: "The seasonal essentials, on schedule.",
    // The spec marks this cell "awaiting founder sign-off" (never specified before
    // Essential was retired). This is a best-honest-guess placeholder, not settled copy.
    forWho: "Smaller homes with lighter maintenance needs",
    monthlyPriceCad: 89,
    annualPriceCad: 890,
    visitsPerYear: 4,
    visitsDescription: "4 visits a year: the seasonal anchors (winter, spring, summer, fall)",
    includedPicks: 1,
    maxPremiumPicks: 0,
    yourListTime: "Not included",
    technician: "Consistent technician where possible",
    scheduling: "Standard scheduling",
    repairs: "Repairs quoted or referred",
    extras: [],
    priorityScheduling: false,
    sameWeekEmergency: false,
    dedicatedTechnician: false,
    gasTuneupCoordination: false,
    smartHomeSupport: false,
    annualHomePlan: false,
    repairsIncluded: false,
    features: [
      "4 visits a year: winter check, spring readiness, summer systems, fall winterization",
      "Standing checklist every visit: filter check, detector test, mechanicals walkaround, humidity reading",
      "Same-day photo report and Home Health Score update",
      "1 included pick a year (Basic or Medium tier)",
      "Consistent technician where possible",
    ],
  },
  {
    id: "complete",
    emoji: "🏡",
    name: "Complete",
    tagline: "The whole home, handled.",
    forWho: "Most detached and semi-detached homes",
    monthlyPriceCad: 169,
    annualPriceCad: 1690,
    visitsPerYear: 8,
    visitsDescription: "8 visits a year (anchors plus mid-season)",
    includedPicks: 3,
    maxPremiumPicks: 1,
    yourListTime: "About 20 minutes of your-list time per visit",
    technician: "Consistent technician where possible",
    scheduling: "Priority scheduling: issue visits within 48 hours, plus an emergency line",
    repairs: "Repairs quoted or referred",
    extras: ["Licensed gas tune-up coordination"],
    priorityScheduling: true,
    sameWeekEmergency: false,
    dedicatedTechnician: false,
    gasTuneupCoordination: true,
    smartHomeSupport: false,
    annualHomePlan: false,
    repairsIncluded: false,
    recommended: true,
    features: [
      "8 visits a year: seasonal anchors plus 4 mid-season visits",
      "Filter checks, detector tests, and batteries every visit",
      "Same-day photo report and Home Health Score update",
      "About 20 minutes of your-list time per visit",
      "3 included picks a year (up to 1 Premium)",
      "Priority scheduling: issues seen within 48 hours",
      "Licensed gas tune-up coordinated for you",
    ],
  },
  {
    id: "premier",
    emoji: "🔑",
    name: "Premier",
    tagline: "White-glove care with a dedicated technician.",
    forWho: "Larger homes, busy households, aging-in-place",
    monthlyPriceCad: 249,
    annualPriceCad: 2490,
    visitsPerYear: 12,
    visitsDescription: "12 visits a year, monthly, each with a named focus",
    includedPicks: 6,
    maxPremiumPicks: 3,
    yourListTime: "Up to 1 hour of your-list time per visit, including minor repairs",
    technician: "Dedicated technician, guaranteed the same person",
    scheduling: "Same-week scheduling, plus a 24-hour emergency line",
    repairs: "Up to 1 hour of repair labor included per visit, parts at cost",
    extras: ["Smart-home support", "Annual Home Plan (5-year capital forecast)"],
    priorityScheduling: true,
    sameWeekEmergency: true,
    dedicatedTechnician: true,
    gasTuneupCoordination: true,
    smartHomeSupport: true,
    annualHomePlan: true,
    repairsIncluded: true,
    features: [
      "Everything in Complete",
      "12 visits a year, one every month, each with a named focus",
      "A dedicated technician: the same person every time",
      "6 included picks a year (up to 3 Premium)",
      "Same-week scheduling plus a 24-hour emergency line",
      "Annual Home Plan: a 5-year capital forecast",
    ],
  },
];

export function formatDollarsCad(amount: number): string {
  return `$${amount.toLocaleString("en-CA")}`;
}

/**
 * Monthly-equivalent price when billed annually (2 months free).
 * Floored to match whole-dollar display, e.g. Premier $2,490/yr -> $207/mo.
 */
export function annualMonthlyEquivalent(plan: Plan): number {
  return Math.floor(plan.annualPriceCad / 12);
}

/**
 * Derived, displayed price per visit hour, in integer cents (format with
 * `formatCentsCad` from `@/lib/format`). Visits are 90 minutes: per-visit-hour
 * is the monthly price spread across a year of visits, then divided down from
 * a 90-minute visit to a 60-minute (1-hour) rate.
 *
 * per-visit-hour = monthly x 12 / visitsPerYear / 1.5
 */
export function perVisitHourCents(plan: Plan): number {
  const monthlyCents = plan.monthlyPriceCad * 100;
  return Math.round((monthlyCents * 12) / plan.visitsPerYear / 1.5);
}

/**
 * How much a subscriber saves per year by choosing annual over monthly
 * billing, in integer cents (format with `formatCentsCad` from `@/lib/format`).
 *
 * savings = (monthly x 12) - annual
 */
export function annualSavingsCents(plan: Plan): number {
  const monthlyTotalCents = plan.monthlyPriceCad * 100 * 12;
  const annualCents = plan.annualPriceCad * 100;
  return monthlyTotalCents - annualCents;
}
