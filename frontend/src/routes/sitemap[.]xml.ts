import { createFileRoute } from "@tanstack/react-router";
import type {} from "@tanstack/react-start";
import { BASE_URL } from "@/lib/seo";
import { ARTICLES } from "./learn.$slug";

interface SitemapEntry {
  path: string;
  changefreq?: "weekly" | "monthly" | "yearly";
  priority?: string;
}

export const Route = createFileRoute("/sitemap.xml")({
  server: {
    handlers: {
      GET: async () => {
        const articleEntries: SitemapEntry[] = ARTICLES.map((a) => ({
          path: `/learn/${a.slug}`,
          changefreq: "yearly" as const,
          priority: "0.5",
        }));

        const entries: SitemapEntry[] = [
          { path: "/", changefreq: "weekly", priority: "1.0" },
          { path: "/plans", changefreq: "monthly", priority: "0.8" },
          { path: "/book", changefreq: "monthly", priority: "0.9" },
          { path: "/oakville", changefreq: "monthly", priority: "0.8" },
          { path: "/mississauga", changefreq: "monthly", priority: "0.8" },
          { path: "/milton", changefreq: "monthly", priority: "0.8" },
          ...articleEntries,
        ];

        const urls = entries.map((e) =>
          [
            `  <url>`,
            `    <loc>${BASE_URL}${e.path}</loc>`,
            e.changefreq ? `    <changefreq>${e.changefreq}</changefreq>` : null,
            e.priority ? `    <priority>${e.priority}</priority>` : null,
            `  </url>`,
          ]
            .filter(Boolean)
            .join("\n"),
        );

        const xml = [
          `<?xml version="1.0" encoding="UTF-8"?>`,
          `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">`,
          ...urls,
          `</urlset>`,
        ].join("\n");

        return new Response(xml, {
          headers: {
            "Content-Type": "application/xml",
            "Cache-Control": "public, max-age=3600",
          },
        });
      },
    },
  },
});
