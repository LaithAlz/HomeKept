// @lovable.dev/vite-tanstack-config already includes the following — do NOT add them manually
// or the app will break with duplicate plugins:
//   - tanstackStart, viteReact, tailwindcss, tsConfigPaths, cloudflare (build-only),
//     componentTagger (dev-only), VITE_* env injection, @ path alias, React/TanStack dedupe,
//     error logger plugins, and sandbox detection (port/host/strictPort).
// You can pass additional config via defineConfig({ vite: { ... } }) if needed.
import { defineConfig } from "@lovable.dev/vite-tanstack-config";

// Redirect TanStack Start's bundled server entry to src/server.ts (our SSR error wrapper).
// @cloudflare/vite-plugin builds from this — wrangler.jsonc main alone is insufficient.
export default defineConfig({
  tanstackStart: {
    server: { entry: "server" },
  },
  vite: {
    server: {
      // Dev-only: forward API calls to the local Spring backend so the app can use
      // same-origin /api paths everywhere (in production, Cloudflare routes /api/*
      // to the backend). Backend default port is 8080; override with BACKEND_ORIGIN
      // when running both locally (e.g. SERVER_PORT=8081 ./gradlew bootRun).
      proxy: {
        "/api": {
          target: process.env.BACKEND_ORIGIN ?? "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  },
});
