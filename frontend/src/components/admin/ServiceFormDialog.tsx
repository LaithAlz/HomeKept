/**
 * Create/edit dialog for a single catalog service (`POST`/`PATCH /api/admin/services...`).
 * A centered modal, not a side sheet — the founder explicitly rejected a sliding panel for
 * this console (see `admin.customers.$id.tsx`'s history).
 *
 * Two things this form has to get right:
 *   - Money: the input collects dollars, the API takes integer cents
 *     (`parseDollarsToCents`/`centsToDollarsInput` from `@/lib/format`) — never a float
 *     multiply. `aLaCartePriceCents` cannot be reset back to `null` once set (backend
 *     limitation), so once an edited service already has a price, this field is required
 *     and says so rather than silently doing nothing if cleared.
 *   - `isFreeWithEveryVisit`: a switch, not a bare checkbox, with the consequence spelled
 *     out next to it — it decides whether the service can ever appear in a customer's à la
 *     carte pick menu.
 */
import { useEffect, useId, useState, type FormEvent } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import { centsToDollarsInput, parseDollarsToCents } from "@/lib/format";
import type { ServiceCategory, TierClass } from "@/lib/catalog";
import {
  useCreateService,
  useUpdateService,
  type AdminServiceItem,
  type AdminCreateServiceRequest,
  type AdminUpdateServiceRequest,
} from "@/lib/admin";

const CATEGORY_OPTIONS: { value: ServiceCategory; label: string }[] = [
  { value: "HVAC", label: "HVAC" },
  { value: "PLUMBING", label: "Plumbing" },
  { value: "EXTERIOR", label: "Exterior" },
  { value: "SMART_HOME", label: "Smart home" },
];

const TIER_CLASS_OPTIONS: { value: TierClass; label: string }[] = [
  { value: "BASIC", label: "Basic" },
  { value: "MEDIUM", label: "Medium" },
  { value: "PREMIUM", label: "Premium" },
];

interface FormData {
  name: string;
  category: ServiceCategory;
  tierClass: TierClass;
  defaultDurationMinutes: string;
  aLaCartePrice: string;
  description: string;
  isFreeWithEveryVisit: boolean;
}

function emptyForm(): FormData {
  return {
    name: "",
    category: "HVAC",
    tierClass: "BASIC",
    defaultDurationMinutes: "",
    aLaCartePrice: "",
    description: "",
    isFreeWithEveryVisit: false,
  };
}

function formFromService(service: AdminServiceItem): FormData {
  return {
    name: service.name,
    category: service.category,
    tierClass: service.tierClass,
    defaultDurationMinutes: String(service.defaultDurationMinutes),
    aLaCartePrice: centsToDollarsInput(service.aLaCartePriceCents),
    description: service.description,
    isFreeWithEveryVisit: service.isFreeWithEveryVisit,
  };
}

type FieldErrors = Partial<Record<keyof FormData, string>>;

/**
 * `hadPriceAlready` gates whether a blank price is allowed to submit — the backend has no
 * way to clear `aLaCartePriceCents` back to `null` once set, so once a service has a price
 * this form must not pretend clearing the box does anything.
 */
function validate(
  f: FormData,
  hadPriceAlready: boolean,
): { errors: FieldErrors; aLaCartePriceCents: number | undefined } {
  const errors: FieldErrors = {};
  if (!f.name.trim()) errors.name = "Enter a name";
  if (!f.description.trim()) errors.description = "Enter a description";

  const duration = Number(f.defaultDurationMinutes);
  if (!f.defaultDurationMinutes.trim() || !Number.isInteger(duration) || duration <= 0) {
    errors.defaultDurationMinutes = "Enter a whole number of minutes greater than 0";
  }

  const priceResult = parseDollarsToCents(f.aLaCartePrice);
  let aLaCartePriceCents: number | undefined;
  if (priceResult === "invalid" || priceResult === 0) {
    errors.aLaCartePrice = "Enter a price greater than $0, or leave it blank";
  } else if (priceResult === null) {
    if (hadPriceAlready) {
      errors.aLaCartePrice = "This can't be cleared once a price is set. Enter a price.";
    }
  } else {
    aLaCartePriceCents = priceResult;
  }

  return { errors, aLaCartePriceCents };
}

