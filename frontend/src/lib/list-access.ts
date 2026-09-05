/**
 * Shared "you can't do this yet" messaging for actions that require a
 * serviceable subscription: adding a "your list" item (`/app/list`,
 * `AddTodoDialog`) and, by the same backend rule, requesting a reschedule.
 * Mirrors the single source of truth on the backend,
 * `SubscriberStatus.isServiceable()` (ACTIVE or PAYMENT_ISSUE only — see
 * `SubscriberNotActiveException.java`), so a paused/cancelled customer never
 * has to decode a raw 409 and always gets a plain-language explanation plus
 * a way forward instead.
 */
import type { AppSubscription } from "@/lib/account";
import { formatFullDate } from "@/lib/format";

/** Mirrors `SubscriberStatus.isServiceable()` verbatim. */
export function isListServiceable(subscription: AppSubscription): boolean {
  return subscription.status === "ACTIVE" || subscription.status === "PAYMENT_ISSUE";
}

export interface ListAccessMessage {
  heading: string;
  body: string;
  linkLabel: string;
}

/**
 * Picks the explanation + next step for a non-serviceable subscription.
 * Only call this once `isListServiceable` has already returned `false`.
 */
export function listAccessMessage(subscription: AppSubscription): ListAccessMessage {
  const { status, currentPeriodEnd } = subscription;

  if (status === "PENDING_ACTIVATION") {
    return {
      heading: "Your plan hasn't started yet",
      body: "You'll be able to add items to your list once your plan starts.",
      linkLabel: "Choose a plan",
    };
  }

  const accessStillRunning =
    status === "CANCELLED" &&
    !!currentPeriodEnd &&
    new Date(currentPeriodEnd).getTime() > Date.now();

  if (accessStillRunning && currentPeriodEnd) {
    return {
      heading: "Your plan is cancelled",
      body: `Your access ends on ${formatFullDate(currentPeriodEnd)}. Start again to keep adding to your list.`,
      linkLabel: "Start again",
    };
  }

  return {
    heading: "You'll need an active plan",
    body: "Adding items to your list needs an active plan.",
    linkLabel: "View plans",
  };
}
