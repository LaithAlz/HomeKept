import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAccount, type AppAccount } from "@/lib/account";
import { useSessionExpiredRedirect } from "@/lib/auth";

export const Route = createFileRoute("/app/settings")({
  head: () => ({
    meta: [{ title: "Settings — HomeKept" }, { name: "robots", content: "noindex" }],
  }),
  component: SettingsPage,
});

function SettingsPage() {
  const query = useAccount();
  useSessionExpiredRedirect(query.error);

  return (
    <div className="px-6 py-10 md:px-10">
      <h1 className="font-display text-3xl font-extrabold tracking-tight md:text-4xl">Settings</h1>
      <p className="mt-2 max-w-2xl text-muted-foreground">Your profile and home address.</p>

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        {query.isLoading ? (
          <div
            className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground lg:col-span-2"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your account.
          </div>
        ) : query.isError ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground lg:col-span-2">
            We couldn't load your account. Try refreshing the page.
          </p>
        ) : !query.data ? (
          <p className="rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground lg:col-span-2">
            No account information yet.
          </p>
        ) : (
          <ProfileAndAddress account={query.data} />
        )}
      </div>
    </div>
  );
}

// There is no profile-update endpoint yet (`GET /api/app/account` is read-only), so the
// Profile and Home address cards below render the real data read-only rather than inviting
// edits that would silently do nothing.
function ProfileAndAddress({ account }: { account: AppAccount }) {
  return (
    <>
      <Card title="Profile">
        <p className="text-xs text-muted-foreground">
          Not editable here yet. To change your name or email, contact us at{" "}
          <a
            href="mailto:hello@homekept.ca"
            className="font-medium underline underline-offset-2 hover:text-foreground"
          >
            hello@homekept.ca
          </a>
          .
        </p>
        <div className="mt-3 grid gap-3">
          <ReadOnlyField id="settings-first-name" label="First name" value={account.firstName} />
          <ReadOnlyField id="settings-last-name" label="Last name" value={account.lastName} />
          <ReadOnlyField id="settings-email" label="Email" value={account.email} type="email" />
        </div>
      </Card>

      <Card title="Home address">
        <p className="text-xs text-muted-foreground">
          Not editable here yet. To change your service address, contact us at{" "}
          <a
            href="mailto:hello@homekept.ca"
            className="font-medium underline underline-offset-2 hover:text-foreground"
          >
            hello@homekept.ca
          </a>
          .
        </p>
        <div className="mt-3 grid gap-3">
          <ReadOnlyField
            id="settings-street"
            label="Street"
            value={account.streetAddress ?? "Not on file"}
          />
          <div className="grid grid-cols-2 gap-3">
            <ReadOnlyField id="settings-unit" label="Unit" value={account.unit ?? "None"} />
            <ReadOnlyField id="settings-city" label="City" value={account.city ?? "Not on file"} />
          </div>
          <ReadOnlyField
            id="settings-postal"
            label="Postal code"
            value={account.postalCode ?? "Not on file"}
          />
        </div>
      </Card>
    </>
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
    <div className="rounded-3xl border border-border bg-card p-6">
      <h2 className="font-display text-lg font-bold">{title}</h2>
      {desc && <p className="text-xs text-muted-foreground">{desc}</p>}
      <div className="mt-4">{children}</div>
    </div>
  );
}

/** A read-only field for data with no backend write endpoint yet — see `ProfileAndAddress`. */
function ReadOnlyField({
  id,
  label,
  value,
  type,
}: {
  id: string;
  label: string;
  value: string;
  type?: string;
}) {
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className="mt-1 bg-muted/40"
        type={type}
        value={value}
        readOnly
        aria-readonly="true"
      />
    </div>
  );
}
