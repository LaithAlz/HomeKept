/**
 * Shared presentation for the Home Health Score, used by both the customer
 * dashboard (`routes/app.index.tsx`) and the full health page
 * (`routes/app.health.tsx`) so the two pages never disagree about what a
 * score/verdict/flag means. Backed by `GET /api/app/health-score` via
 * `useHealthScore` (`@/lib/health`) — nothing here is fabricated.
 */

import { ArrowUpRight, ArrowDownRight, Loader2 } from "lucide-react";
import type { FlagSeverity, HealthScoreFlaggedItem } from "@/lib/health";
import { cn } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Verdict + summary copy
// ---------------------------------------------------------------------------

export function verdictFor(score: number): string {
  if (score >= 90) return "Excellent";
  if (score >= 75) return "Good";
  if (score >= 60) return "Fair";
  return "Needs attention";
}

export function summaryFor(score: number): string {
  if (score >= 90) return "Your home is in excellent shape.";
  if (score >= 75) return "Your home is in good shape.";
  if (score >= 60) return "Your home is in fair shape, with a few things to watch.";
  return "Your home needs some attention.";
}

// ---------------------------------------------------------------------------
// Flag severity helpers
// ---------------------------------------------------------------------------

const SEVERITY_RANK: Record<FlagSeverity, number> = { URGENT: 3, ATTENTION: 2, INFO: 1 };

function highestSeverityFlag(flagged: HealthScoreFlaggedItem[]): HealthScoreFlaggedItem | null {
  if (flagged.length === 0) return null;
  return [...flagged].sort((a, b) => SEVERITY_RANK[b.severity] - SEVERITY_RANK[a.severity])[0];
}

/**
 * The highest-severity flag worth surfacing as an attention banner.
 * INFO is deliberately excluded: per `FlagSeverity.java`, INFO means "customer
 * is aware, no action required," so it shouldn't read as an urgent nudge.
 */
export function attentionFlag(flagged: HealthScoreFlaggedItem[]): HealthScoreFlaggedItem | null {
  return highestSeverityFlag(flagged.filter((flag) => flag.severity !== "INFO"));
}

const SEVERITY_STYLES: Record<FlagSeverity, { label: string; className: string }> = {
  URGENT: { label: "Urgent", className: "bg-destructive text-destructive-foreground" },
  ATTENTION: { label: "Attention", className: "bg-warning text-warning-foreground" },
  INFO: { label: "Info", className: "bg-info text-info-foreground" },
};

export function SeverityBadge({ severity }: { severity: FlagSeverity }) {
  const { label, className } = SEVERITY_STYLES[severity];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider",
        className,
      )}
    >
      {label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Score ring
// ---------------------------------------------------------------------------

export function ScoreRing({ score, tone = "light" }: { score: number; tone?: "light" | "dark" }) {
  const size = 168;
  const stroke = 14;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, score));
  const offset = circumference * (1 - clamped / 100);

  const trackClass = tone === "dark" ? "text-primary-foreground/20" : "text-border";
  const progressClass = "text-accent";
  const numberClass = tone === "dark" ? "text-primary-foreground" : "text-foreground";
  const labelClass = tone === "dark" ? "text-primary-foreground/70" : "text-muted-foreground";

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role="img"
        aria-label={`Home health score ${clamped} out of 100`}
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          className={trackClass}
          strokeWidth={stroke}
          opacity={0.6}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          className={progressClass}
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span
          className={cn(
            "font-display text-5xl font-extrabold leading-none tracking-tight",
            numberClass,
          )}
        >
          {clamped}
        </span>
        <span className={cn("mt-1 text-xs font-medium uppercase tracking-[0.18em]", labelClass)}>
          out of 100
        </span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Delta chip
// ---------------------------------------------------------------------------

export function HealthDeltaChip({ delta }: { delta: number }) {
  if (delta === 0) return null;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold",
        delta > 0 ? "bg-accent/15 text-accent" : "bg-destructive/15 text-destructive",
      )}
    >
      {delta > 0 ? (
        <ArrowUpRight className="size-3.5" aria-hidden="true" />
      ) : (
        <ArrowDownRight className="size-3.5" aria-hidden="true" />
      )}
      {delta > 0 ? "Up" : "Down"} {Math.abs(delta)} since your last visit
    </span>
  );
}

// ---------------------------------------------------------------------------
// Open items list (real flagged[] items — never a fabricated per-system grid)
// ---------------------------------------------------------------------------

export function OpenItemsList({
  flagged,
  isLoading,
  isError,
}: {
  flagged: HealthScoreFlaggedItem[];
  isLoading: boolean;
  isError: boolean;
}) {
  if (isLoading) {
    return (
      <div
        className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
        role="status"
        aria-live="polite"
      >
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        Loading your open items.
      </div>
    );
  }

  if (isError) {
    return (
      <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
        We couldn't load your open items. Try refreshing the page.
      </p>
    );
  }

  if (flagged.length === 0) {
    return (
      <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
        Everything is on track. Nothing is flagged on your home right now.
      </p>
    );
  }

  return (
    <ul className="grid gap-3 md:grid-cols-2">
      {flagged.map((item) => (
        <li key={item.id} className="rounded-2xl border border-border bg-card p-5">
          <div className="flex items-start justify-between gap-3">
            <p className="text-sm text-foreground">{item.body}</p>
            <SeverityBadge severity={item.severity} />
          </div>
        </li>
      ))}
    </ul>
  );
}
