/**
 * Shared presentation for a visit's status pill and calendar date block, used
 * by both the visits list (`routes/app.visits.tsx`) and the visit detail page
 * (`routes/app.visits.$id.tsx`) so the two pages never disagree about what a
 * status means or how a date renders. Backed by `VisitStatus` from
 * `@/lib/visits` — nothing here is fabricated.
 */

import { CalendarCheck, CheckCircle2, Circle, RefreshCcw, type LucideIcon } from "lucide-react";
import { getCalendarParts } from "@/lib/format";
import type { VisitStatus } from "@/lib/visits";
import { cn } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Status pill
// ---------------------------------------------------------------------------

const STATUS_STYLES: Record<VisitStatus, { className: string; icon: LucideIcon; label: string }> = {
  SCHEDULED: {
    className: "bg-primary/10 text-primary",
    icon: CalendarCheck,
    label: "Scheduled",
  },
  IN_PROGRESS: {
    className: "bg-primary/10 text-primary",
    icon: CalendarCheck,
    label: "In progress",
  },
  COMPLETED: {
    className: "bg-success/15 text-success",
    icon: CheckCircle2,
    label: "Completed",
  },
  INCOMPLETE: {
    className: "bg-warning/15 text-warning",
    icon: Circle,
    label: "Incomplete",
  },
  CANCELLED: {
    className: "bg-muted text-muted-foreground",
    icon: Circle,
    label: "Cancelled",
  },
  RESCHEDULED: {
    className: "bg-muted text-muted-foreground",
    icon: RefreshCcw,
    label: "Rescheduled",
  },
};

export function VisitStatusBadge({ status }: { status: VisitStatus }) {
  const { className, icon: Icon, label } = STATUS_STYLES[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold",
        className,
      )}
    >
      <Icon className="size-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Calendar date block
// ---------------------------------------------------------------------------

export function VisitDateBlock({ scheduledFor, muted }: { scheduledFor: string; muted?: boolean }) {
  const { month, day, weekday } = getCalendarParts(scheduledFor);

  return (
    <div
      className={cn(
        "flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-2xl border text-center",
        muted
          ? "border-border bg-background text-muted-foreground"
          : "border-primary/30 bg-primary/10 text-primary",
      )}
      aria-label={`${weekday}, ${month} ${day}`}
    >
      <div className="text-[10px] font-semibold uppercase tracking-wide">{month}</div>
      <div className="font-display text-2xl font-extrabold leading-none tabular-nums">{day}</div>
    </div>
  );
}
