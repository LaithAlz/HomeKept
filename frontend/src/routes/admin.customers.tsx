import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";

const searchSchema = z.object({
  id: z.coerce.number().int().positive().optional(),
});

/**
 * Layout-only route for the `/admin/customers` prefix (formerly `/admin/subscribers` —
 * issue: "I don't want a subscribers page, I want a customers page"; the backend domain
 * term `subscriber` is unchanged, this is a UI-language rename only). It exists purely to
 * run the `?id=` redirect below for every URL under this prefix, then hands off to
 * whichever child actually matched (the list at `/admin/customers/`, or a record at
 * `/admin/customers/$id`) via `<Outlet />`. It renders nothing of its own — TanStack
 * Router only renders a matched child route if some ancestor's component includes an
 * `Outlet`, so this file can't also carry the list page's markup the way it used to
 * (that would render the list underneath the detail page too, not instead of it).
 */
export const Route = createFileRoute("/admin/customers")({
  validateSearch: zodValidator(searchSchema),
  // `/admin/customers?id=N` opens a side-panel-turned-page over this list — kept for
  // any link (dashboard cards, the routes dispatch board) that builds this query-string
  // form instead of the `$id` path form, both of which land on the same record page.
  beforeLoad: ({ search }) => {
    if (search.id !== undefined) {
      throw redirect({
        to: "/admin/customers/$id",
        params: { id: String(search.id) },
        replace: true,
      });
    }
  },
  component: () => <Outlet />,
});
