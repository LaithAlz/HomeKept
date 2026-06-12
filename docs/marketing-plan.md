# HomeKept · Marketing Plan (non-video)

Companion to `marketing-videos.md` (video), `pricing-and-visits.md` (positioning, legal
rails), and `three-year-plan.md` (phase gates). Same rails apply everywhere: no
fabricated social proof, CASL on every message, no contracts at the door, prices all-in
except tax.

## Message house

**One-liner:** Home maintenance on autopilot.
**The displacement pitch (anti-Reliance):** "Protection plans show up after the furnace
dies. We're why it doesn't — and nobody at a protection plan has ever cleared your
gutters or sent you a photo of your roof." Insurance vs. care. Use everywhere.
**Per persona:** busy families → "your weekends back" · empty-nesters → "less climbing
ladders, same standards" · first-time owners → "your home, finally explained."
**Proof style (pre-reviews):** process transparency — show the checklist, a real photo
report, the seasonal calendar. The product is the proof until reviews exist.

## Local SEO — the highest-leverage channel after referrals

### Google Business Profile (do this in week 1 — it's free and outranks the website)

- Category: "Property maintenance" primary; services listed = the visit calendar items
- **Service-area business** (Oakville, Mississauga, Milton) — no storefront address shown
- Photos: real van/kit/checklist/report shots (no stock, no AI); weekly GBP "update"
  posts = the same seasonal tips as the video series
