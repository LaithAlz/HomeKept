import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Overlay } from "@/components/tech/TechBits";
import { messageFor } from "@/lib/api";
import {
  useIncompleteVisit,
  type TechIncompleteVisitResponse,
  type TechVisitListItem,
} from "@/lib/tech";

export function IncompleteSheet({
  visit,
  mutation,
  onCancel,
  onCompleted,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useIncompleteVisit>;
  onCancel: () => void;
  onCompleted: (response: TechIncompleteVisitResponse) => void;
}) {
  const [reason, setReason] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reason.trim()) {
      setFormError("Add a short reason so the office knows what happened.");
      return;
    }
    setFormError(null);
    mutation.mutate(
      { visitId: visit.id, request: { reason: reason.trim() } },
      {
        onSuccess: (res) => onCompleted(res),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">
            Mark this visit incomplete
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {visit.streetAddress}
            {visit.unit ? `, Unit ${visit.unit}` : ""} · {visit.city}
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            A follow-up visit will be scheduled automatically, about a week from now.
          </p>
        </div>
        <label htmlFor="incomplete-reason" className="sr-only">
          Reason
        </label>
        <textarea
          id="incomplete-reason"
          autoFocus
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={4}
          placeholder="What kept this visit from being finished?"
          className="w-full resize-none rounded-2xl border border-border bg-background p-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
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
          <Button type="submit" className="h-12 flex-1 rounded-2xl" disabled={mutation.isPending}>
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Mark incomplete"
            )}
          </Button>
        </div>
      </form>
    </Overlay>
  );
}
