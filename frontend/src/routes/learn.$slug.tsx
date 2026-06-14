/**
 * Seasonal article scaffold — /learn/:slug
 *
 * These pages are CONTENT SCAFFOLDS. The route, metadata, and structure exist;
 * the founder writes the article body for each page.
 *
 * Slugs correspond to the 12-month visit calendar from docs/pricing-and-visits.md.
 * Each article mirrors the matching video episode topic (V3–V14 in the video series).
 *
 * TODO (founder): Replace the placeholder body in each ARTICLES entry with real content.
 * The title, description, and slug should be reviewed for SEO before launch.
 */
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { ArrowRight, ArrowLeft, Calendar } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { BASE_URL, OG_IMAGE_DEFAULT } from "@/lib/seo";

// ---------------------------------------------------------------------------
// Article registry
// Each entry drives: URL slug, page title, meta description, and sitemap entry.
// The founder writes `body` content before launch.
// ---------------------------------------------------------------------------

export type ArticleSlug =
  | "ontario-home-maintenance-january"
  | "ontario-home-maintenance-february"
  | "ontario-home-maintenance-march"
  | "ontario-home-maintenance-april"
  | "ontario-home-maintenance-may"
  | "ontario-home-maintenance-june"
  | "ontario-home-maintenance-july"
  | "ontario-home-maintenance-august"
  | "ontario-home-maintenance-september"
  | "ontario-home-maintenance-october"
  | "ontario-home-maintenance-november"
  | "ontario-home-maintenance-december";

export interface Article {
  slug: ArticleSlug;
  month: string;
  title: string;
  description: string;
  /** Founder writes this before launch. Placeholder shown until then. */
  bodyPlaceholder: string;
}

export const ARTICLES: Article[] = [
  {
    slug: "ontario-home-maintenance-january",
    month: "January",
    title: "What Your Ontario Home Needs in January — HomeKept",
    description:
      "Mid-winter home maintenance checklist for Ontario homeowners: furnace filter check, water heater flush, attic ice-dam inspection, humidity tuning, and smoke detector sweep.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: mid-season filter, water heater flush, attic peek for ice dams, humidity tune, detector sweep (peak CO season).",
  },
  {
    slug: "ontario-home-maintenance-february",
    month: "February",
    title: "What Your Ontario Home Needs in February — HomeKept",
    description:
      "Deep-winter home care in Ontario: condensation and draft checks, basement moisture scan, tub and shower re-caulking, and garage door tune-up.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: condensation/draft check, basement moisture scan, tub/shower caulking, garage door tune.",
  },
  {
    slug: "ontario-home-maintenance-march",
    month: "March",
    title: "What Your Ontario Home Needs in March — HomeKept",
    description:
      "Spring thaw prep for Ontario homes: sump pump test and pit cleaning, snowmelt drainage and grading check, foundation walkaround, and floor drain inspection.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: sump pump test & pit clean, melt drainage/grading, foundation walkaround, floor drains.",
  },
  {
    slug: "ontario-home-maintenance-april",
    month: "April",
    title: "What Your Ontario Home Needs in April — HomeKept",
    description:
      "Spring readiness for Ontario homeowners: reconnecting outdoor taps, AC startup observation, spring gutter clearing, and winter-damage walkaround.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: reconnect/test outdoor taps, AC startup observation, spring gutter clear, winter-damage walkaround.",
  },
  {
    slug: "ontario-home-maintenance-may",
    month: "May",
    title: "What Your Ontario Home Needs in May — HomeKept",
    description:
      "May exterior maintenance for Ontario homes: deck, railing, and fence hardware checks, screen inspection, exterior caulking, and irrigation hose check.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: deck/railing/fence hardware, screens, exterior caulking touch-points, irrigation/hose check.",
  },
  {
    slug: "ontario-home-maintenance-june",
    month: "June",
    title: "What Your Ontario Home Needs in June — HomeKept",
    description:
      "Summer prep for Ontario homes: full exterior caulking pass, AC condenser clean, bathroom fan cleaning, and drainage recheck.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: full exterior caulking pass, AC condenser clean (power off), bath fan clean, drainage recheck.",
  },
  {
    slug: "ontario-home-maintenance-july",
    month: "July",
    title: "What Your Ontario Home Needs in July — HomeKept",
    description:
      "Summer systems check for Ontario homeowners: AC performance observation, under-sink and appliance leak inspection, dryer vent deep clean, and washer hose check.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: AC performance check, under-sink/toilet/appliance leak inspection, dryer vent deep clean, washer hoses.",
  },
  {
    slug: "ontario-home-maintenance-august",
    month: "August",
    title: "What Your Ontario Home Needs in August — HomeKept",
    description:
      "Water system maintenance for Ontario homes in August: water heater visual and temperature check, water pressure test, toilet internals, and sump pump recheck.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: water heater visual & temp, water pressure test, toilet internals, sump recheck.",
  },
  {
    slug: "ontario-home-maintenance-september",
    month: "September",
    title: "What Your Ontario Home Needs in September — HomeKept",
    description:
      "Pre-heating season checklist for Ontario homes: furnace filter and visual inspection, humidifier pad replacement, weatherstripping pass, and gas tune-up coordination.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: filter & furnace visual/performance, humidifier pad, weatherstripping pass, book licensed gas tune-up if due.",
  },
  {
    slug: "ontario-home-maintenance-october",
    month: "October",
    title: "What Your Ontario Home Needs in October — HomeKept",
    description:
      "Fall winterization for Ontario homes: shutting down outdoor taps, humidifier service, weatherstripping and door sweeps, eaves check, and detector sweep.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: shut down & drain outdoor taps, humidifier service, weatherstripping/door sweeps, eaves check, detector sweep.",
  },
  {
    slug: "ontario-home-maintenance-november",
    month: "November",
    title: "What Your Ontario Home Needs in November — HomeKept",
    description:
      "Post-leaf season home maintenance in Ontario: full gutter and downspout clearing, roof-line visual from grade, and downspout extension check.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: full gutter & downspout clear, roof-line visual (grade/eaves only), downspout extensions.",
  },
  {
    slug: "ontario-home-maintenance-december",
    month: "December",
    title: "What Your Ontario Home Needs in December — HomeKept",
    description:
      "Holiday season home safety checklist for Ontario homes: smoke and CO detector tests, fire extinguisher check, dryer vent recheck, and cord and space-heater walkthrough.",
    bodyPlaceholder:
      "// TODO: founder writes content — topic: detectors + extinguisher, dryer vent recheck, cord/space-heater walkaround, your-list catch-up.",
  },
];

