import { useState } from "react";
import { Link } from "@tanstack/react-router";
import { Menu, X } from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";

const links = [
  { to: "/#how", label: "How it works" },
  { to: "/plans", label: "Plans" },
  { to: "/signin", label: "Sign in" },
] as const;

export function SiteNav() {
  const [open, setOpen] = useState(false);

  return (
    <nav className="sticky top-0 z-50 w-full border-b border-border bg-background/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6 md:h-20">
        <Link to="/" className="flex items-center" aria-label="HomeKept home">
          <Wordmark size="md" />
        </Link>

        <div className="hidden items-center gap-8 md:flex">
          {links.map((l) => (
            <a
              key={l.to}
              href={l.to}
              className="text-sm font-medium text-foreground/80 transition-colors hover:text-accent"
            >
              {l.label}
            </a>
          ))}
          <Button asChild>
            <Link to="/book">Book walk-through</Link>
          </Button>
        </div>

        <button
          type="button"
          className="inline-flex size-11 items-center justify-center rounded-full text-foreground md:hidden"
          aria-label={open ? "Close menu" : "Open menu"}
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </div>

      {open && (
        <div className="border-t border-border bg-background md:hidden">
          <div className="mx-auto flex max-w-7xl flex-col gap-1 px-6 py-4">
            {links.map((l) => (
              <a
                key={l.to}
                href={l.to}
                className="rounded-xl px-3 py-3 text-base font-medium hover:bg-surface"
                onClick={() => setOpen(false)}
              >
                {l.label}
              </a>
            ))}
            <Button asChild className="mt-2 w-full" size="lg">
              <Link to="/book" onClick={() => setOpen(false)}>
                Book walk-through
              </Link>
            </Button>
          </div>
        </div>
      )}
    </nav>
  );
}
