import { useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { AlertCircle, CalendarCheck, Check, Circle, Loader2, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import { useSessionExpiredRedirect } from "@/lib/auth";
import { formatRelativeTime } from "@/lib/format";
import { useCreateTodo, useDeleteTodo, useTodos, type TodoResponse } from "@/lib/todos";
import { cn } from "@/lib/utils";

const MAX_LENGTH = 1000;

export const Route = createFileRoute("/app/list")({
  head: () => ({
    meta: [{ title: "Your list — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: YourListPage,
});

function YourListPage() {
  const todosQuery = useTodos();
  useSessionExpiredRedirect(todosQuery.error);

  const items = todosQuery.data ?? [];

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Your list</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Small tasks for around your home, like a loose towel bar or a squeaky door. Add something
        here and your technician takes care of it on your next scheduled visit.
      </p>

      <section
        aria-labelledby="add-heading"
        className="mt-8 rounded-3xl border border-border bg-card p-6"
      >
        <h2 id="add-heading" className="font-display text-lg font-bold">
          Add something
        </h2>
        <AddTodoForm />
      </section>

      <section className="mt-8" aria-labelledby="list-heading">
        <h2 id="list-heading" className="text-xs uppercase tracking-wide text-muted-foreground">
          Queued items
        </h2>
        <div className="mt-3 space-y-3">
          {todosQuery.isLoading ? (
            <div
              className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
              role="status"
              aria-live="polite"
            >
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Loading your list.
            </div>
          ) : todosQuery.isError ? (
            <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
              We couldn't load your list. Try refreshing the page.
            </p>
          ) : items.length === 0 ? (
            <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground">
              Nothing on your list yet. Add your first item above and your technician will take care
              of it on your next visit.
            </p>
          ) : (
            items.map((item) => <TodoRow key={item.id} item={item} />)
          )}
        </div>
      </section>
    </div>
  );
}

function AddTodoForm() {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useCreateTodo();

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
      onSuccess: () => setValue(""),
      onError: (err) => {
        setError(
          err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
        );
      },
    });
  }

  return (
    <form onSubmit={handleSubmit} className="mt-4" noValidate>
      <Label htmlFor="todo-body" className="sr-only">
        Describe what you'd like done
      </Label>
      <Textarea
        id="todo-body"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="e.g. The towel bar in the main bathroom is loose"
        rows={3}
        maxLength={MAX_LENGTH}
        aria-invalid={error ? "true" : undefined}
        aria-describedby={error ? "todo-body-error" : "todo-body-count"}
      />
      <div className="mt-1 flex items-center justify-between">
        <span id="todo-body-count" className="text-xs text-muted-foreground">
          {value.length}/{MAX_LENGTH}
        </span>
      </div>
      {error && (
        <p id="todo-body-error" role="alert" className="mt-1 text-sm text-destructive">
          {error}
        </p>
      )}
      <Button type="submit" size="sm" disabled={mutation.isPending} className="mt-3">
        {mutation.isPending ? (
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        ) : (
          <Plus className="size-4" aria-hidden="true" />
        )}
        Add to your list
      </Button>
    </form>
  );
}

const STATUS_STYLES: Record<
  TodoResponse["status"],
  { className: string; icon: typeof Circle; label: string }
> = {
  OPEN: { className: "bg-muted text-muted-foreground", icon: Circle, label: "Open" },
  SCHEDULED: { className: "bg-primary/10 text-primary", icon: CalendarCheck, label: "Scheduled" },
  DONE: { className: "bg-emerald-100 text-emerald-800", icon: Check, label: "Done" },
  DECLINED: {
    className: "bg-amber-100 text-amber-800",
    icon: AlertCircle,
    label: "Couldn't be done",
  },
};

function StatusBadge({ status }: { status: TodoResponse["status"] }) {
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

function TodoRow({ item }: { item: TodoResponse }) {
  const deleteMutation = useDeleteTodo();
  const [error, setError] = useState<string | null>(null);

  function handleRemove() {
    setError(null);
    deleteMutation.mutate(item.id, {
      onError: (err) => {
        setError(
          err instanceof ApiError ? err.message : "That didn't go through. Please try again.",
        );
      },
    });
  }

  return (
    <div className="rounded-3xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-sm text-foreground">{item.body}</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={item.status} />
            {item.status === "OPEN" && (
              <span className="text-xs text-muted-foreground">Not yet scheduled.</span>
            )}
            {item.status === "SCHEDULED" && (
              <span className="text-xs text-muted-foreground">
                Will be handled on your next visit.
              </span>
            )}
            {item.status === "DONE" && (
              <span className="text-xs text-muted-foreground">
                Completed{" "}
                <time dateTime={item.updatedAt}>{formatRelativeTime(item.updatedAt)}</time>
              </span>
            )}
          </div>
          {item.status === "DECLINED" && (
            <p className="mt-2 text-sm text-muted-foreground">
              {item.declineNote ?? "Your technician wasn't able to complete this one."}
            </p>
          )}
        </div>
        {item.status === "OPEN" && (
          <div className="flex flex-col items-end gap-1">
            <Button
              type="button"
              size="sm"
              variant="ghost"
              disabled={deleteMutation.isPending}
              onClick={handleRemove}
              aria-label={`Remove "${item.body}" from your list`}
            >
              {deleteMutation.isPending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Trash2 className="size-4" aria-hidden="true" />
              )}
              Remove
            </Button>
            {error && (
              <p role="alert" className="text-xs text-destructive">
                {error}
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
