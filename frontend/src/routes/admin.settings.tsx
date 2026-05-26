import { createFileRoute } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

export const Route = createFileRoute("/admin/settings")({
  head: () => ({
    meta: [
      { title: "Settings — HomeKept Admin" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: AdminSettingsPage,
});

function AdminSettingsPage() {
  return (
    <div className="px-6 py-8">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">Settings</h1>
      <p className="mt-1 text-sm text-muted-foreground">Business hours, service area, integrations, and team access.</p>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card title="Business hours" desc="When customers can book and technicians are dispatched.">
          {["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"].map((d) => (
            <div key={d} className="flex items-center justify-between border-t border-border py-2.5 text-sm">
              <span>{d}</span>
              <span className="tabular-nums text-muted-foreground">8:00 AM – 6:00 PM</span>
            </div>
          ))}
          <div className="flex items-center justify-between border-t border-border py-2.5 text-sm">
            <span>Saturday</span>
            <span className="tabular-nums text-muted-foreground">9:00 AM – 2:00 PM</span>
          </div>
          <div className="flex items-center justify-between border-t border-border py-2.5 text-sm">
            <span>Sunday</span>
            <span className="text-muted-foreground">Closed</span>
          </div>
          <Button size="sm" variant="outline" className="mt-3">Edit hours</Button>
        </Card>

        <Card title="Service areas" desc="Cities and postal prefixes HomeKept currently serves.">
          <div className="mt-2 flex flex-wrap gap-2">
            {["L5L Mississauga", "L5M Mississauga", "L5N Mississauga", "L6H Oakville", "L6J Oakville", "L6K Oakville", "L9T Milton", "L9E Milton"].map((p) => (
              <span key={p} className="rounded-full border border-border bg-background px-3 py-1 text-xs">{p}</span>
            ))}
          </div>
          <Button size="sm" variant="outline" className="mt-4">Add postal prefix</Button>
        </Card>

        <Card title="Billing" desc="Payment processor and tax configuration.">
          <Row label="Processor" value="Stripe (Live)" />
          <Row label="Tax" value="HST 13% (Ontario)" />
          <Row label="Failed payment retries" value="3 (over 7 days)" />
        </Card>

        <Card title="Integrations" desc="Connected tools that power day-to-day ops.">
          <ToggleRow label="Google Calendar" desc="Sync visits to technician calendars" defaultChecked />
          <ToggleRow label="Twilio SMS" desc="Visit reminders and arrival texts" defaultChecked />
          <ToggleRow label="Resend (email)" desc="Reports, receipts, and plan deliveries" defaultChecked />
          <ToggleRow label="QuickBooks" desc="Revenue and expense sync" />
        </Card>

        <Card title="Team access" desc="Who can sign into the admin console.">
          <TeamRow name="Owner" email="founder@homekept.ca" role="Owner" />
          <TeamRow name="Operations Lead" email="ops@homekept.ca" role="Admin" />
          <TeamRow name="Bookkeeper" email="books@homekept.ca" role="Billing only" />
          <Button size="sm" className="mt-3">Invite teammate</Button>
        </Card>

        <Card title="Brand">
          <div className="grid grid-cols-2 gap-3">
            <div><Label>Business name</Label><Input defaultValue="HomeKept" className="mt-1" /></div>
            <div><Label>Support email</Label><Input defaultValue="hello@homekept.ca" className="mt-1" /></div>
          </div>
          <Button size="sm" className="mt-4">Save</Button>
        </Card>
      </div>
    </div>
  );
}

function Card({ title, desc, children }: { title: string; desc?: string; children: React.ReactNode }) {
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

function ToggleRow({ label, desc, defaultChecked }: { label: string; desc: string; defaultChecked?: boolean }) {
  return (
    <div className="flex items-center justify-between border-t border-border py-3 first:border-t-0">
      <div>
        <div className="text-sm font-medium">{label}</div>
        <div className="text-xs text-muted-foreground">{desc}</div>
      </div>
      <Switch defaultChecked={defaultChecked} />
    </div>
  );
}

function TeamRow({ name, email, role }: { name: string; email: string; role: string }) {
  return (
    <div className="flex items-center justify-between border-t border-border py-3 first:border-t-0">
      <div>
        <div className="text-sm font-medium">{name}</div>
        <div className="text-xs text-muted-foreground">{email}</div>
      </div>
      <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{role}</span>
    </div>
  );
}
