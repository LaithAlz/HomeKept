/**
 * Add technician sheet — onboards a `technician_profile` for a user who already
 * exists and already has the TECHNICIAN role (role assignment happens separately,
 * outside this form — see `POST /api/admin/technicians`, api-contract.md line 206).
 *
 * There is currently no admin UI to create the technician *user* itself (no invite
 * or "create staff account" flow) — that's a real product gap, not something this
 * form can paper over. See the report for this issue for a flagged follow-up.
 */
import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ApiError } from "@/lib/api";
import {
  EMPLOYEE_STATUS_OPTIONS,
  useCreateTechnician,
  type CreateTechnicianRequest,
} from "@/lib/admin";

interface AddTechnicianFormData {
  userId: string;
  hourlyCostDollars: string;
  employeeStatus: string;
  hireDate: string;
}

const EMPTY_FORM: AddTechnicianFormData = {
  userId: "",
  hourlyCostDollars: "",
  employeeStatus: "",
  hireDate: "",
};

type FieldErrors = Partial<Record<"userId" | "hourlyCostDollars", string>>;

function validate(f: AddTechnicianFormData): FieldErrors {
  const errs: FieldErrors = {};
  const userId = Number(f.userId.trim());
  if (!f.userId.trim() || !Number.isInteger(userId) || userId <= 0) {
    errs.userId = "Enter a valid user id";
  }
  const dollars = Number.parseFloat(f.hourlyCostDollars.trim());
  if (!f.hourlyCostDollars.trim() || Number.isNaN(dollars) || dollars <= 0) {
    errs.hourlyCostDollars = "Enter the fully loaded hourly cost";
  }
  return errs;
}

/**
 * Both a nonexistent `userId` and an already-onboarded `userId` currently surface
 * as the same 409 CONFLICT from the backend (a DB FK violation for the former, the
 * dedicated `TechnicianAlreadyExistsException` for the latter — see
 * `GlobalExceptionHandler.handleDataIntegrity`/`handleConflict`). The two are told
 * apart here by matching on the duplicate-profile message text; anything else on a
 * 409 is the FK-violation path, i.e. no such user.
 */
function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 409) {
      return err.message.toLowerCase().includes("already exists")
        ? "That user already has a technician profile."
        : "No user with that id.";
    }
    if (err.status === 404) {
      return "No user with that id.";
    }
    if (err.status === 400 && err.fields && Object.keys(err.fields).length > 0) {
      return Object.values(err.fields).join(" ");
    }
    if (err.status === 400) {
      return err.message;
    }
  }
  return "That didn't go through. Please try again.";
}

export function AddTechnicianSheet({
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
      if (!keys.some((k) => k in prev && prev[k as keyof FieldErrors])) return prev;
      const next = { ...prev };
      for (const k of keys) delete next[k as keyof FieldErrors];
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
      userId: Number(data.userId.trim()),
      // Dollars -> integer cents: parse the decimal string, then round — never
      // float math on the display value itself (CLAUDE.md "money is integer cents").
      fullyLoadedHourlyCostCents: Math.round(
        Number.parseFloat(data.hourlyCostDollars.trim()) * 100,
      ),
      employeeStatus: data.employeeStatus || undefined,
      hireDate: data.hireDate || undefined,
    };

    mutation.mutate(request, {
      onSuccess: () => {
        toast.success("Technician added");
        onOpenChange(false);
      },
      onError: (err) => setFormError(describeError(err)),
    });
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="font-display text-2xl font-extrabold tracking-tight">
            Add technician
          </SheetTitle>
          <SheetDescription>
            Create a technician profile for an existing account so it appears on the roster.
          </SheetDescription>
        </SheetHeader>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit} noValidate>
          <fieldset
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
            className="space-y-4"
          >
            <legend className="sr-only">Technician onboarding details</legend>

            <div>
              <Label htmlFor="add-tech-user-id">User id</Label>
              <Input
                id="add-tech-user-id"
                type="number"
                inputMode="numeric"
                min={1}
                step={1}
                value={data.userId}
                onChange={(e) => patch({ userId: e.target.value })}
                aria-invalid={!!errors.userId}
                aria-describedby="add-tech-user-id-help"
                className="mt-1"
              />
              <p id="add-tech-user-id-help" className="mt-1 text-xs text-muted-foreground">
                The person must already have an account with the technician role.
              </p>
              {errors.userId && (
                <p role="alert" className="mt-1 text-xs font-semibold text-destructive">
                  {errors.userId}
                </p>
              )}
            </div>

            <div>
              <Label htmlFor="add-tech-hourly-cost">Fully loaded hourly cost</Label>
              <Input
                id="add-tech-hourly-cost"
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                placeholder="0.00"
                value={data.hourlyCostDollars}
                onChange={(e) => patch({ hourlyCostDollars: e.target.value })}
                aria-invalid={!!errors.hourlyCostDollars}
                className="mt-1"
              />
              {errors.hourlyCostDollars && (
                <p role="alert" className="mt-1 text-xs font-semibold text-destructive">
                  {errors.hourlyCostDollars}
                </p>
              )}
            </div>

            <div>
              <Label htmlFor="add-tech-employee-status">Employee status</Label>
              <Select
                value={data.employeeStatus}
                onValueChange={(v) => patch({ employeeStatus: v })}
                disabled={mutation.isPending}
              >
                <SelectTrigger id="add-tech-employee-status" className="mt-1">
                  <SelectValue placeholder="Not set" />
                </SelectTrigger>
                <SelectContent>
                  {EMPLOYEE_STATUS_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="add-tech-hire-date">Hire date</Label>
              <Input
                id="add-tech-hire-date"
                type="date"
                value={data.hireDate}
                onChange={(e) => patch({ hireDate: e.target.value })}
                className="mt-1"
              />
            </div>
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
              Add technician
            </Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  );
}