- Booking link → homekept.ca/book?utm_source=gbp
- **The reviews engine — testimonials and reviews are different things:**
  - The founding-rate exchange buys a **testimonial** for HomeKept's own marketing,
    with founding-member status disclosed wherever it's used. It never buys a Google
    review — incentivized reviews violate Google policy outright (removal/suspension
    risk for the profile this whole channel depends on) and are a Competition Bureau
    deceptive-marketing concern. No exceptions, including the founding cohort.
  - **Google reviews are asked for uniformly:** the review ask goes in the visit-report
    email after *every* completed visit, same direct GBP link for everyone. The 👍/👎
    report rating is used for ops follow-up and ask *timing* only — never for selecting
    who gets asked (that's review gating, also prohibited).
- NAP (name/address/phone) consistency: identical on site footer, GBP, Bing Places,
  Apple Business Connect, Yelp/Nextdoor business pages — citation set done once.

### On-site SEO

- **Pages that earn rank:** one city page per service area (`/oakville`, `/mississauga`,
  `/milton`) — genuinely distinct content (neighbourhoods served, FSA list, that city's
  seasonal pain points), not find-and-replace. Three pages, hand-written. No
  programmatic SEO until Phase 2+ at the earliest (thin-content penalty risk beats
  the upside at 3 cities).
- **Keyword targets (v1):** primary: "home maintenance service [city]", "home
  maintenance plan/subscription [city]"; secondary (content engine): "gutter cleaning
  [city]", "when to [seasonal task]", "home maintenance checklist canada/ontario",
  "furnace filter how often". Premier/"house manager" terms are low volume — ignore.
- **Content engine = the seasonal calendar, again.** 12 articles, one per month,
  same topics as video episodes V3–V14 ("What your Ontario home needs in October"),
  each embedding its episode. Written once, refreshed yearly, internally linked to the
  matching city pages and /book. This is the entire blog strategy for year 1 — no
  other content.
- **Technical:** LocalBusiness + Service schema (JSON-LD), per-page titles/descriptions,
  OG images in brand, sitemap.xml (route exists) listing real pages, correct
  canonical on www→apex. Tracked as the technical-SEO issue.
- **AI-search note:** the seasonal articles double as the citable answers
  ("when should I winterize outdoor taps in Ontario") that LLM-based search surfaces —
  plain-language Q&A headings, dates, and a named local business beat tricks.

## Channel playbook (Phase 0–1, founder-led)

| Channel | Motion | Cadence | Rules |
|---|---|---|---|
| **Nextdoor** | Seasonal tip posts (text or episode) as *founders, not brand*; answer every maintenance question in the area | 2×/mo posts + daily 10-min reply sweep | Value first; CTA only in profile + when asked; never DM-pitch |
| **FB local groups** | Same content, same persona; join 5–8 Oakville/Mississauga/Milton home/community groups | 1–2×/mo per group | Respect group promo rules; the tip *is* the post |
| **Door hangers** | Seasonal hook + QR → /book?utm_source=doorhanger&utm_campaign=[month]; hang only — **no knocking-to-sell, no contracts at the door** | Neighbourhood blitz around each anchor month, target FSA only | CPA posture per pricing doc |
| **Realtor seed** | 3–5 agents: "first 60 days on us" closing gift for buyers (agent pays nothing; we get a warm intro at the exact moment of need) | Phase 1; formalizes in Phase 2 partnerships | Gift framing, not kickbacks; the agent discloses the HomeKept-provided gift to their client in writing (TRESA s.18); CASL consent comes from the buyer; offer terms to be defined in pricing-and-visits.md before first use |
| **Referrals (informal)** | Ask built into month-3 report email: "know a neighbour drowning in their list?" — give-a-month/get-a-month *formalizes in Phase 4* | Automated in report email | Referral emails are CEMs: sender ID + unsubscribe |
| **Walk-through no-shows / non-converts** | See email nurture | — | — |

**The density rule governs all of it:** city pages and GBP are evergreen three-city
assets, but posts, photos, door-hangers, and the review flywheel all concentrate on the
home FSA's city first. Every channel aims at the ONE target FSA until
saturated. "We already maintain two homes on this street" is the best ad we'll ever have
— and the only one that's free.

## Email (CASL-compliant by construction)

- **Lead nurture (non-converted walk-through bookings):** inquiry = implied consent for
  6 months under CASL. Sequence: plan recap (day 1) → seasonal tip + what we'd have
  caught (week 2) → founding-rate nudge while available (week 4) → monthly seasonal tip
  until the 6-month consent window closes, then stop unless they opt in. Every send:
  sender ID + unsubscribe.
- **Subscriber monthly note:** the seasonal tip + "what's on your next visit" — same
  content engine, third use. This is retention, not acquisition; it's also where the
  review ask and referral ask live.
- No purchased lists, ever. No cold email.

## Measurement (ties to arch §5.7)

- UTM convention: `utm_source` = channel (gbp/nextdoor/fb/doorhanger/realtor/referral),
  `utm_campaign` = asset or month. `lead_source` enum on the booking is the ground truth —
  the enum gains `GBP`, `REALTOR`, `DOORHANGER` values (tracked in the technical-SEO
  issue). The frontend must propagate UTMs itself (sessionStorage on landing → booking
  POST): PostHog's cookieless mode does not carry them across pages. UTM-less arrivals
  (most Nextdoor/FB) fall back to a "how did you hear about us" picker on the form.
- Weekly scorecard (PostHog + DB): walk-throughs booked by source · close rate ·
  CAC-in-hours per channel (founder time is the only spend) · GBP views/actions ·
  review count. Kill channels that produce nothing for 8 weeks *and* ≥10 leads of
  history elsewhere to compare against (don't judge noise); double down where close
  rate is highest, not volume.

## Not doing (and the trigger to start)

| Thing | Trigger |
|---|---|
| Paid ads (FB/Google LSA) | Phase 2, after organic close rate is known — LSA (Local Services Ads) first, it's pay-per-lead |
| Programmatic SEO / more city pages | Phase 2 expansion beyond the first three cities |
| Social brand accounts as a daily channel | Never as a goal; profiles exist to host the videos |
| PR / local news | Opportunistic only (first-100-customers story, Phase 2) |
| Sponsorships (teams, fairs) | Phase 2+, density-targeted only |
