import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Overlay } from "@/components/tech/TechBits";
import { cn } from "@/lib/utils";
import { messageFor } from "@/lib/api";
import {
  useCreateFlag,
  type FlagResponse,
  type FlagSeverity,
  type TechVisitListItem,
} from "@/lib/tech";

const SEVERITY_OPTIONS: { value: FlagSeverity; label: string }[] = [
  { value: "INFO", label: "Info" },
  { value: "ATTENTION", label: "Attention" },
  { value: "URGENT", label: "Urgent" },
];

export function FlagSheet({
  visit,
  mutation,
  onCancel,
  onSaved,
}: {
  visit: TechVisitListItem;
  mutation: ReturnType<typeof useCreateFlag>;
  onCancel: () => void;
  onSaved: (flag: FlagResponse) => void;
}) {
  const [body, setBody] = useState("");
  const [severity, setSeverity] = useState<FlagSeverity>("INFO");
  const [formError, setFormError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!body.trim()) return;
    setFormError(null);
    mutation.mutate(
      { visitId: visit.id, request: { body: body.trim(), severity } },
      {
        onSuccess: (flag) => onSaved(flag),
        onError: (err) => setFormError(messageFor(err)),
      },
    );
  }

  return (
    <Overlay onClose={onCancel}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <h3 className="font-display text-xl font-bold tracking-tight">Raise a flag</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Shared with the office. May carry forward to the next visit.
          </p>
        </div>

        <div role="radiogroup" aria-label="Severity" className="grid grid-cols-3 gap-2">
          {SEVERITY_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={severity === opt.value}
              onClick={() => setSeverity(opt.value)}
              className={cn(
                "h-10 rounded-xl border text-sm font-semibold transition-colors",
                severity === opt.value
                  ? "border-transparent bg-primary text-primary-foreground"
                  : "border-border bg-background text-foreground/80 hover:bg-surface",
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <label htmlFor="flag-body" className="sr-only">
          What did you notice?
        </label>
        <textarea
          id="flag-body"
          autoFocus
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={5}
          placeholder="What did you notice?"
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
          <Button
            type="submit"
            className="h-12 flex-1 rounded-2xl"
            disabled={!body.trim() || mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              "Save flag"
            )}
          </Button>
        </div>
      </form>
    </Overlay>
  );
}
