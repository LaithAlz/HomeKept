import { createFileRoute } from "@tanstack/react-router";
import { BASE_URL, OG_IMAGE_DEFAULT, buildLocalBusinessSchema } from "@/lib/seo";
import { CityPage, type CityConfig } from "@/components/marketing/CityPage";

const CITY_PATH = "/milton";
const CITY_URL = `${BASE_URL}${CITY_PATH}`;

export const Route = createFileRoute("/milton")({
  head: () => ({
    meta: [
      {
        title: "Home Maintenance Service Milton: HomeKept",
      },
      {
        name: "description",
        content:
          "HomeKept provides monthly home maintenance subscriptions in Milton, ON. We serve Coates, Clarke, Willmott, Scott, and the newer Derry Green communities.",
      },
      {
        property: "og:title",
        content: "Home Maintenance Subscription in Milton: HomeKept",
      },
      {
        property: "og:description",
        content:
          "Scheduled visits, seasonal checklists, photo reports. Serving Milton neighbourhoods including Coates, Clarke, Willmott, Scott, and Beaty.",
      },
      { property: "og:type", content: "website" },
      { property: "og:url", content: CITY_URL },
      { property: "og:image", content: OG_IMAGE_DEFAULT },
      {
        "script:ld+json": buildLocalBusinessSchema({
          cityName: "Milton",
          cityUrl: CITY_URL,
        }),
      },
    ],
    links: [{ rel: "canonical", href: CITY_URL }],
  }),
  component: MiltonPage,
});

// Milton FSAs — the town uses L9E and L9T postal prefixes.
// Sources: Canada Post, Statistics Canada 2021 Census.
// L9E covers newer Derry Green / Trafalgar corridor expansion areas.
// L9T covers the established core, Beaty, Coates, Clarke, and Scott.
const FSAS = ["L9E", "L9T"];

// Milton neighbourhoods — real, verifiable.
const NEIGHBOURHOODS = [
  "Coates",
  "Clarke",
  "Willmott",
  "Scott",
  "Beaty",
  "Bronte Meadows",
  "Old Milton",
  "Hawthorne Village",
  "Boyne Survey",
  "Derry Green",
];

// Milton-specific seasonal pain points.
const SEASONAL = [
  {
    season: "Winter",
    heading: "New-build settling and cold-weather envelope checks",
    body: "Milton has grown faster than almost any municipality in Canada, and newer builds in Derry Green, Boyne Survey, and Coates are still settling. Cold winters reveal gaps at window and door frames before they become significant air-sealing problems. A mid-winter weatherstripping and draft check catches these early.",
  },
  {
    season: "Spring",
    heading: "Grading and drainage in low-lying Escarpment runoff areas",
    body: "Milton sits at the base of the Niagara Escarpment. Spring snowmelt and rain flow down toward the town, making proper grading, downspout extensions, and sump pump function critical in March and April for homes near the escarpment drainage corridor.",
  },
  {
    season: "Summer",
    heading: "AC performance in newer all-electric and hybrid homes",
    body: "Milton's newest builds in Boyne Survey and Derry Green include high-efficiency HVAC systems that benefit from a condenser clean and performance observation in June. Catching airflow issues before the peak summer heat is considerably less disruptive than a service call in August.",
  },
  {
    season: "Fall",
    heading: "Humidifier service and furnace prep before Milton winters",
    body: "Milton winters are colder and drier than the lake-effect moderated climate in Oakville and Mississauga. Furnace filter replacement, humidifier pad service, and a performance observation in September ensures the system is ready before heating season begins.",
  },
];

const CITY: CityConfig = {
  cityName: "Milton",
  fsas: FSAS,
  neighbourhoods: NEIGHBOURHOODS,
  postalIntro: "We serve all Milton addresses whose postal code begins with:",
  fsaContainerClassName: "mt-4 flex flex-wrap gap-2.5",
  fsaPillClassName:
    "inline-block rounded-full border border-border bg-background px-4 py-1.5 font-mono text-sm font-semibold text-primary",
  seasonalIntro:
    "Milton has grown rapidly, which means a large proportion of homes are relatively new, with maintenance needs that differ from older urban stock.",
  seasonal: SEASONAL,
};

function MiltonPage() {
  return <CityPage city={CITY} />;
}
