import { createFileRoute } from "@tanstack/react-router";
import { Building2, Clock, CreditCard, Info, MapPin, Plug, Users } from "lucide-react";

export const Route = createFileRoute("/admin/settings")({
  head: () => ({
    meta: [{ title: "Settings — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: AdminSettingsPage,
});

type ConfigRow = {
  label: string;
  value: React.ReactNode;
  note?: React.ReactNode;
  /**
   * A real place to go change this: an external destination ("Stripe Dashboard, Tax
   * settings") or, when there's genuinely nowhere to go, one plain sentence that it's
   * fixed in code. Omit when `note` already says everything there is to say (e.g. a
   * feature that simply isn't built yet).
   */
  configuredVia?: React.ReactNode;
  /**
   * Environment variable name(s) that control this, meaningful to whoever configures the
   * deploy. Omit when there's no such variable.
   */
  envVars?: string[];
};

type IconComponent = React.ComponentType<React.SVGProps<SVGSVGElement>>;

/**
 * There is no admin-settings endpoint (no backing table for business hours, service-area
 * boundaries, billing configuration, or console-access management, see
 * `backend/api-contract.md`), so this page is not an editable settings form. It is a
 * read-only system-configuration reference, grouped by what an operator would actually
 * come here to check.
 *
 * The old version of this page repeated "isn't built yet" once per card. That's said once,
 * structurally, in the "Not configurable yet" section at the bottom. Everything above it is
 * real and current. An earlier version of this page also named the exact source file behind
 * every value (`frontend/src/lib/seo.ts`, `CITIES in frontend/src/lib/booking.ts`, and so
 * on): useful to a developer reading the code, meaningless to the founder running this
 * console as an operator ("why are there file names here"). What earns a place in a row now
 * instead: a real external destination to go change something (a Stripe Dashboard section),
 * an environment variable name where that's genuinely what whoever configures the deploy
 * would set, or, for anything with neither, one plain sentence that it's fixed in code and
 * would need a developer. No inputs, toggles, or Save buttons appear anywhere, since nothing
 * here has a write path.
 */
function AdminSettingsPage() {
  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Settings</h1>
      <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
        How HomeKept is configured right now. Nothing on this page is editable yet. Where a setting
        lives in an environment variable or an external dashboard, that's shown below; otherwise,
        it's fixed in code.
      </p>

      <div className="mt-6 space-y-8">
        <ConfigSection
          id="business-identity"
          icon={Building2}
          title="Business identity"
          rows={[
            {
              label: "Business name",
              value: "HomeKept",
              configuredVia: "Fixed in code, changing it needs a developer.",
            },
            {
              label: "Support email",
              value: "hello@homekept.ca",
              note: "The only support channel today. Replies go to a real inbox, not a form.",
              configuredVia: "Fixed in code, changing it needs a developer.",
            },
            {
              label: "Phone",
              value: "None",
              note: "No business line is set up.",
              configuredVia: "Fixed in code, changing it needs a developer.",
            },
          ]}
        />

        <ConfigSection
          id="service-area"
          icon={MapPin}
          title="Service area"
          rows={[
            {
              label: "Cities served",
              value: "Oakville, Mississauga, Milton",
              note: 'The walk-through form also lists "Other" as a catch-all. The backend does not reject a booking outside these three cities, so this is a client-side allowlist, not an enforced boundary.',
              configuredVia: "Fixed in code, changing it needs a developer.",
            },
          ]}
        />

        <ConfigSection
          id="payments-billing"
          icon={CreditCard}
          title="Payments & billing"
          rows={[
            {
              label: "Processor",
              value: "Stripe",
              note: "Checkout for subscriptions and one-off extras, the Billing Portal for plan changes and cancellations, signature-verified webhooks for the subscription lifecycle.",
              envVars: ["STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET"],
            },
            {
              label: "Tax collection",
              value: <StatusPill>Off</StatusPill>,
              note: 'Stripe Tax is not enabled, so no HST is added to any charge. The pricing page and the customer billing page both say "plus HST": that is not true yet.',
              configuredVia: "Stripe Dashboard, Tax settings.",
            },
            {
              label: "Failed-payment handling",
              value: "Stripe's own retry rules",
              note: "Not something this app configures. A failed invoice moves the subscriber to Payment issue until it resolves or Stripe's retries run out, then Cancelled.",
              configuredVia: "Stripe Dashboard, Billing → Retries.",
            },
          ]}
        />

        <ConfigSection
          id="integrations"
          icon={Plug}
          title="Integrations"
          rows={[
            {
              label: "Transactional email",
              value: "SendGrid",
              note: "Booking confirmations, activation invites, password resets, visit reports, billing notices. If the key is unset, a send is logged and skipped rather than retried, and the action that triggered it still succeeds.",
              envVars: ["SENDGRID_API_KEY", "SENDGRID_FROM_EMAIL", "SENDGRID_FROM_NAME"],
            },
            {
              label: "Visit photo storage",
              value: "Cloudflare R2",
              note: "Signed upload and viewing URLs for technician visit photos. If unset, the photo endpoints return a 503 instead of the app failing to start.",
              envVars: ["R2_ENDPOINT", "R2_BUCKET", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY"],
            },
            {
              label: "Product analytics",
              value: "PostHog",
              note: "Funnel and lifecycle events from the backend and the frontend, scrubbed of PII. A silent no-op if the key is unset.",
              envVars: ["POSTHOG_API_KEY", "POSTHOG_HOST", "VITE_PUBLIC_POSTHOG_KEY"],
            },
            {
              label: "Error tracking",
              value: "None",
              note: "Sentry was evaluated and dropped. An unhandled error today reaches only the server logs.",
            },
          ]}
        />

        <ConfigSection
          id="operational"
          icon={Clock}
          title="Operational"
          rows={[
            {
              label: "Timezone",
              value: "America/Toronto",
              note: "Every visit time, technician day sheet, and reminder is computed in this zone, regardless of where the server itself runs.",
              envVars: ["APP_TIMEZONE"],
            },
          ]}
        />

        <ConfigSection
          id="team-access"
          icon={Users}
          title="Team & console access"
          rows={[
            {
              label: "Admin console",
              value: "Seeded from environment variables",
              note: "Runs on every boot but only creates a user if that exact email doesn't already exist, so setting a new ADMIN_SEED_EMAIL and restarting adds another admin. There's no in-app invite flow.",
              envVars: ["ADMIN_SEED_EMAIL", "ADMIN_SEED_PASSWORD"],
            },
            {
              label: "Technician app",
              value: "Invited by an existing admin",
              note: "Admin → Technicians → Add technician. The technician sets their own password when they activate.",
            },
          ]}
        />

        <section aria-labelledby="not-configurable-heading">
          <div className="flex items-center gap-2">
            <Info className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <h2 id="not-configurable-heading" className="font-display text-lg font-bold">
              Not configurable yet
            </h2>
          </div>
          <div className="mt-3 rounded-2xl border border-dashed border-border bg-muted/20 p-5">
            <p className="text-sm text-muted-foreground">
              These have no backing config or table anywhere in the system yet, not just on this
              page.
            </p>
            <ul className="mt-3 space-y-3 text-sm">
              <li>
                <span className="font-medium text-foreground">Service-area precision.</span>{" "}
                <span className="text-muted-foreground">
                  Today it's a flat three-city allowlist. There's no postal-code or
                  neighbourhood-level rule.
                </span>
              </li>
              <li>
                <span className="font-medium text-foreground">Dispatch or business hours.</span>{" "}
                <span className="text-muted-foreground">
                  No working-hours concept exists anywhere in the system, not even for technician
                  scheduling capacity: the route calendar reports honest visit counts and
                  deliberately never a "slots free" figure, because there's nothing to compute one
                  from.
                </span>
              </li>
            </ul>
          </div>
        </section>
      </div>
    </div>
  );
}

function ConfigSection({
  id,
  icon: Icon,
  title,
  rows,
}: {
  id: string;
  icon: IconComponent;
  title: string;
  rows: ConfigRow[];
}) {
  const headingId = `${id}-heading`;
  return (
    <section aria-labelledby={headingId}>
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <h2 id={headingId} className="font-display text-lg font-bold">
          {title}
        </h2>
      </div>
      <dl className="mt-3 divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
        {rows.map((row) => (
          <div key={row.label} className="px-4 py-3.5 sm:flex sm:gap-6">
            <dt className="text-sm font-medium text-foreground sm:w-52 sm:shrink-0">{row.label}</dt>
            <dd className="mt-1 min-w-0 flex-1 text-sm sm:mt-0">
              <div className="font-medium text-foreground">{row.value}</div>
              {row.note && <p className="mt-1 text-xs text-muted-foreground">{row.note}</p>}
              {row.configuredVia && (
                <p className="mt-1.5 text-xs text-muted-foreground">{row.configuredVia}</p>
              )}
              {row.envVars && row.envVars.length > 0 && (
                <p className="mt-1.5 flex flex-wrap items-center gap-1.5">
                  <span className="sr-only">
                    Environment variable{row.envVars.length > 1 ? "s" : ""}:
                  </span>
                  {row.envVars.map((v) => (
                    <code
                      key={v}
                      className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground"
                    >
                      {v}
                    </code>
                  ))}
                </p>
              )}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function StatusPill({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full border border-warning/30 bg-warning/10 px-2 py-0.5 text-xs font-semibold text-foreground">
      {children}
    </span>
  );
}
