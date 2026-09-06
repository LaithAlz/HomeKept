import { createFileRoute, Outlet } from "@tanstack/react-router";

/**
 * Layout-only route for the `/admin/visits` prefix: renders nothing of its own, just
 * hands off to whichever child actually matched (the list at `/admin/visits/`, or a
 * single visit's detail at `/admin/visits/$id`) via `<Outlet />`. Mirrors
 * `routes/admin.subscribers.tsx`'s layout shape — see that file for why a page that
 * needs both a list and a detail route under the same prefix can't also carry the
 * list's own markup here (an ancestor's component has to render an `Outlet` for a
 * matched child route to appear at all).
 */
export const Route = createFileRoute("/admin/visits")({
  component: () => <Outlet />,
});
