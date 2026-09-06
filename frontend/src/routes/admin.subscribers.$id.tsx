import { createFileRoute, redirect } from "@tanstack/react-router";

/**
 * Old bookmark for a subscriber record, now `/admin/customers/$id` (see
 * `admin.subscribers.tsx`). Nothing to render — this always redirects, carrying the
 * same `id` param through.
 */
export const Route = createFileRoute("/admin/subscribers/$id")({
  beforeLoad: ({ params }) => {
    throw redirect({ to: "/admin/customers/$id", params: { id: params.id }, replace: true });
  },
});
