import { createFileRoute } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { subscriber } from "@/lib/mock-account";

export const Route = createFileRoute("/app/settings")({
  head: () => ({
    meta: [
      { title: "Settings — HomeKept" },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: SettingsPage,
});

function SettingsPage() {
  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Settings</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">Profile, address, access instructions, and notification preferences.</p>

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <Card title="Profile">
          <div className="grid gap-3">
            <Field label="First name" defaultValue={subscriber.firstName} />
            <Field label="Last name" defaultValue={subscriber.lastName} />
            <Field label="Email" defaultValue={subscriber.email} type="email" />
            <Field label="Phone" defaultValue="(905) 555-0123" type="tel" />
          </div>
          <Button size="sm" className="mt-4">Save profile</Button>
        </Card>

        <Card title="Home address">
          <div className="grid gap-3">
            <Field label="Street" defaultValue={subscriber.address.street} />
            <div className="grid grid-cols-2 gap-3">
              <Field label="Neighbourhood" defaultValue={subscriber.address.neighbourhood} />
              <Field label="City" defaultValue={subscriber.address.city} />
            </div>
            <Field label="Postal code" defaultValue="L5L 3K2" />
          </div>
          <Button size="sm" className="mt-4">Save address</Button>
        </Card>

        <Card title="Access instructions" desc="What your technician needs to know to get in and around.">
          <div className="grid gap-3">
            <div>
              <Label htmlFor="entry">Entry method</Label>
              <Textarea id="entry" defaultValue="Side gate code: 4271. Garage keypad: 8830. Dog (Luna) lives inside — friendly." className="mt-1" rows={3} />
            </div>
            <div>
              <Label htmlFor="notes">Notes for technicians</Label>
              <Textarea id="notes" defaultValue="Furnace is in the basement utility room. Crawlspace access under the stairs." className="mt-1" rows={3} />
            </div>
          </div>
          <Button size="sm" className="mt-4">Save access info</Button>
        </Card>

        <Card title="Notifications">
          <Toggle label="Visit reminders" desc="Day-before reminder by email + SMS" defaultChecked />
          <Toggle label="Arrival texts" desc="When your technician is 30 minutes out" defaultChecked />
          <Toggle label="Report ready" desc="Email when a new visit report is uploaded" defaultChecked />
          <Toggle label="Seasonal tips" desc="Monthly newsletter with home care tips" />
        </Card>
      </div>
    </div>
  );
}

function Card({ title, desc, children }: { title: string; desc?: string; children: React.ReactNode }) {
  return (
    <div className="rounded-3xl border border-border bg-card p-6">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      {desc && <p className="text-xs text-muted-foreground">{desc}</p>}
      <div className="mt-4">{children}</div>
    </div>
  );
}

function Field({ label, ...rest }: { label: string } & React.ComponentProps<typeof Input>) {
  return (
    <div>
      <Label>{label}</Label>
      <Input className="mt-1" {...rest} />
    </div>
  );
}

function Toggle({ label, desc, defaultChecked }: { label: string; desc: string; defaultChecked?: boolean }) {
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
