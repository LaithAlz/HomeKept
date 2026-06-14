import { Link } from "@tanstack/react-router";
import { Wordmark } from "@/components/brand/Wordmark";
import { ShieldCheck } from "lucide-react";

export function SiteFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto max-w-7xl px-6 py-16">
        <div className="grid gap-10 md:grid-cols-3">
          <div>
            <Wordmark size="md" />
            <p className="mt-4 flex items-start gap-2 text-sm text-muted-foreground">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
              <span>
                Insured &amp; bonded. Background-checked technicians. Family-run from the GTA.
              </span>
            </p>
          </div>

          <div>
            <h4 className="text-xs font-bold uppercase tracking-[0.18em] text-foreground">
              Service area
            </h4>
            <ul className="mt-4 space-y-2 text-sm text-muted-foreground">
              <li>Oakville</li>
              <li>Mississauga</li>
              <li>Milton</li>
            </ul>
          </div>

          <div>
            <h4 className="text-xs font-bold uppercase tracking-[0.18em] text-foreground">
              Company
            </h4>
            <ul className="mt-4 space-y-2 text-sm">
              <li>
                <Link to="/privacy" className="text-muted-foreground hover:text-accent">
                  Privacy
                </Link>
              </li>
              <li>
                <Link to="/terms" className="text-muted-foreground hover:text-accent">
                  Terms
                </Link>
              </li>
              <li>
                <a
                  href="mailto:hello@homekept.ca"
                  className="text-muted-foreground hover:text-accent"
                >
                  hello@homekept.ca
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-12 border-t border-border pt-6 text-xs text-muted-foreground">
          © {new Date().getFullYear()} HomeKept. Serving the Greater Toronto Area.
        </div>
      </div>
    </footer>
  );
}
