import { CheckCircle2, PauseCircle, AlertTriangle, CreditCard, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export type StatusKind = "active" | "paused" | "at-risk" | "payment-issue";

const config: Record<
  StatusKind,
  { label: string; icon: LucideIcon; classes: string }
> = {
  active: {
    label: "Active",
    icon: CheckCircle2,
    classes: "bg-success/10 text-success border-success/20",
  },
  paused: {
    label: "Paused",
    icon: PauseCircle,
    classes: "bg-info/10 text-info border-info/20",
  },
  "at-risk": {
    label: "At risk",
    icon: AlertTriangle,
    classes: "bg-warning/15 text-warning border-warning/30",
  },
  "payment-issue": {
    label: "Payment issue",
    icon: CreditCard,
    classes: "bg-destructive/10 text-destructive border-destructive/20",
  },
};

interface StatusPillProps {
  status: StatusKind;
  children?: React.ReactNode;
  className?: string;
}

export function StatusPill({ status, children, className }: StatusPillProps) {
  const { label, icon: Icon, classes } = config[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold",
        classes,
        className,
      )}
      role="status"
    >
      <Icon className="size-3.5" aria-hidden="true" />
      <span>{children ?? label}</span>
    </span>
  );
}
