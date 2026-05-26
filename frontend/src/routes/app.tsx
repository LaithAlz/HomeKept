import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/app/AppShell";

export const Route = createFileRoute("/app")({
  head: () => ({
    meta: [
      { title: "Your home — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: AppShell,
});

