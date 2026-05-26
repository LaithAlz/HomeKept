import { createFileRoute } from "@tanstack/react-router";
import { AdminShell } from "@/components/admin/AdminShell";

export const Route = createFileRoute("/admin")({
  head: () => ({
    meta: [
      { title: "Admin — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: AdminShell,
});
