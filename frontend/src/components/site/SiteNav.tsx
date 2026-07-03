import { useState } from "react";
import { Link } from "@tanstack/react-router";
import { Menu, X } from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";

const links = [
  { to: "/#how", label: "How it works" },
  { to: "/#services", label: "What's included" },
  { to: "/plans", label: "Plans" },
] as const;

export function SiteNav() {
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-4 z-50 px-4 sm:px-5">
      <nav className="mx-auto w-full max-w-6xl rounded-3xl border border-border bg-card/85 shadow-soft backdrop-blur-xl">
        <div className="flex h-14 items-center justify-between gap-3 pl-5 pr-2">
          <Link to="/" className="flex items-center" aria-label="HomeKept home">
            <Wordmark size="sm" />
          </Link>

          <div className="hidden items-center gap-1 md:flex">
            {links.map((l) => (
              <a
                key={l.to}
                href={l.to}
                className="rounded-full px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-surface hover:text-primary"
              >
                {l.label}
              </a>
            ))}
          </div>

          <div className="flex items-center gap-1">
            <Button asChild variant="accent" size="sm" className="hidden sm:inline-flex">
              <Link to="/book">Book free walk-through</Link>
            </Button>
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
        </div>

        {open && (
          <div className="border-t border-border px-3 py-3 md:hidden">
            <div className="flex flex-col gap-1">
              {links.map((l) => (
                <a
                  key={l.to}
                  href={l.to}
                  className="rounded-2xl px-4 py-3 text-base font-medium hover:bg-surface"
                  onClick={() => setOpen(false)}
                >
                  {l.label}
                </a>
              ))}
              <Button asChild variant="accent" className="mt-2 w-full" size="lg">
                <Link to="/book" onClick={() => setOpen(false)}>
                  Book free walk-through
                </Link>
              </Button>
            </div>
          </div>
        )}
      </nav>
    </header>
  );
}
