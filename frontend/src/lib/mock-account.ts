// Mock subscriber data for the customer dashboard.
// Replace with real data fetching once auth + backend are wired up.
//
// Visit and activity data has moved to real fetches (`@/lib/visits`) — see
// GitHub issue #32. Billing/plan and profile/address fields have moved to real
// fetches (`@/lib/account`, `GET /api/app/subscription` + `GET /api/app/account`) —
// see GitHub issue #100. Home health has also moved to a real fetch
// (`@/lib/health`, `GET /api/app/health-score`). What's left here (name + plan
// label for the app shell) still has no backing GET endpoint and stays mock
// until that lands.

export interface Subscriber {
  firstName: string;
  lastName: string;
  planName: "Essential" | "Complete" | "Premier";
  address: {
    street: string;
    neighbourhood: string;
    city: string;
  };
}

export const subscriber: Subscriber = {
  firstName: "Priya",
  lastName: "Sharma",
  planName: "Complete",
  address: {
    street: "14 Maple Ridge Crt",
    neighbourhood: "Erin Mills",
    city: "Mississauga",
  },
};
