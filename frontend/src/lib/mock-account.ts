// Mock subscriber data for the customer dashboard.
// Replace with real data fetching once auth + backend are wired up.
//
// Visit and activity data has moved to real fetches (`@/lib/visits`) — see
// GitHub issue #32. What's left here (profile, plan, address, home health)
// still has no backing GET endpoint and stays mock until that lands.

export interface HomeHealth {
  score: number; // 0-100
  delta: number; // change from previous quarter
  note: string; // human-readable interpretation
}

export interface Subscriber {
  firstName: string;
  lastName: string;
  email: string;
  planName: "Essential" | "Complete" | "Premier";
  address: {
    street: string;
    neighbourhood: string;
    city: string;
  };
  health: HomeHealth;
}

export const subscriber: Subscriber = {
  firstName: "Priya",
  lastName: "Sharma",
  email: "priya.sharma@example.com",
  planName: "Complete",
  address: {
    street: "14 Maple Ridge Crt",
    neighbourhood: "Erin Mills",
    city: "Mississauga",
  },
  health: {
    score: 84,
    delta: 6,
    note: "Up 6 from last quarter. One item flagged for attention: dryer vent cleaning.",
  },
};
