/**
 * SEO constants and helpers.
 *
 * Centralises the canonical base URL so sitemap.xml, canonical <link> tags,
 * and JSON-LD @id values all stay in sync from one place.
 */

import { PLANS } from "@/lib/plans";

export const BASE_URL = "https://homekept.ca";

/** Brand OG image used as the fallback on every page. */
export const OG_IMAGE_DEFAULT = `${BASE_URL}/og/default.png`;

/** Build a full canonical URL from a path (must start with /). */
export function canonicalUrl(path: string): string {
  return `${BASE_URL}${path}`;
}

/** Shared organisation identity re-used in every JSON-LD graph. */
export const ORG_IDENTITY = {
  "@type": "HomeAndConstructionBusiness",
  "@id": `${BASE_URL}/#organization`,
  name: "HomeKept",
  url: BASE_URL,
  logo: `${BASE_URL}/logo.png`,
  telephone: "",
  email: "hello@homekept.ca",
  areaServed: [
    { "@type": "City", name: "Oakville", containedInPlace: { "@type": "State", name: "Ontario" } },
    {
      "@type": "City",
      name: "Mississauga",
      containedInPlace: { "@type": "State", name: "Ontario" },
    },
    { "@type": "City", name: "Milton", containedInPlace: { "@type": "State", name: "Ontario" } },
  ],
  priceRange: "$$",
} as const;

/**
 * Build the LocalBusiness + Service JSON-LD graph for the landing page.
 * Pass `areaServedOverride` to narrow areaServed to a single city page.
 */
export function buildLocalBusinessSchema(opts?: { cityName?: string; cityUrl?: string }): object {
  const areaServed = opts?.cityName
    ? [
        {
          "@type": "City",
          name: opts.cityName,
          containedInPlace: { "@type": "State", name: "Ontario" },
        },
      ]
    : ORG_IDENTITY.areaServed;

  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": ["LocalBusiness", "HomeAndConstructionBusiness"],
        "@id": opts?.cityUrl ? `${opts.cityUrl}#organization` : `${BASE_URL}/#organization`,
        name: "HomeKept",
        description:
          "Subscription home maintenance for homeowners in Oakville, Mississauga, and Milton. Scheduled visits, seasonal checklists, photo reports.",
        url: opts?.cityUrl ?? BASE_URL,
        logo: `${BASE_URL}/logo.png`,
        email: "hello@homekept.ca",
        priceRange: "$$",
        currenciesAccepted: "CAD",
        paymentAccepted: "Credit Card",
        areaServed,
        serviceArea: areaServed,
      },
      {
        "@type": "Service",
        "@id": `${BASE_URL}/#service`,
        name: "Home Maintenance Subscription",
        serviceType: "Home Maintenance",
        provider: {
          "@id": opts?.cityUrl ? `${opts.cityUrl}#organization` : `${BASE_URL}/#organization`,
        },
        areaServed,
        description:
          "Monthly subscription covering HVAC filters, gutter clearing, smoke detector tests, plumbing inspections, and seasonal home checks.",
        // Sourced from lib/plans.ts (docs/pricing-and-visits.md) so these
        // numbers can never diverge from what's shown on the plans page.
        offers: PLANS.map((plan) => ({
          "@type": "Offer",
          name: `${plan.name} Plan`,
          price: String(plan.monthlyPriceCad),
          priceCurrency: "CAD",
          priceSpecification: {
            "@type": "UnitPriceSpecification",
            price: String(plan.monthlyPriceCad),
            priceCurrency: "CAD",
            billingIncrement: 1,
            unitCode: "MON",
          },
        })),
      },
    ],
  };
}