/** 400 with field errors maps onto the matching inputs; anything else is a plain sentence. */
function describeError(err: unknown, setFieldErrors: (e: FieldErrors) => void): string | null {
  if (err instanceof ApiError) {
    if (err.status === 400 && err.fields && Object.keys(err.fields).length > 0) {
      setFieldErrors(err.fields as FieldErrors);
      return null;
    }
    return err.message;
  }
  return "That didn't go through. Please try again.";
}

export function ServiceFormDialog({
  open,
  onOpenChange,
  service,
  onSaved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** `undefined` creates a new service; a value edits that existing service in place. */
  service?: AdminServiceItem;
  onSaved?: () => void;
}) {
  const isEdit = service !== undefined;
  const [data, setData] = useState<FormData>(() =>
    service ? formFromService(service) : emptyForm(),
  );
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  const createMutation = useCreateService();
  const updateMutation = useUpdateService(service?.id ?? -1);
  const mutation = isEdit ? updateMutation : createMutation;

  const baseId = useId();

  useEffect(() => {
    if (open) {
      setData(service ? formFromService(service) : emptyForm());
      setErrors({});
      setFormError(null);
    }
  }, [open, service]);

  function patchField(updates: Partial<FormData>) {
    setData((d) => ({ ...d, ...updates }));
    const keys = Object.keys(updates) as (keyof FormData)[];
    setErrors((prev) => {
      if (!keys.some((k) => prev[k])) return prev;
      const next = { ...prev };
      for (const k of keys) delete next[k];
      return next;
    });
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const hadPriceAlready = service ? service.aLaCartePriceCents !== null : false;
    const { errors: validationErrors, aLaCartePriceCents } = validate(data, hadPriceAlready);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    setFormError(null);

    const durationMinutes = Number(data.defaultDurationMinutes);

    if (isEdit) {
      const request: AdminUpdateServiceRequest = {
        name: data.name.trim(),
        category: data.category,
        tierClass: data.tierClass,
        defaultDurationMinutes: durationMinutes,
        description: data.description.trim(),
        isFreeWithEveryVisit: data.isFreeWithEveryVisit,
      };
      if (aLaCartePriceCents !== undefined) {
        request.aLaCartePriceCents = aLaCartePriceCents;
      }
      updateMutation.mutate(request, {
        onSuccess: () => {
          onOpenChange(false);
          onSaved?.();
        },
        onError: (err) => setFormError(describeError(err, setErrors)),
      });
    } else {
      const request: AdminCreateServiceRequest = {
        name: data.name.trim(),
        category: data.category,
        tierClass: data.tierClass,
        defaultDurationMinutes: durationMinutes,
        description: data.description.trim(),
        isFreeWithEveryVisit: data.isFreeWithEveryVisit,
      };
      if (aLaCartePriceCents !== undefined) {
        request.aLaCartePriceCents = aLaCartePriceCents;
      }
      createMutation.mutate(request, {
        onSuccess: () => {
          onOpenChange(false);
          onSaved?.();
        },
        onError: (err) => setFormError(describeError(err, setErrors)),
      });
    }
  }

  const priceAlreadySet = service ? service.aLaCartePriceCents !== null : false;

  return (
    <Dialog open={open} onOpenChange={(next) => !mutation.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="font-display text-2xl font-extrabold tracking-tight">
            {isEdit ? "Edit service" : "New service"}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Changes apply to future visits and the customer picks menu. Past visits keep their own record."
              : "New services start active and appear on the picks menu once given a price."}
          </DialogDescription>
        </DialogHeader>

        <form className="mt-2 space-y-4" onSubmit={handleSubmit} noValidate>
          <fieldset
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
            className="space-y-4"
          >
            <legend className="sr-only">Service details</legend>

            <div>
              <Label htmlFor={`${baseId}-name`}>Name</Label>
              <Input
                id={`${baseId}-name`}
                value={data.name}
                onChange={(e) => patchField({ name: e.target.value })}
                aria-invalid={!!errors.name}
                aria-describedby={errors.name ? `${baseId}-name-error` : undefined}
                className="mt-1"
              />
              {errors.name && (
                <p
                  id={`${baseId}-name-error`}
                  role="alert"
                  className="mt-1 text-xs text-destructive"
                >
                  {errors.name}
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor={`${baseId}-category`}>Category</Label>
                <Select
                  value={data.category}
                  onValueChange={(v) => patchField({ category: v as ServiceCategory })}
                >
                  <SelectTrigger id={`${baseId}-category`} className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORY_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        {opt.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label htmlFor={`${baseId}-tier-class`}>Tier class</Label>
                <Select
                  value={data.tierClass}
                  onValueChange={(v) => patchField({ tierClass: v as TierClass })}
                >
                  <SelectTrigger id={`${baseId}-tier-class`} className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TIER_CLASS_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        {opt.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor={`${baseId}-duration`}>Default duration (minutes)</Label>
                <Input
                  id={`${baseId}-duration`}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  step={1}
                  value={data.defaultDurationMinutes}
                  onChange={(e) => patchField({ defaultDurationMinutes: e.target.value })}
                  aria-invalid={!!errors.defaultDurationMinutes}
                  aria-describedby={
                    errors.defaultDurationMinutes ? `${baseId}-duration-error` : undefined
                  }
                  className="mt-1"
                />
                {errors.defaultDurationMinutes && (
                  <p
                    id={`${baseId}-duration-error`}
                    role="alert"
                    className="mt-1 text-xs text-destructive"
                  >
                    {errors.defaultDurationMinutes}
                  </p>
                )}
              </div>

              <div>
                <Label htmlFor={`${baseId}-price`}>À la carte price (CAD)</Label>
                <Input
                  id={`${baseId}-price`}
                  type="text"
                  inputMode="decimal"
                  placeholder="49.00"
                  value={data.aLaCartePrice}
                  onChange={(e) => patchField({ aLaCartePrice: e.target.value })}
                  aria-invalid={!!errors.aLaCartePrice}
                  aria-describedby={`${baseId}-price-hint${errors.aLaCartePrice ? ` ${baseId}-price-error` : ""}`}
                  className="mt-1"
                />
                <p id={`${baseId}-price-hint`} className="mt-1 text-xs text-muted-foreground">
                  {priceAlreadySet
                    ? "Can be changed, but not cleared back to blank once set."
                    : "Leave blank for a service that's never sold à la carte."}
                </p>
                {errors.aLaCartePrice && (
                  <p
                    id={`${baseId}-price-error`}
                    role="alert"
                    className="mt-1 text-xs text-destructive"
                  >
                    {errors.aLaCartePrice}
                  </p>
                )}
              </div>
            </div>

            <div>
              <Label htmlFor={`${baseId}-description`}>Description</Label>
              <Textarea
                id={`${baseId}-description`}
                value={data.description}
                onChange={(e) => patchField({ description: e.target.value })}
                rows={3}
                aria-invalid={!!errors.description}
                aria-describedby={errors.description ? `${baseId}-description-error` : undefined}
                className="mt-1"
              />
              {errors.description && (
                <p
                  id={`${baseId}-description-error`}
                  role="alert"
                  className="mt-1 text-xs text-destructive"
                >
                  {errors.description}
                </p>
              )}
            </div>

            <div className="flex items-start gap-3 rounded-lg border border-border bg-surface p-3">
              <Switch
                id={`${baseId}-standing`}
                checked={data.isFreeWithEveryVisit}
                onCheckedChange={(checked) => patchField({ isFreeWithEveryVisit: checked })}
                aria-describedby={`${baseId}-standing-hint`}
                className="mt-0.5"
              />
              <div>
                <Label htmlFor={`${baseId}-standing`}>Standing item, free with every visit</Label>
                <p id={`${baseId}-standing-hint`} className="mt-1 text-xs text-muted-foreground">
                  {data.isFreeWithEveryVisit
                    ? "On: this runs on every visit at no extra cost and never appears in the customer's à la carte picks menu."
                    : "Off: customers can choose this from the picks menu, either with an included pick or paid à la carte."}
                </p>
              </div>
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
              {isEdit ? "Save changes" : "Create service"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
