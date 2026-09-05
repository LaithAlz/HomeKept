/**
 * Add technician dialog — invites a new technician by identity only (first name,
 * last name, email, optional phone). `POST /api/admin/technicians` creates the
 * account PENDING_ACTIVATION with an unusable password and emails a staff-invite
 * link; the invited person sets their own password there (see
 * `routes/staff.activate.tsx`) — there is no admin-set temporary password. The
 * role is always server-set to TECHNICIAN; hourly cost, employee status, and hire
 * date are set later on a technician-edit screen, not here.
 *
 * A centered modal, not a side sheet: four fields, one submit action, no reason
 * for its own URL.
 */
import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ApiError } from "@/lib/api";
import { EMAIL_RE } from "@/lib/booking";
import { useCreateTechnician, type CreateTechnicianRequest } from "@/lib/admin";

interface AddTechnicianFormData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}

const EMPTY_FORM: AddTechnicianFormData = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
};

type FieldErrors = Partial<Record<keyof AddTechnicianFormData, string>>;

function validate(f: AddTechnicianFormData): FieldErrors {
  const errs: FieldErrors = {};
  if (!f.firstName.trim()) errs.firstName = "Enter a first name";
  if (!f.lastName.trim()) errs.lastName = "Enter a last name";
  if (!EMAIL_RE.test(f.email.trim())) errs.email = "Enter a valid email address";
  return errs;
}

/**
 * 409 (duplicate email) shows the backend's curated message verbatim; 400 with field
 * errors maps them onto the matching inputs (the backend's field names already match
 * this form's keys 1:1); any other 400 falls back to the backend's message; anything
 * else is the generic line.
 */
function describeError(err: unknown, setFieldErrors: (e: FieldErrors) => void): string | null {
  if (err instanceof ApiError) {
    if (err.status === 409) {
      return err.message;
    }
    if (err.status === 400 && err.fields && Object.keys(err.fields).length > 0) {
      setFieldErrors(err.fields as FieldErrors);
      return null;
    }
    if (err.status === 400) {
      return err.message;
    }
  }
  return "That didn't go through. Please try again.";
}

export function AddTechnicianDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const [data, setData] = useState<AddTechnicianFormData>(EMPTY_FORM);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const mutation = useCreateTechnician();

  useEffect(() => {
    if (!open) {
      setData(EMPTY_FORM);
      setErrors({});
      setFormError(null);
    }
  }, [open]);

  function patch(updates: Partial<AddTechnicianFormData>) {
    setData((d) => ({ ...d, ...updates }));
    const keys = Object.keys(updates) as (keyof AddTechnicianFormData)[];
    setErrors((prev) => {
      if (!keys.some((k) => prev[k])) return prev;
      const next = { ...prev };
      for (const k of keys) delete next[k];
      return next;
    });
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const validationErrors = validate(data);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    setFormError(null);

    const request: CreateTechnicianRequest = {
      firstName: data.firstName.trim(),
      lastName: data.lastName.trim(),
      email: data.email.trim(),
      phone: data.phone.trim() || undefined,
    };

    mutation.mutate(request, {
      onSuccess: () => {
        toast.success("Invitation sent");
        onOpenChange(false);
      },
      onError: (err) => setFormError(describeError(err, setErrors)),
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-2xl font-extrabold tracking-tight">
            Invite technician
          </DialogTitle>
          <DialogDescription>
            Send an invite by email. They&rsquo;ll set their own password and appear on the roster
            as pending until they accept.
          </DialogDescription>
        </DialogHeader>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit} noValidate>
          <fieldset
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
            className="space-y-4"
          >
            <legend className="sr-only">Technician invite details</legend>

            <div className="grid grid-cols-2 gap-3">
              <Field label="First name" error={errors.firstName}>
                <Input
                  placeholder="Jordan"
                  value={data.firstName}
                  onChange={(e) => patch({ firstName: e.target.value })}
                  aria-invalid={!!errors.firstName}
                />
              </Field>
              <Field label="Last name" error={errors.lastName}>
                <Input
                  placeholder="Lee"
                  value={data.lastName}
                  onChange={(e) => patch({ lastName: e.target.value })}
                  aria-invalid={!!errors.lastName}
                />
              </Field>
            </div>
            <Field label="Email" error={errors.email}>
              <Input
                type="email"
                placeholder="jordan@example.com"
                value={data.email}
                onChange={(e) => patch({ email: e.target.value })}
                aria-invalid={!!errors.email}
              />
            </Field>
            <Field label="Phone (optional)" error={errors.phone}>
              <Input
                type="tel"
                placeholder="(905) 555-0123"
                value={data.phone}
                onChange={(e) => patch({ phone: e.target.value })}
                aria-invalid={!!errors.phone}
              />
            </Field>
          </fieldset>

          {formError && (
            <p role="alert" className="text-sm font-semibold text-destructive">
              {formError}
            </p>
          )}

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending} aria-busy={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
              Send invite
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {children}
      {error && (
        <span role="alert" className="mt-1 block text-xs font-semibold text-destructive">
          {error}
        </span>
      )}
    </label>
  );
}
