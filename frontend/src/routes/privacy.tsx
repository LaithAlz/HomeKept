{
  /* DRAFT — not legal advice. Founder/lawyer must review before launch. */
}
import { createFileRoute, Link } from "@tanstack/react-router";
import { SiteNav } from "@/components/site/SiteNav";
import { SiteFooter } from "@/components/site/SiteFooter";
import { OG_IMAGE_DEFAULT, canonicalUrl } from "@/lib/seo";

export const Route = createFileRoute("/privacy")({
  head: () => ({
    meta: [
      { title: "Privacy Policy: HomeKept" },
      {
        name: "description",
        content:
          "HomeKept privacy policy: what personal information we collect, how we use it, the third-party processors we rely on, and your rights under PIPEDA.",
      },
      { property: "og:title", content: "Privacy Policy: HomeKept" },
      {
        property: "og:description",
        content:
          "How HomeKept collects, uses, and protects your personal information under PIPEDA.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: canonicalUrl("/privacy") },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/privacy") }],
  }),
  component: PrivacyPage,
});

function PrivacyPage() {
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
              Privacy Policy
            </h1>
            {/* FLAG FOR FOUNDER: replace placeholder date before launch */}
            <p className="mt-4 text-sm text-muted-foreground">
              Last updated: <time dateTime="YYYY-MM-DD">[Date to be confirmed before launch]</time>
            </p>
          </header>

          <div className="prose-legal">
            <Section heading="1. Who we are">
              <p>
                HomeKept is a subscription home-maintenance service operating in Oakville,
                Mississauga, and Milton, Ontario, Canada. We are a service-area business with no
                public storefront; all services are performed at your property. References to
                "HomeKept", "we", "us", or "our" in this policy refer to the HomeKept business and
                its principals.
              </p>
            </Section>

            <Section heading="2. Governing law and consent">
              <p>
                Your personal information is collected and handled in accordance with Canada's{" "}
                <em>Personal Information Protection and Electronic Documents Act</em> (PIPEDA).
                Where we send you marketing communications, we comply with Canada's{" "}
                <em>Anti-Spam Legislation</em> (CASL) and only do so with your express or implied
                consent as defined under that legislation.
              </p>
            </Section>

            <Section heading="3. Information we collect">
              <p>
                We collect the following categories of personal information to deliver and improve
                our service:
              </p>

              <SubHeading>Account information</SubHeading>
              <ul>
                <li>Name, email address, and phone number, provided by you at sign-up.</li>
              </ul>

              <SubHeading>Property details</SubHeading>
              <ul>
                <li>
                  Street address, unit number, city, and postal code of the property we maintain.
                </li>
                <li>
                  Approximate latitude and longitude, and forward sortation area (FSA, the first
                  three characters of your postal code), derived from your address.
                </li>
                <li>Year built, approximate square-footage range, and property type.</li>
              </ul>

              <SubHeading>Home systems information (your "SKU sheet")</SubHeading>
              <ul>
                <li>
                  Details about your home's specific systems that let us bring the right materials
                  on every visit: HVAC filter sizes, smoke and CO detector models, humidifier model,
                  and water-heater age.
                </li>
              </ul>

              <SubHeading>Access notes</SubHeading>
              <ul>
                <li>
                  Information needed to access your property for scheduled visits, such as lockbox
                  codes or alarm codes.{" "}
                  <strong>Access notes are encrypted at rest using column-level encryption.</strong>{" "}
                  They are never logged in plain text and are accessible only to the technician
                  assigned to your visit.
                </li>
              </ul>

              <SubHeading>Visit data</SubHeading>
              <ul>
                <li>
                  Photographs taken during each visit, technician notes, maintenance flags raised,
                  and a home health score calculated from visit observations.
                </li>
              </ul>

              <SubHeading>Payment information</SubHeading>
              <ul>
                <li>
                  Billing is processed by Stripe. HomeKept does not store your full card number or
                  CVV. We receive and retain a Stripe customer ID, subscription ID, and the last
                  four digits of the card on file for display purposes only.
                </li>
              </ul>
            </Section>

            <Section heading="4. How we use your information">
              <p>We use the information we collect to:</p>
              <ul>
                <li>Schedule, perform, and report on home-maintenance visits.</li>
                <li>
                  Prepare your home for each visit by sourcing the correct materials in advance.
                </li>
                <li>Bill you for your subscription and manage your account.</li>
                <li>Send you visit summaries, reports, and service communications.</li>
                <li>
                  Send you marketing updates about HomeKept: only with your consent, as required by
                  CASL. You may withdraw consent at any time.
                </li>
                <li>
                  Measure and improve our service through privacy-conscious product analytics (see
                  section 6).
                </li>
                <li>Meet our legal obligations.</li>
              </ul>
            </Section>

            <Section heading="5. Third-party processors">
              <p>
                We share your information with the following third-party service providers to
                operate HomeKept. Each processor handles your data only as necessary for the purpose
                listed, under a data-processing agreement with us:
              </p>
              <ul>
                <li>
                  <strong>Stripe</strong> (United States): payment processing. Stripe's own privacy
                  policy governs how they handle card data.
                </li>
                <li>
                  <strong>Render</strong> (United States): our backend application server and
                  PostgreSQL database are hosted on Render. Your personal data resides on
                  Render-managed infrastructure in the US. PIPEDA requires that we ensure equivalent
                  protection regardless of where data is hosted, not that it be stored in Canada.
                </li>
                <li>
                  <strong>Vercel</strong>: website hosting and edge delivery for homekept.ca.
                </li>
                <li>
                  <strong>SendGrid</strong> (United States): transactional email (visit
                  confirmations, reports, account notifications).
                </li>
                <li>
                  <strong>Sentry</strong>: error monitoring. Error events may include technical
                  context (e.g. a URL or user ID) to help us diagnose problems.
                </li>
                <li>
                  <strong>PostHog</strong> (United States): product analytics. See section 6 for
                  details.
                </li>
                <li>
                  <strong>Cloudflare R2</strong> (United States): visit photographs are stored in
                  Cloudflare's R2 object storage. Photos are secured and accessible only to
                  authorised HomeKept personnel and to you via your account.
                </li>
              </ul>
              <p>
                We do not sell your personal information to third parties, nor do we share it with
                advertisers.
              </p>
            </Section>

            <Section heading="6. Analytics and cookies">
              <p>
                HomeKept uses <strong>PostHog</strong> for product analytics in a privacy-conscious
                configuration:
              </p>
              <ul>
                <li>
                  Analytics is <strong>cookieless</strong>: no tracking cookies are set on your
                  device.
                </li>
                <li>
                  We capture <strong>no session recordings</strong> and use{" "}
                  <strong>no advertising trackers</strong>.
                </li>
                <li>
                  Analytics events carry identifiers (such as an internal user ID or event count)
                  and not the personal details listed in section 3.
                </li>
              </ul>
              <p>
                Because we do not use cookies for analytics or advertising, and do not engage in
                cross-site tracking, we do not display a cookie-consent banner. If this practice
                changes, we will update this policy and add appropriate consent mechanisms.
              </p>
            </Section>

            <Section heading="7. Retention and deletion">
              <p>
                We retain your personal information while your HomeKept account is active and for a
                reasonable period after cancellation to meet our legal and operational obligations.
                Visit reports and photographs are retained for the periods described in our service
                terms.
              </p>
              <p>
                You may request deletion of your personal information at any time. A deletion
                request is processed as a PIPEDA access/correction/deletion request. Upon
                completion, we remove your personal data, including any linked analytics records,
                from our systems and instruct our processors to do the same, subject to any legal
                hold obligations. Some data may be retained in anonymised or aggregated form where
                it no longer identifies you.
              </p>
            </Section>

            <Section heading="8. Your rights under PIPEDA">
              <p>Under PIPEDA, you have the right to:</p>
              <ul>
                <li>Know what personal information we hold about you and request a copy.</li>
                <li>Correct inaccurate information.</li>
                <li>Withdraw consent for non-essential uses (such as marketing).</li>
                <li>
                  Request deletion of your personal information, subject to legal-hold exceptions.
                </li>
                <li>Lodge a complaint with the Office of the Privacy Commissioner of Canada.</li>
              </ul>
              <p>
                To exercise any of these rights, contact us at the address in section 9. We will
                respond within 30 days.
              </p>
            </Section>

            <Section heading="9. Contact">
              <p>
                For privacy questions, access requests, or deletion requests, contact our privacy
                lead:
              </p>
              {/* FLAG FOR FOUNDER: confirm privacy@homekept.ca is live before launch */}
              <p>
                Email:{" "}
                <a
                  href="mailto:privacy@homekept.ca"
                  className="font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                >
                  privacy@homekept.ca
                </a>
              </p>
              <p className="mt-2 text-sm text-muted-foreground">
                [FLAG: founder to confirm this address is active before launch]
              </p>
            </Section>

            <Section heading="10. Changes to this policy">
              <p>
                We may update this policy from time to time. When we make material changes, we will
                update the "last updated" date at the top of this page and, where appropriate,
                notify active subscribers by email. Continued use of HomeKept after a change
                constitutes acceptance of the updated policy.
              </p>
            </Section>

            <div className="mt-14 border-t border-border pt-8">
              <Link
                to="/terms"
                className="text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                Read our Terms of Service
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

function SubHeading({ children }: { children: React.ReactNode }) {
  return <h3 className="mb-1.5 mt-5 text-sm font-semibold text-foreground">{children}</h3>;
}

function sectionId(heading: string) {
  return heading.replace(/[^a-z0-9]+/gi, "-").toLowerCase();
}
