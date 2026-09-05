import { useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { StatusPanel } from "@/components/app/StatusPanel";
import { ApiError, messageFor } from "@/lib/api";
import {
  useAccount,
  useChangePassword,
  useUpdateAccount,
  type AppAccount,
  type UpdateAccountRequest,
} from "@/lib/account";
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
      <p className="mt-2 max-w-2xl text-muted-foreground">
        Your profile, password, and service address.
      </p>

      <div className="mt-8 max-w-2xl space-y-6">
        {query.isLoading ? (
          <StatusPanel>
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading your account.
          </StatusPanel>
        ) : query.isError ? (
          <StatusPanel>We couldn't load your account. Try refreshing the page.</StatusPanel>
        ) : !query.data ? (
          <StatusPanel>No account information yet.</StatusPanel>
        ) : (
          <>
            <ProfileCard account={query.data} />
            <PasswordCard />
            <ServiceAddressCard account={query.data} />
          </>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Profile: editable first/last name + phone, read-only email
// ---------------------------------------------------------------------------

// Same shape as the booking wizard's phone check (routes/book.tsx) — kept in sync
// deliberately so "a valid phone number" means the same thing everywhere.
const PHONE_RE = /^[\d\s()+\-.]{10,}$/;

interface ProfileFieldErrors {
  firstName?: string;
  lastName?: string;
  phone?: string;
}

function ProfileCard({ account }: { account: AppAccount }) {
  const [firstName, setFirstName] = useState(account.firstName);
  const [lastName, setLastName] = useState(account.lastName);
  const [phone, setPhone] = useState(account.phone ?? "");
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const mutation = useUpdateAccount();

  const savedPhone = account.phone ?? "";
  const dirty =
    firstName.trim() !== account.firstName ||
    lastName.trim() !== account.lastName ||
    phone.trim() !== savedPhone;

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();

    const errs: ProfileFieldErrors = {};
    if (!firstName.trim()) errs.firstName = "First name is required.";
    if (!lastName.trim()) errs.lastName = "Last name is required.";
    if (phone.trim() && !PHONE_RE.test(phone.trim())) errs.phone = "Enter a valid phone number";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    setErrors({});
    setFormError(null);

    const body: UpdateAccountRequest = {};
    if (firstName.trim() !== account.firstName) body.firstName = firstName.trim();
    if (lastName.trim() !== account.lastName) body.lastName = lastName.trim();
    if (phone.trim() !== savedPhone) body.phone = phone.trim();

    mutation.mutate(body, {
      onSuccess: (data) => {
        setFirstName(data.firstName);
        setLastName(data.lastName);
        setPhone(data.phone ?? "");
        toast.success("Profile updated.");
      },
      onError: (err) => {
        if (
          err instanceof ApiError &&
          err.status === 400 &&
          err.fields &&
          Object.keys(err.fields).length
        ) {
          setErrors(err.fields as ProfileFieldErrors);
          return;
        }
        if (err instanceof ApiError && err.status === 404) {
          setFormError("Saving isn't available yet. Please try again shortly.");
          return;
        }
        setFormError(messageFor(err));
      },
    });
  }

  return (
    <Card title="Profile">
      <form onSubmit={handleSubmit} noValidate className="grid gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="settings-first-name"
            label="First name"
            value={firstName}
            onChange={(v) => {
              setFirstName(v);
              if (errors.firstName) setErrors((prev) => ({ ...prev, firstName: undefined }));
            }}
            error={errors.firstName}
            autoComplete="given-name"
          />
          <Field
            id="settings-last-name"
            label="Last name"
            value={lastName}
            onChange={(v) => {
              setLastName(v);
              if (errors.lastName) setErrors((prev) => ({ ...prev, lastName: undefined }));
            }}
            error={errors.lastName}
            autoComplete="family-name"
          />
        </div>

        <Field
          id="settings-phone"
          label="Phone"
          type="tel"
          value={phone}
          onChange={(v) => {
            setPhone(v);
            if (errors.phone) setErrors((prev) => ({ ...prev, phone: undefined }));
          }}
          error={errors.phone}
          autoComplete="tel"
        />

        <div>
          <Label htmlFor="settings-email">Email</Label>
          <Input
            id="settings-email"
            className="mt-1 bg-muted/40"
            type="email"
            value={account.email}
            readOnly
            aria-readonly="true"
          />
          <p className="mt-1.5 text-xs text-muted-foreground">
            Your email is your sign-in identity, so changing it needs verification. Email changes go
            through us for now: contact{" "}
            <a
              href="mailto:hello@homekept.ca"
              className="font-medium underline underline-offset-2 hover:text-foreground"
            >
              hello@homekept.ca
            </a>
            .
          </p>
        </div>

        {formError && (
          <p role="alert" className="text-sm text-destructive">
            {formError}
          </p>
        )}

        <div className="flex justify-end">
          <Button
            type="submit"
            size="sm"
            disabled={!dirty || mutation.isPending}
            aria-busy={mutation.isPending}
          >
            {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            Save
          </Button>
        </div>
      </form>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Password: current/new/confirm
// ---------------------------------------------------------------------------

interface PasswordFieldErrors {
  currentPassword?: string;
  newPassword?: string;
  confirmPassword?: string;
}

function PasswordCard() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<PasswordFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const mutation = useChangePassword();

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();

    const errs: PasswordFieldErrors = {};
    if (!currentPassword) errs.currentPassword = "Enter your current password.";
    if (newPassword.length < 8) errs.newPassword = "Password must be at least 8 characters.";
    if (newPassword !== confirmPassword) errs.confirmPassword = "Passwords don't match.";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    setErrors({});
    setFormError(null);

    mutation.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword("");
          setNewPassword("");
          setConfirmPassword("");
          toast.success("Password updated.");
        },
        onError: (err) => {
          // The contract only documents a plain 400 for a wrong current password (no
          // field-level detail) — map it to one fixed, safe sentence rather than
          // trusting whatever text the backend happens to send.
          if (err instanceof ApiError && err.status === 400) {
            setErrors({ currentPassword: "Your current password is incorrect." });
            return;
          }
          if (err instanceof ApiError && err.status === 404) {
            setFormError("Changing your password isn't available yet. Please try again shortly.");
            return;
          }
          setFormError(messageFor(err));
        },
      },
    );
  }

  return (
    <Card title="Password">
      <form onSubmit={handleSubmit} noValidate className="grid gap-4">
        <Field
          id="settings-current-password"
          label="Current password"
          type="password"
          value={currentPassword}
          onChange={(v) => {
            setCurrentPassword(v);
            if (errors.currentPassword) {
              setErrors((prev) => ({ ...prev, currentPassword: undefined }));
            }
          }}
          error={errors.currentPassword}
          autoComplete="current-password"
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="settings-new-password"
            label="New password"
            type="password"
            value={newPassword}
            onChange={(v) => {
              setNewPassword(v);
              if (errors.newPassword) setErrors((prev) => ({ ...prev, newPassword: undefined }));
            }}
            error={errors.newPassword}
            autoComplete="new-password"
          />
          <Field
            id="settings-confirm-password"
            label="Confirm new password"
            type="password"
            value={confirmPassword}
            onChange={(v) => {
              setConfirmPassword(v);
              if (errors.confirmPassword) {
                setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
              }
            }}
            error={errors.confirmPassword}
            autoComplete="new-password"
          />
        </div>

        {formError && (
          <p role="alert" className="text-sm text-destructive">
            {formError}
          </p>
        )}

        <div className="flex justify-end">
          <Button
            type="submit"
            size="sm"
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
          >
            {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            Update password
          </Button>
        </div>
      </form>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Service address: read-only, honest reason instead of "contact us"
// ---------------------------------------------------------------------------

function ServiceAddressCard({ account }: { account: AppAccount }) {
  return (
    <Card title="Service address">
      <p className="text-xs text-muted-foreground">
        Your service address is what routes a technician to your home, so address changes go through
        us for now: contact{" "}
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
  );
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

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

/** A labeled, editable input with an inline error, used by the Profile and Password forms. */
function Field({
  id,
  label,
  value,
  onChange,
  error,
  type = "text",
  autoComplete,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  type?: string;
  autoComplete?: string;
}) {
  const errorId = `${id}-error`;
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className="mt-1"
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        aria-invalid={!!error}
        aria-describedby={error ? errorId : undefined}
      />
      {error && (
        <p id={errorId} role="alert" className="mt-1 text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

/** A read-only field for data that goes through us for now — see `ServiceAddressCard`. */
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
