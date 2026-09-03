/**
 * Small presentational pieces shared by the technician day sheet
 * (`routes/tech.tsx`): status/type/severity pills, and the bottom-sheet
 * overlay every modal in `components/tech/*Sheet.tsx` renders into.
 */
import { useEffect } from "react";
import { AlertTriangle, Ban, CheckCheck, Clock, Loader2, RefreshCw } from "lucide-react";
import { cn } from "@/lib/utils";
import type { FlagSeverity, VisitStatus, VisitType } from "@/lib/tech";

/**
 * `label` is passed in rather than looked up here because the status→label
 * map (`STATUS_LABEL` in `routes/tech.tsx`) is also used by the route strip,
 * which stays in the route file — this avoids either duplicating that map or
 * having a component import from a route module.
 */
export function StatusPill({ status, label }: { status: VisitStatus; label: string }) {
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
      {label}
    </span>
  );
}

export function VisitTypeChip({ type, name }: { type: VisitType; name: string }) {
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

export function SeverityTag({ severity }: { severity: FlagSeverity }) {
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

/** The shared bottom-sheet chrome every tech modal (`components/tech/*Sheet.tsx`) renders into. */
export function Overlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
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
