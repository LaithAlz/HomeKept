import { createFileRoute, redirect } from "@tanstack/react-router";

/**
 * Old bookmark for the subscribers list, now `/admin/customers` (see `admin.subscribers.tsx`).
 * Nothing to render — this always redirects.
 */
export const Route = createFileRoute("/admin/subscribers/")({
  beforeLoad: () => {
    throw redirect({ to: "/admin/customers", replace: true });
  },
});
