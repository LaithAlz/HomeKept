{
  /* DRAFT — not legal advice. Founder/lawyer must review before launch. */
}
import { createFileRoute, Link } from "@tanstack/react-router";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { OG_IMAGE_DEFAULT, canonicalUrl } from "@/lib/seo";

export const Route = createFileRoute("/terms")({
  head: () => ({
    meta: [
      { title: "Terms of Service: HomeKept" },
      {
        name: "description",
        content:
          "HomeKept terms of service: what's included in your subscription, billing and cancellation, scope of work, property access, and governing law (Ontario, Canada).",
      },
      { property: "og:title", content: "Terms of Service: HomeKept" },
      {
        property: "og:description",
        content:
          "Subscription terms, billing, service scope, and your rights as a HomeKept member.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: canonicalUrl("/terms") },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/terms") }],
  }),
  component: TermsPage,
});

function TermsPage() {
  return (
    <div className="min-h-dvh bg-background">
      <SiteNav />
      <main id="main">
        <article className="mx-auto max-w-3xl px-6 pb-28 pt-16 md:pt-24">
          {/* Draft notice — visible, prominent */}
          <div
            role="note"
            className="mb-10 rounded-2xl border border-warning/40 bg-warning/10 px-6 py-4 text-sm text-foreground"
          >
            <strong className="font-semibold text-primary">Draft: pending legal review.</strong>{" "}
            This document has not yet been reviewed by a lawyer and is not final. Do not rely on it
            as legal advice.
          </div>

          <header className="mb-12">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-accent">Legal</p>
            <h1 className="mt-3 font-display text-[clamp(30px,4.5vw,56px)] font-[560] leading-[1.07] tracking-[-0.02em] text-primary">
              Terms of Service
            </h1>
            {/* FLAG FOR FOUNDER: replace placeholder date before launch */}
            <p className="mt-4 text-sm text-muted-foreground">
              Last updated: <time dateTime="YYYY-MM-DD">[Date to be confirmed before launch]</time>
            </p>
          </header>

          <div className="prose-legal">
            <Section heading="1. Agreement">
              <p>
                By booking a walk-through, activating a HomeKept subscription, or otherwise using
                HomeKept's service, you ("you" or "the member") agree to these Terms of Service. If
                you do not agree, do not use the service.
              </p>
              <p>
                HomeKept reserves the right to update these terms. Material changes will be
                communicated by email to active members and reflected in the "last updated" date
                above. Continued use after the effective date constitutes acceptance.
              </p>
            </Section>

            <Section heading="2. The service">
              <p>
                HomeKept provides a subscription home-maintenance service. Depending on your plan,
                this includes:
              </p>
              <ul>
                <li>Scheduled visits by HomeKept technicians to your property.</li>
                <li>
                  A free, no-obligation walk-through before you subscribe, during which we assess
                  your home and build a written maintenance plan.
                </li>
                <li>
                  Routine maintenance tasks and observational checks performed on each visit
                  (details vary by plan: see{" "}
                  <Link
                    to="/plans"
                    className="font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                  >
                    our plans page
                  </Link>
                  ).
                </li>
                <li>A photo report delivered to you after each visit.</li>
                <li>
                  A home health score maintained in your member dashboard, updated after each visit.
                </li>
              </ul>
              <p>
                Three subscription tiers are available: <strong>Essential</strong>,{" "}
                <strong>Complete</strong>, and <strong>Premier</strong>. Plan features are described
                on{" "}
                <Link
                  to="/plans"
                  className="font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                >
                  our plans page
                </Link>
                .
              </p>
            </Section>

            <Section heading="3. Scope of work: what HomeKept does and does not do">
              <p>
                HomeKept performs routine home maintenance and visual/observational checks. Examples
                include: HVAC filter replacements, gutter clearing, smoke and CO detector tests,
                plumbing leak checks, sump pump tests, and seasonal exterior inspections.
              </p>
              <p>
                <strong>HomeKept does not perform licensed trade work.</strong> Any task requiring a
                licensed tradesperson, including but not limited to gas work, electrical panel work,
                structural repairs, plumbing re-piping, or HVAC refrigerant handling, is outside our
                scope. Where a visit reveals a condition that requires a licensed trade, we will
                document it in your report and{" "}
                <strong>refer it to an appropriate professional</strong>; we will not perform the
                work ourselves.
              </p>
              <p>
                HomeKept's observations during visits are visual and operational in nature. They do
                not constitute a home inspection, engineering assessment, or guarantee that no
                defect exists in any system.
              </p>
            </Section>

            <Section heading="4. Billing and cancellation">
              <ul>
                <li>
                  Subscriptions are billed monthly or annually in Canadian dollars (CAD) via Stripe.
                </li>
                <li>
                  Current prices are displayed on{" "}
                  <Link
                    to="/plans"
                    className="font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                  >
                    our plans page
                  </Link>
                  . HomeKept may adjust prices; active members will receive at least 30 days' notice
                  before any price change takes effect on their subscription.
                </li>
                <li>
                  <strong>You may cancel at any time</strong> from your account settings. Cancelling
                  stops future billing. Access to your member dashboard and historical reports is
                  maintained for a reasonable period after cancellation (see our Privacy Policy for
                  data retention details).
                </li>
                <li>
                  Annual subscribers who cancel before the end of their annual term are not entitled
                  to a pro-rated refund for the unused portion, except where required by applicable
                  Ontario consumer-protection law.
                </li>
                <li>
                  If a payment fails, we will attempt to reach you by email. Access to scheduled
                  visits may be paused while an account balance remains outstanding.
                </li>
              </ul>
            </Section>

            <Section heading="5. Property access">
              <p>
                By subscribing, you grant HomeKept and its technicians access to your property for
                the purpose of performing scheduled visits. You are responsible for:
              </p>
              <ul>
                <li>
                  Ensuring that the access information you provide (such as lockbox codes or alarm
                  codes) is accurate and kept up to date in your account settings.
                </li>
                <li>
                  Informing us of any access restrictions, safety considerations, or changes to
                  access details before a scheduled visit.
                </li>
                <li>
                  Ensuring that our technicians can safely access all areas of the property relevant
                  to the subscribed services (e.g. attic hatch, utility room, exterior taps).
                </li>
              </ul>
              <p>
                Access notes you provide are stored encrypted at rest and are accessible only to the
                technician assigned to your visit.
              </p>
            </Section>

            <Section heading="6. Acceptable use">
              <p>You agree not to:</p>
              <ul>
                <li>Provide false or misleading information about your property or its systems.</li>
                <li>
                  Use HomeKept's service for any property you do not own or are not authorised to
                  grant access to.
                </li>
                <li>
                  Misuse the member dashboard, reports, or any other part of the platform in a way
                  that could damage HomeKept or other members.
                </li>
              </ul>
              <p>
                HomeKept reserves the right to suspend or terminate a subscription if these
                conditions are materially breached, with notice where reasonably practicable.
              </p>
            </Section>

            <Section heading="7. Limitation of liability">
              <p>
                To the maximum extent permitted by applicable law, HomeKept's liability to you for
                any claim arising from or related to these terms or the service is limited to the
                total subscription fees you paid in the 12 months preceding the claim.
              </p>
              <p>
                HomeKept is not liable for indirect, consequential, incidental, or punitive damages
                of any kind, including loss of use, loss of data, or cost of substitute services,
                even if HomeKept was advised of the possibility of such damages.
              </p>
              <p>
                Nothing in these terms limits liability that cannot be excluded under Ontario or
                Canadian consumer-protection law.
              </p>
            </Section>

            <Section heading="8. Governing law">
              <p>
                These terms are governed by the laws of the Province of Ontario and the applicable
                federal laws of Canada, without regard to conflict-of-law principles. Any dispute
                arising from these terms shall be resolved in the courts of the Province of Ontario.
              </p>
            </Section>

            <Section heading="9. Contact">
              <p>
                Questions about these terms? Reach us at{" "}
                <a
                  href="mailto:hello@homekept.ca"
                  className="font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                >
                  hello@homekept.ca
                </a>
                .
              </p>
            </Section>

            <div className="mt-14 border-t border-border pt-8">
              <Link
                to="/privacy"
                className="text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                Read our Privacy Policy
              </Link>
            </div>
          </div>
        </article>
      </main>
      <SiteFooter />
    </div>
  );
}

function Section({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <section className="mb-10" aria-labelledby={sectionId(heading)}>
      <h2
        id={sectionId(heading)}
        className="mb-4 font-display text-xl font-[560] leading-snug tracking-[-0.015em] text-primary"
      >
        {heading}
      </h2>
      <div className="space-y-3 text-sm leading-relaxed text-muted-foreground">{children}</div>
    </section>
  );
}

function sectionId(heading: string) {
  return heading.replace(/[^a-z0-9]+/gi, "-").toLowerCase();
}
