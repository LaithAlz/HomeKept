import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { Button } from "@/components/ui/button";

const searchSchema = z.object({
  plan: fallback(z.enum(["essential", "complete", "premier"]), "complete").default("complete"),
});

export const Route = createFileRoute("/checkout")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Checkout: HomeKept" },
      { name: "description", content: "Complete your HomeKept subscription." },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: CheckoutPage,
});

function CheckoutPage() {
  const { plan } = Route.useSearch();
  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main" className="mx-auto max-w-2xl px-6 py-24 text-center">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Checkout</p>
        <h1 className="mt-3 font-display text-4xl font-extrabold tracking-tight md:text-5xl">
          Almost there.
        </h1>
        <p className="mt-4 text-muted-foreground">
          You've selected the{" "}
          <span className="font-semibold capitalize text-foreground">{plan}</span> plan. Checkout
          flow coming next.
        </p>
        <div className="mt-8 flex justify-center gap-3">
          <Button asChild variant="outline">
            <Link to="/plans">Back to plans</Link>
          </Button>
          <Button asChild>
            <Link to="/book">Book a free walk-through</Link>
          </Button>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
