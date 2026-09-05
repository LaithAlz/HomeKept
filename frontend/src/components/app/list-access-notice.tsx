/**
 * The calm, full-card explanation shown on `/app/list` in place of the add
 * form when the subscription isn't serviceable. Wording logic lives in
 * `@/lib/list-access` so it's shared verbatim with `AddTodoDialog`.
 */
import { Link } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import type { AppSubscription } from "@/lib/account";
import { listAccessMessage } from "@/lib/list-access";

export function ListAccessNotice({ subscription }: { subscription: AppSubscription }) {
  const { heading, body, linkLabel } = listAccessMessage(subscription);
  return (
    <div role="note" className="rounded-3xl border border-border bg-card p-6">
      <h2 className="font-display text-lg font-bold">{heading}</h2>
      <p className="mt-2 text-sm text-muted-foreground">{body}</p>
      <Button asChild size="sm" className="mt-4">
        <Link to="/plans">{linkLabel}</Link>
      </Button>
    </div>
  );
}
