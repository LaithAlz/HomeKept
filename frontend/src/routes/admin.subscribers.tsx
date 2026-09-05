import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";

const searchSchema = z.object({
  id: z.coerce.number().int().positive().optional(),
});

/**
 * Layout-only route for the `/admin/subscribers` prefix: it exists purely to run the
 * `?id=` redirect below for every URL under this prefix, then hands off to whichever
 * child actually matched (the list at `/admin/subscribers/`, or a record at
 * `/admin/subscribers/$id`) via `<Outlet />`. It renders nothing of its own — TanStack
 * Router only renders a matched child route if some ancestor's component includes an
 * `Outlet`, so this file can't also carry the list page's markup the way it used to
 * (that would render the list underneath the detail page too, not instead of it).
 */
export const Route = createFileRoute("/admin/subscribers")({
  validateSearch: zodValidator(searchSchema),
  // `/admin/subscribers?id=N` used to open a side-panel over this list; the panel
  // is gone (issue: "get rid of the sliding side-panel"), replaced by a real page
  // at `/admin/subscribers/$id`. This keeps every existing deep link working
  // (the dashboard and the routes dispatch board both still build this URL) by
  // bouncing straight to the new route instead of breaking the link. `replace`
  // so the old query-string URL doesn't linger in history.
  beforeLoad: ({ search }) => {
    if (search.id !== undefined) {
      throw redirect({
        to: "/admin/subscribers/$id",
        params: { id: String(search.id) },
        replace: true,
      });
    }
  },
  component: () => <Outlet />,
});
