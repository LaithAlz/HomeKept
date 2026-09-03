import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Overlay } from "@/components/tech/TechBits";
import { messageFor } from "@/lib/api";
import { useCompleteVisit, type TechVisitListItem } from "@/lib/tech";

export function CompleteVisitSheet({
  visit,
  mutation,
  onCancel,
  onCompleted,
  onSwitchToIncomplete,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useCompleteVisit>;
  onCancel: () => void;
  onCompleted: () => void;
  onSwitchToIncomplete: () => void;
}) {
  const [durationMinutes, setDurationMinutes] = useState(String(visit.durationMinutes));
  const [materialsCost, setMaterialsCost] = useState("0");
  const [materialsNotes, setMaterialsNotes] = useState("");
  const [completionNotes, setCompletionNotes] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const remaining = visit.services.filter((s) => !s.completed).length;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const duration = Number.parseInt(durationMinutes, 10);
    if (!Number.isFinite(duration) || duration < 1) {
      setFormError("Enter the time actually spent on site, in minutes.");
      return;
    }
    const costDollars = Number.parseFloat(materialsCost || "0");
    if (!Number.isFinite(costDollars) || costDollars < 0) {
      setFormError("Enter a materials cost of 0 or more.");
      return;
    }
    setFormError(null);
    mutation.mutate(
      {
        visitId: visit.id,
        request: {
          completionNotes: completionNotes.trim() || null,
          actualDurationMinutes: duration,
          materialsCostCents: Math.round(costDollars * 100),
          materialsNotes: materialsNotes.trim() || null,
        },
      },
      {
        onSuccess: () => onCompleted(),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">Complete this visit?</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""} · {visit.city}
          </p>
          {remaining > 0 && (
            <p className="mt-3 rounded-2xl bg-surface px-3 py-2 text-sm text-foreground/90">
              {remaining} item{remaining === 1 ? "" : "s"} still unchecked. They'll be marked
              complete.
            </p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="actual-duration"
              className="text-xs font-semibold text-muted-foreground"
            >
              Time on site (min)
            </label>
            <input
              id="actual-duration"
              type="number"
              min={1}
              inputMode="numeric"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          <div>
            <label htmlFor="materials-cost" className="text-xs font-semibold text-muted-foreground">
              Materials cost ($)
            </label>
            <input
              id="materials-cost"
              type="number"
              min={0}
              step="0.01"
              inputMode="decimal"
              value={materialsCost}
              onChange={(e) => setMaterialsCost(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
        </div>

        <div>
          <label htmlFor="materials-notes" className="text-xs font-semibold text-muted-foreground">
            What materials? (optional)
          </label>
          <input
            id="materials-notes"
            type="text"
            value={materialsNotes}
            onChange={(e) => setMaterialsNotes(e.target.value)}
            placeholder="e.g. furnace filter, 2 AA batteries"
            className="mt-1 w-full rounded-xl border border-border bg-background px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        <div>
          <label htmlFor="completion-notes" className="text-xs font-semibold text-muted-foreground">
            Notes for the report (optional)
          </label>
          <textarea
            id="completion-notes"
            rows={3}
            value={completionNotes}
            onChange={(e) => setCompletionNotes(e.target.value)}
            className="mt-1 w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        {formError && (
          <p
            role="alert"
            className="rounded-2xl bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {formError}
          </p>
        )}

        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-12 flex-1 rounded-2xl"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="accent"
            className="h-12 flex-1 rounded-2xl"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Complete"
            )}
          </Button>
        </div>

        <button
          type="button"
          onClick={onSwitchToIncomplete}
          className="mx-auto block text-xs font-medium text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
        >
          Can't finish this visit? Mark it incomplete instead.
        </button>
      </form>
    </Overlay>
  );
}
