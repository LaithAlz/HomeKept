/**
 * Full-screen placeholders shared by the customer app shell (`AppShell.tsx`)
 * and the technician shell (`routes/tech.tsx`) while each shell's own
 * client-side auth guard checks `GET /api/auth/me`.
 *
 * Presentational only — the guard logic (session state, redirects, the
 * TECHNICIAN role check) stays in each shell; this just avoids copy-pasting
 * the loading/error markup between them.
 */
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Shown while the guard's session check is in flight (and while a redirect
 * for a signed-out/wrong-role visitor is in flight) so the real page never
 * flashes before the guard has decided.
 */
export function SessionLoading({ label }: { label: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-dvh items-center justify-center bg-background"
    >
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </div>
  );
}

/**
 * Shown when the session check itself fails (network/5xx) — not shown for a
 * simple signed-out visitor, which redirects instead.
 */
export function SessionError({
  onRetry,
  compact = false,
}: {
  onRetry: () => void;
  /** Tighter heading for the phone-width tech shell. */
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        "flex min-h-dvh items-center justify-center bg-background text-center",
        compact ? "px-6" : "px-4",
      )}
    >
      <div className={compact ? undefined : "max-w-sm"}>
        <h1
          className={cn(
            "font-display font-bold tracking-tight text-foreground",
            compact ? "text-xl" : "text-2xl",
          )}
        >
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
