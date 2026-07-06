import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/admin/settings")({
  head: () => ({
    meta: [{ title: "Settings — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: AdminSettingsPage,
});

/**
 * There is no admin-settings endpoint yet (business hours, service-area boundaries,
 * billing configuration, integration status, and console-access management all have
 * no backing table or route in `backend/api-contract.md`). Rather than show invented
 * hours, postal codes, integration states, or a fabricated team roster as if they were
 * live configuration, this page states only what's independently verifiable elsewhere
 * in the codebase (brand identity, the real service cities, the real payment and email
 * providers) and is honest that the rest isn't configurable here yet. No inputs or
 * buttons imply a save/add/invite action that nothing on the backend handles.
 */
function AdminSettingsPage() {
  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Settings</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Business info that's currently fixed in code. Most settings management isn't built yet, so
        nothing on this page is editable.
      </p>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card title="Brand">
          <Row label="Business name" value="HomeKept" />
          <Row label="Support email" value="hello@homekept.ca" />
        </Card>

        <Card title="Service area" desc="Where HomeKept currently operates.">
          <Row label="Cities" value="Oakville, Mississauga, Milton" />
          <p className="mt-3 text-xs text-muted-foreground">
            Postal-code level service-area configuration isn't built yet.
          </p>
        </Card>

        <Card title="Payments" desc="How subscriptions and one-time charges are processed.">
          <Row label="Processor" value="Stripe" />
          <p className="mt-3 text-xs text-muted-foreground">
            Billing configuration (tax handling, retry policy) isn't exposed here yet.
          </p>
        </Card>

        <Card title="Integrations" desc="Tools HomeKept's codebase is actually wired to today.">
          <Row label="Email" value="SendGrid" />
          <Row label="Payments" value="Stripe" />
          <p className="mt-3 text-xs text-muted-foreground">
            No other integrations are connected yet.
          </p>
        </Card>

        <Card title="Team access" desc="Who can sign into the admin console.">
          <p className="text-sm text-muted-foreground">
            Console access isn't managed from this page yet. Admin accounts are provisioned
            directly, not through a self-serve roster.
          </p>
        </Card>

        <Card title="Business hours" desc="When customers can book and technicians are dispatched.">
          <p className="text-sm text-muted-foreground">
            Business hours aren't configurable here yet.
          </p>
        </Card>
      </div>
    </div>
  );
}

function Card({
  title,
  desc,
  children,
}: {
  title: string;
  desc?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      {desc && <p className="text-xs text-muted-foreground">{desc}</p>}
      <div className="mt-3">{children}</div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between border-t border-border py-2.5 text-sm first:border-t-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
