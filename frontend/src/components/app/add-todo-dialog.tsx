/**
 * "Add a request" dialog: lets the customer queue an item on their "your
 * list" for their next visit from the dashboard's next-visit card, via the
 * same `POST /api/app/todos` mutation and 1000-character limit as
 * `/app/list` (`useCreateTodo` in `@/lib/todos`), so the todos query stays
 * in sync everywhere it's read.
 *
 * Checks subscription serviceability itself (`list-access-notice.tsx`, the
 * same rule the backend enforces) before rendering the form, so a
 * paused/cancelled customer gets the same calm explanation shown on
 * `/app/list` instead of the dialog posting and failing on a 409.
 */
import { useEffect, useId, useState, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import { useSubscription } from "@/lib/account";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { isListServiceable, listAccessMessage } from "@/lib/list-access";
import { useCreateTodo } from "@/lib/todos";

const MAX_LENGTH = 1000;

interface AddTodoDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddTodoDialog({ open, onOpenChange }: AddTodoDialogProps) {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useCreateTodo();
  const subscriptionQuery = useSubscription();
  useSessionExpiredRedirect(subscriptionQuery.error);

  const baseId = useId();
  const titleId = `${baseId}-title`;
  const descId = `${baseId}-desc`;
  const bodyId = `${baseId}-body`;
  const countId = `${baseId}-count`;
  const errorId = `${baseId}-error`;

  // Reset to a clean form every time the dialog is (re)opened.
  useEffect(() => {
    if (open) {
      setValue("");
      setError(null);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const blockedMessage =
    subscriptionQuery.data && !isListServiceable(subscriptionQuery.data)
      ? listAccessMessage(subscriptionQuery.data)
      : null;

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) {
      setError("Add a few words about what you'd like done.");
      return;
    }
    if (trimmed.length > MAX_LENGTH) {
      setError(`Keep it under ${MAX_LENGTH} characters.`);
      return;
    }
    setError(null);
    mutation.mutate(trimmed, {
      onSuccess: () => {
        toast.success("Added to your list", {
          description: "It's queued for your next scheduled visit.",
        });
        setValue("");
        onOpenChange(false);
      },
      onError: (err) => {
        setError(
          err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
        );
      },
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (mutation.isPending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent aria-labelledby={titleId} aria-describedby={descId} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle id={titleId}>
            {blockedMessage ? blockedMessage.heading : "Add to your list"}
          </DialogTitle>
          <DialogDescription id={descId}>
            {blockedMessage
              ? blockedMessage.body
              : "Small tasks for around your home, like a loose towel bar or a squeaky door. Your technician sets time aside for your list on every visit."}
          </DialogDescription>
        </DialogHeader>

        {subscriptionQuery.isLoading ? (
          <div
            className="flex items-center gap-3 py-2 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Checking your plan.
          </div>
        ) : subscriptionQuery.isError ? (
          <>
            <p className="text-sm text-muted-foreground">
              We couldn't check your plan. Try refreshing the page.
            </p>
            <DialogFooter className="mt-2">
              <DialogClose asChild>
                <Button type="button" variant="outline">
                  Close
                </Button>
              </DialogClose>
            </DialogFooter>
          </>
        ) : blockedMessage ? (
          <DialogFooter className="mt-2">
            <DialogClose asChild>
              <Button type="button" variant="outline">
                Close
              </Button>
            </DialogClose>
            <Button asChild>
              <Link to="/plans">{blockedMessage.linkLabel}</Link>
            </Button>
          </DialogFooter>
        ) : (
          <form onSubmit={handleSubmit} noValidate>
            <Label htmlFor={bodyId}>Describe what you'd like done</Label>
            <Textarea
              id={bodyId}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="e.g. The towel bar in the main bathroom is loose"
              rows={4}
              maxLength={MAX_LENGTH}
              disabled={mutation.isPending}
              aria-invalid={error ? "true" : undefined}
              aria-describedby={error ? errorId : countId}
              className="mt-1"
            />
            <div className="mt-1 flex items-center justify-between">
              <span id={countId} className="text-xs text-muted-foreground">
                {value.length}/{MAX_LENGTH}
              </span>
            </div>
            {error && (
              <p id={errorId} role="alert" className="mt-1 text-sm text-destructive">
                {error}
              </p>
            )}

            <DialogFooter className="mt-6">
              <DialogClose asChild>
                <Button type="button" variant="outline" disabled={mutation.isPending}>
                  Cancel
                </Button>
              </DialogClose>
              <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
                {mutation.isPending && (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                )}
                Add to your list
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
