import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";

const searchSchema = z.object({
  id: z.coerce.number().int().positive().optional(),
});

/**
 * `/admin/subscribers` moved to `/admin/customers` (issue: "I don't want a subscribers
 * page, I want a customers page"). This whole prefix is kept only as a redirect shim so
 * a bookmarked or previously-shared link still lands somewhere sensible:
 *   - `/admin/subscribers?id=N` → straight to the record page.
 *   - every other path under this prefix (the bare list, or `/admin/subscribers/$id`) is
 *     redirected by its own child route — see `admin.subscribers.index.tsx` and
 *     `admin.subscribers.$id.tsx`.
 */
export const Route = createFileRoute("/admin/subscribers")({
  validateSearch: zodValidator(searchSchema),
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
