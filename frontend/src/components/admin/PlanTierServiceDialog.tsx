/**
 * Add-to-plan / edit-frequency dialog for a plan tier's service composition
 * (`POST`/`PATCH /api/admin/plan-tiers/{planTierId}/services...`). One dialog, two modes,
 * because they're the same one-or-two-field form: "add" picks a service and a frequency,
 * "edit" just changes the frequency of a service already on the plan.
 *
 * The picker only ever offers services that aren't already on this plan (`availableServices`
 * is pre-filtered by the caller), so the backend's `409` on a duplicate add should be
 * unreachable from here in the common case — it's still handled below for the case where
 * another admin added the same service in the meantime.
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
import { ApiError } from "@/lib/api";
import type { AdminServiceItem } from "@/lib/admin";
import { useAddPlanTierService, useUpdatePlanTierServiceFrequency } from "@/lib/admin";

interface AddModeProps {
  mode: "add";
  availableServices: AdminServiceItem[];
}

interface EditModeProps {
  mode: "edit";
  existing: { serviceId: number; serviceName: string; frequencyPerYear: number };
}

type PlanTierServiceDialogProps = (AddModeProps | EditModeProps) & {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  planTierId: number;
  planDisplayName: string;
  onSaved?: () => void;
};

function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 409) {
      return "That service is already on this plan. Someone may have just added it. Close this and check the list.";
    }
    return err.message;
  }
  return "That didn't go through. Please try again.";
}

export function PlanTierServiceDialog(props: PlanTierServiceDialogProps) {
  const { open, onOpenChange, planTierId, planDisplayName, onSaved } = props;
  const isAdd = props.mode === "add";

  const [serviceId, setServiceId] = useState<string>("");
  const [frequency, setFrequency] = useState<string>(
    isAdd ? "" : String(props.existing.frequencyPerYear),
  );
  const [error, setError] = useState<string | null>(null);
  const [frequencyError, setFrequencyError] = useState<string | null>(null);

  const addMutation = useAddPlanTierService(planTierId);
  const updateMutation = useUpdatePlanTierServiceFrequency(planTierId);
  const mutation = isAdd ? addMutation : updateMutation;

  const baseId = useId();
  const freqId = `${baseId}-frequency`;
  const serviceSelectId = `${baseId}-service`;

  useEffect(() => {
    if (!open) return;
    setError(null);
    setFrequencyError(null);
    if (isAdd) {
      setServiceId("");
      setFrequency("");
    } else {
      setFrequency(String(props.existing.frequencyPerYear));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const freqNum = Number(frequency);
    if (!frequency.trim() || !Number.isInteger(freqNum) || freqNum <= 0) {
      setFrequencyError("Enter a whole number of times per year, greater than 0");
      return;
    }
    setFrequencyError(null);

    if (isAdd) {
      if (!serviceId) {
        setError("Choose a service to add");
        return;
      }
      addMutation.mutate(
        { serviceId: Number(serviceId), frequencyPerYear: freqNum },
        {
          onSuccess: () => {
            onOpenChange(false);
            onSaved?.();
          },
          onError: (err) => setError(describeError(err)),
        },
      );
    } else {
      updateMutation.mutate(
        { serviceId: props.existing.serviceId, frequencyPerYear: freqNum },
        {
          onSuccess: () => {
            onOpenChange(false);
            onSaved?.();
          },
          onError: (err) => setError(describeError(err)),
        },
      );
    }
  }

  const noneAvailable = isAdd && props.availableServices.length === 0;

  return (
    <Dialog open={open} onOpenChange={(next) => !mutation.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-2xl font-extrabold tracking-tight">
            {isAdd ? "Add a service" : "Change frequency"}
          </DialogTitle>
          <DialogDescription>
            {isAdd
              ? `Choose a service and how many times per year it runs on ${planDisplayName}.`
              : `How many times per year "${props.existing.serviceName}" runs on ${planDisplayName}.`}
          </DialogDescription>
        </DialogHeader>

        {noneAvailable ? (
          <>
            <p className="text-sm text-muted-foreground">
              Every active service is already on this plan. Create a new service first, or restore
              an archived one, to add something here.
            </p>
            <div className="flex justify-end border-t border-border pt-4">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Close
              </Button>
            </div>
          </>
        ) : (
          <form className="mt-2 space-y-4" onSubmit={handleSubmit} noValidate>
            <fieldset
              disabled={mutation.isPending}
              aria-busy={mutation.isPending}
              className="space-y-4"
            >
              <legend className="sr-only">{isAdd ? "Service and frequency" : "Frequency"}</legend>

              {isAdd && (
                <div>
                  <Label htmlFor={serviceSelectId}>Service</Label>
                  <Select value={serviceId} onValueChange={setServiceId}>
                    <SelectTrigger id={serviceSelectId} className="mt-1">
                      <SelectValue placeholder="Choose a service" />
                    </SelectTrigger>
                    <SelectContent>
                      {props.availableServices.map((s) => (
                        <SelectItem key={s.id} value={String(s.id)}>
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div>
                <Label htmlFor={freqId}>Times per year</Label>
                <Input
                  id={freqId}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  step={1}
                  value={frequency}
                  onChange={(e) => {
                    setFrequency(e.target.value);
                    setFrequencyError(null);
                  }}
                  aria-invalid={!!frequencyError}
                  aria-describedby={frequencyError ? `${freqId}-error` : undefined}
                  className="mt-1"
                />
                {frequencyError && (
                  <p id={`${freqId}-error`} role="alert" className="mt-1 text-xs text-destructive">
                    {frequencyError}
                  </p>
                )}
              </div>
            </fieldset>

            {error && (
              <p role="alert" className="text-sm font-semibold text-destructive">
                {error}
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
                {mutation.isPending && (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                )}
                {isAdd ? "Add to plan" : "Save frequency"}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
