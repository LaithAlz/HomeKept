import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { useHealthScore } from "@/lib/health";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { formatFullDate, formatTime } from "@/lib/format";
import {
  ScoreRing,
  HealthDeltaChip,
  OpenItemsList,
  verdictFor,
  summaryFor,
} from "@/components/app/health-score";

export const Route = createFileRoute("/app/health")({
  head: () => ({
    meta: [{ title: "Home health: HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: HealthPage,
});

function HealthPage() {
  const query = useHealthScore();
  useSessionExpiredRedirect(query.error);
  const flagged = query.data?.flagged ?? [];

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">
        Home health
      </h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Your 0 to 100 score, and anything your technician has flagged for your home.
      </p>

      <section
        aria-labelledby="score-heading"
        className="mt-8 rounded-3xl border border-border bg-card p-6 md:p-8"
      >
        <h2 id="score-heading" className="sr-only">
          Home health score
        </h2>

        {query.isLoading ? (
          <div
            className="flex items-center justify-center gap-3 py-10 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your home health score.
          </div>
        ) : query.isError ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            We couldn't load your home health score. Try refreshing the page.
          </p>
        ) : query.data ? (
          <div className="flex flex-col items-center gap-6 text-center md:flex-row md:items-center md:gap-10 md:text-left">
            <ScoreRing score={query.data.score} tone="light" />
            <div>
              <p className="font-display text-2xl font-bold text-foreground">
                {verdictFor(query.data.score)}
              </p>
              <p className="mt-2 max-w-md text-sm text-muted-foreground">
                {summaryFor(query.data.score)}
              </p>
              <div className="mt-3">
                <HealthDeltaChip delta={query.data.delta} />
              </div>
              <p className="mt-4 text-xs text-muted-foreground">
                Last checked {formatFullDate(query.data.computedAt)} at{" "}
                {formatTime(query.data.computedAt)}
              </p>
            </div>
          </div>
        ) : null}
      </section>

      <section aria-labelledby="open-items-heading" className="mt-8">
        <h2 id="open-items-heading" className="font-display text-2xl font-bold tracking-tight">
          Open items
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Things your technician has flagged for your home.
        </p>
        <div className="mt-6">
          <OpenItemsList flagged={flagged} isLoading={query.isLoading} isError={query.isError} />
        </div>
      </section>
    </div>
  );
}
