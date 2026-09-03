/**
 * Shared loading/error row for admin console panels and pages. `className`
 * carries whatever spacing/border/background each site already used (a
 * compact `p-4` row inside a dashboard card, or a `mt-6`/`mt-8` banner with a
 * destructive border+background at the top of a full list page) — the two
 * markup shapes were already identical apart from that, just copy-pasted
 * across ~8 admin routes.
 */
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function PanelLoading({ label, className = "p-4" }: { label: string; className?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("flex items-center gap-2 text-sm text-muted-foreground", className)}
    >
      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
      {label}
    </div>
  );
}

export function PanelError({
  label,
  onRetry,
  className = "p-4",
}: {
  label: string;
  onRetry: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-wrap items-center justify-between gap-3 text-sm text-destructive",
        className,
      )}
    >
      <span>{label}</span>
      <Button size="sm" variant="outline" onClick={onRetry}>
        Try again
      </Button>
    </div>
  );
}