// Map slug → article for O(1) lookup
const ARTICLE_MAP = new Map<string, Article>(ARTICLES.map((a) => [a.slug, a]));

export const Route = createFileRoute("/learn/$slug")({
  head: ({ params }) => {
    const article = ARTICLE_MAP.get(params.slug);
    if (!article) {
      return { meta: [{ title: "Article not found — HomeKept" }] };
    }
    const url = `${BASE_URL}/learn/${article.slug}`;
    return {
      meta: [
        { title: article.title },
        { name: "description", content: article.description },
        { property: "og:title", content: article.title },
        { property: "og:description", content: article.description },
        { property: "og:type", content: "article" },
        { property: "og:url", content: url },
        { property: "og:image", content: OG_IMAGE_DEFAULT },
      ],
      links: [{ rel: "canonical", href: url }],
    };
  },
  loader: ({ params }) => {
    const article = ARTICLE_MAP.get(params.slug);
    if (!article) throw notFound();
    return { article };
  },
  component: LearnArticlePage,
  notFoundComponent: ArticleNotFound,
});

function LearnArticlePage() {
  const { article } = Route.useLoaderData();
  const monthIndex = ARTICLES.findIndex((a) => a.slug === article.slug);
  const prevArticle = monthIndex > 0 ? ARTICLES[monthIndex - 1] : null;
  const nextArticle = monthIndex < ARTICLES.length - 1 ? ARTICLES[monthIndex + 1] : null;

  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main">
        {/* Article header */}
        <header className="border-b border-border bg-surface/60">
          <div className="mx-auto max-w-3xl px-6 py-16 md:py-20">
            <Link
              to="/"
              className="mb-6 inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-primary"
            >
              <ArrowLeft className="size-4" aria-hidden="true" />
              Back to HomeKept
            </Link>
            <div className="flex items-center gap-3">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-moss/15 px-3 py-1 text-xs font-bold uppercase tracking-wider text-moss">
                <Calendar className="size-3" aria-hidden="true" />
                {article.month}
              </span>
              <span className="text-xs text-muted-foreground">Seasonal guide · Ontario</span>
            </div>
            <h1 className="mt-5 font-display text-[clamp(28px,4.5vw,56px)] font-[560] leading-[1.06] tracking-[-0.02em] text-primary">
              {article.title.replace(" — HomeKept", "")}
            </h1>
            <p className="mt-5 text-lg leading-relaxed text-muted-foreground">
              {article.description}
            </p>
          </div>
        </header>

        {/* Placeholder body — founder replaces this with real content */}
        <article
          className="mx-auto max-w-3xl px-6 py-16 md:py-20"
          aria-label={`Article: ${article.title}`}
        >
          <div className="rounded-[20px] border border-dashed border-border bg-surface/40 p-10 text-center">
            <p className="font-display text-xl font-semibold text-primary">Content coming soon</p>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
              This seasonal guide is being written. In the meantime, book a free walk-through and
              we'll walk you through what your home needs this month.
            </p>
            <div className="mt-8">
              <Button asChild size="lg">
                <Link to="/book">
                  Book free walk-through <ArrowRight className="size-4" />
                </Link>
              </Button>
            </div>
          </div>

          {/* Month navigation */}
          <nav
            aria-label="Article navigation"
            className="mt-16 flex items-center justify-between gap-4 border-t border-border pt-8"
          >
            {prevArticle ? (
              <Link
                to="/learn/$slug"
                params={{ slug: prevArticle.slug }}
                className="group flex min-h-[44px] max-w-[45%] flex-col justify-center gap-1 text-left"
              >
                <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                  <ArrowLeft
                    className="size-3.5 transition-transform group-hover:-translate-x-1"
                    aria-hidden="true"
                  />
                  Previous
                </span>
                <span className="text-sm font-semibold text-primary group-hover:underline">
                  {prevArticle.month}
                </span>
              </Link>
            ) : (
              <div />
            )}
            {nextArticle ? (
              <Link
                to="/learn/$slug"
                params={{ slug: nextArticle.slug }}
                className="group flex min-h-[44px] max-w-[45%] flex-col items-end justify-center gap-1 text-right"
              >
                <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                  Next
                  <ArrowRight
                    className="size-3.5 transition-transform group-hover:translate-x-1"
                    aria-hidden="true"
                  />
                </span>
                <span className="text-sm font-semibold text-primary group-hover:underline">
                  {nextArticle.month}
                </span>
              </Link>
            ) : (
              <div />
            )}
          </nav>
        </article>

        {/* Inline CTA */}
        <section className="border-t border-border bg-surface px-6 py-16">
          <div className="mx-auto max-w-3xl text-center">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-accent">
              Don't do it alone
            </p>
            <h2 className="mt-3 font-display text-2xl font-semibold text-primary md:text-3xl">
              We handle this, every month.
            </h2>
            <p className="mx-auto mt-4 max-w-[46ch] text-sm leading-relaxed text-muted-foreground">
              HomeKept subscribers get this seasonal work done on a schedule — no to-do list, no
              remembering, just a photo report after every visit.
            </p>
            <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
              <Button asChild size="lg" variant="accent">
                <Link to="/book">Book free walk-through</Link>
              </Button>
              <Button asChild size="lg" variant="outline">
                <Link to="/plans">See plans</Link>
              </Button>
            </div>
          </div>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}

function ArticleNotFound() {
  return (
    <div className="flex min-h-dvh flex-col">
      <SiteNav />
      <main id="main" className="flex flex-1 items-center justify-center px-6 py-20">
        <div className="max-w-md text-center">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">404</p>
          <h1 className="mt-4 font-display text-3xl font-semibold text-primary">
            Article not found
          </h1>
          <p className="mt-3 text-sm text-muted-foreground">
            That article doesn't exist yet. Browse the seasonal guides or book a walk-through.
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <Button asChild size="lg">
              <Link to="/book">Book walk-through</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link to="/">Back to home</Link>
            </Button>
          </div>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
