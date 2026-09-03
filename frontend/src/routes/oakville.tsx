import { createFileRoute } from "@tanstack/react-router";
import { BASE_URL, OG_IMAGE_DEFAULT, buildLocalBusinessSchema } from "@/lib/seo";
import { CityPage, type CityConfig } from "@/components/marketing/CityPage";

const CITY_PATH = "/oakville";
const CITY_URL = `${BASE_URL}${CITY_PATH}`;

export const Route = createFileRoute("/oakville")({
  head: () => ({
    meta: [
      {
        title: "Home Maintenance Service Oakville: HomeKept",
      },
      {
        name: "description",
        content:
          "HomeKept provides monthly home maintenance subscriptions in Oakville, ON. We serve Glen Abbey, Iroquois Ridge, Bronte, Palermo, and more. HVAC, gutters, plumbing, seasonal checks.",
      },
      {
        property: "og:title",
        content: "Home Maintenance Subscription in Oakville: HomeKept",
      },
      {
        property: "og:description",
        content:
          "Scheduled visits, seasonal checklists, photo reports. Serving Oakville neighbourhoods including Glen Abbey, River Oaks, Bronte, and Iroquois Ridge.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: CITY_URL },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
      {
        "script:ld+json": buildLocalBusinessSchema({
          cityName: "Oakville",
          cityUrl: CITY_URL,
        }),
      },
    ],
    links: [{ rel: "canonical", href: CITY_URL }],
  }),
  component: OakvillePage,
});

// Oakville FSAs (Forward Sortation Areas — first 3 characters of postal code).
// Sources: Canada Post, Statistics Canada 2021 Census geography.
const FSAS = ["L6H", "L6J", "L6K", "L6L", "L6M"];

// Oakville neighbourhoods — real, verifiable.
const NEIGHBOURHOODS = [
  "Glen Abbey",
  "Bronte",
  "River Oaks",
  "Iroquois Ridge North",
  "Iroquois Ridge South",
  "Palermo Village",
  "Joshua Creek",
  "Old Oakville",
  "West Oak Trails",
  "Clearview",
];

// Oakville-specific seasonal home-maintenance pain points.
const SEASONAL = [
  {
    season: "Winter",
    heading: "Ice dams and attic moisture",
    body: "Oakville's proximity to Lake Ontario keeps winters damp. Ice dams form where inadequate attic insulation lets heat escape at the roof edge. A visual attic check every winter catches moisture before it migrates to ceilings.",
  },
  {
    season: "Spring",
    heading: "Sump pumps after snowmelt and spring rain",
    body: "Flat sections of Bronte, River Oaks, and the Sixteen Mile Creek corridor are prone to high water tables in March and April. A sump pump test and pit clean before the melt season is one of the highest-value preventive tasks in Oakville.",
  },
  {
    season: "Summer",
    heading: "AC performance on humid lake-effect days",
    body: "Summer humidity near the lake drives AC units hard. A condenser clean and performance check in June keeps your system efficient through the heavy-use months and helps avoid emergency calls in July.",
  },
  {
    season: "Fall",
    heading: "Gutters after Oakville's canopy sheds",
    body: "Mature oak and maple canopy, especially in Glen Abbey and Old Oakville, fills gutters fast in October and November. A post-leaf clearing before freeze prevents ice load and fascia damage through the winter.",
  },
];

const CITY: CityConfig = {
  cityName: "Oakville",
  fsas: FSAS,
  neighbourhoods: NEIGHBOURHOODS,
  postalIntro: "We serve all Oakville addresses whose postal code begins with:",
  fsaContainerClassName: "mt-4 flex flex-wrap gap-2.5",
  fsaPillClassName:
    "inline-block rounded-full border border-border bg-background px-4 py-1.5 font-mono text-sm font-semibold text-primary",
  seasonalIntro:
    "Living near Lake Ontario means your home faces specific pressures each season. Our visits are built around the Oakville calendar.",
  seasonal: SEASONAL,
};

function OakvillePage() {
  return <CityPage city={CITY} />;
}
