import { createFileRoute } from "@tanstack/react-router";
import { BASE_URL, OG_IMAGE_DEFAULT, buildLocalBusinessSchema } from "@/lib/seo";
import { CityPage, type CityConfig } from "@/components/marketing/CityPage";

const CITY_PATH = "/mississauga";
const CITY_URL = `${BASE_URL}${CITY_PATH}`;

export const Route = createFileRoute("/mississauga")({
  head: () => ({
    meta: [
      {
        title: "Home Maintenance Service Mississauga: HomeKept",
      },
      {
        name: "description",
        content:
          "HomeKept provides monthly home maintenance subscriptions in Mississauga, ON. We serve Port Credit, Lorne Park, Erin Mills, Streetsville, Meadowvale, and more.",
      },
      {
        property: "og:title",
        content: "Home Maintenance Subscription in Mississauga: HomeKept",
      },
      {
        property: "og:description",
        content:
          "Scheduled visits, seasonal checklists, photo reports. Serving Mississauga neighbourhoods including Port Credit, Lorne Park, Erin Mills, and Streetsville.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: CITY_URL },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
      {
        "script:ld+json": buildLocalBusinessSchema({
          cityName: "Mississauga",
          cityUrl: CITY_URL,
        }),
      },
    ],
    links: [{ rel: "canonical", href: CITY_URL }],
  }),
  component: MississaugaPage,
});

// Mississauga FSAs — a large city with many postal prefixes.
// Sources: Canada Post forward sortation area map, Statistics Canada 2021.
// Western Mississauga (bordering Oakville/Milton) uses L4/L5 series.
const FSAS = [
  "L4T",
  "L4W",
  "L4X",
  "L4Y",
  "L4Z",
  "L5A",
  "L5B",
  "L5C",
  "L5E",
  "L5G",
  "L5H",
  "L5J",
  "L5K",
  "L5L",
  "L5M",
  "L5N",
  "L5R",
  "L5V",
  "L5W",
];

// Mississauga neighbourhoods — real, verifiable.
const NEIGHBOURHOODS = [
  "Port Credit",
  "Lorne Park",
  "Clarkson",
  "Erin Mills",
  "Streetsville",
  "Meadowvale",
  "Churchill Meadows",
  "Applewood",
  "Cooksville",
  "East Credit",
  "Malton",
  "Rathwood",
];

// Mississauga-specific seasonal pain points.
const SEASONAL = [
  {
    season: "Winter",
    heading: "Basement moisture in older Applewood and Cooksville stock",
    body: "Mississauga's older housing stock in Applewood, Rathwood, and Cooksville was built before modern drainage standards. Winter basement moisture checks, covering caulking, grading observations, and sump pump health, catch water infiltration before it becomes a renovation.",
  },
  {
    season: "Spring",
    heading: "Sump pump and drainage in low-lying Meadowvale",
    body: "Parts of Meadowvale and Churchill Meadows sit in Credit River tributary drainage areas. A sump pump test and pit clean in March is essential: a failed pump during snowmelt can flood a finished basement in hours.",
  },
  {
    season: "Summer",
    heading: "Dryer vents and bath fans in tightly-built townhomes",
    body: "Erin Mills and Churchill Meadows include large blocks of semi-detached and townhome stock where dryer vents run long horizontal runs to the exterior. A dryer vent deep-clean in summer reduces fire risk and improves drying efficiency.",
  },
  {
    season: "Fall",
    heading: "Gutter clearing across Mississauga's wide tree canopy",
    body: "Mature silver maples and Norway maples across Lorne Park and Clarkson deposit leaves steadily through October. A post-leaf gutter clear before freeze prevents fascia damage and ice loads on older homes where gutters are already working hard.",
  },
];

const CITY: CityConfig = {
  cityName: "Mississauga",
  fsas: FSAS,
  neighbourhoods: NEIGHBOURHOODS,
  postalIntro: "We serve Mississauga addresses whose postal code begins with:",
  fsaContainerClassName: "mt-4 flex flex-wrap gap-2",
  fsaPillClassName:
    "inline-block rounded-full border border-border bg-background px-3.5 py-1.5 font-mono text-sm font-semibold text-primary",
  seasonalIntro:
    "Mississauga's mix of housing eras, from 1960s stock in Cooksville to newer builds in Churchill Meadows, means maintenance priorities vary by neighbourhood.",
  seasonal: SEASONAL,
};

function MississaugaPage() {
  return <CityPage city={CITY} />;
}
