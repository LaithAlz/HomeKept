import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Archive, ArchiveRestore, Lock, Pencil, Plus, Trash2, Wrench } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PlanTierServiceDialog } from "@/components/admin/PlanTierServiceDialog";
import { ServiceFormDialog } from "@/components/admin/ServiceFormDialog";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { formatCentsCad } from "@/lib/format";
import {
  useCatalogPlans,
  type PlanCode,
  type PlanTierResponse,
  type TierClass,
  type ServiceCategory,
} from "@/lib/catalog";
import {
  useAdminServices,
  useArchiveService,
  useRestoreService,
  useRemovePlanTierService,
  type AdminServiceItem,
} from "@/lib/admin";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/catalog")({
  head: () => ({
    meta: [{ title: "Service catalog — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: CatalogPage,
});

const PLAN_ORDER: PlanCode[] = ["ESSENTIAL", "COMPLETE", "PREMIER"];

const TIER_CLASS_LABEL: Record<TierClass, string> = {
  BASIC: "Basic",
  MEDIUM: "Medium",
  PREMIUM: "Premium",
};

const TIER_CLASS_TONE: Record<TierClass, string> = {
  BASIC: "bg-muted text-muted-foreground",
  MEDIUM: "bg-sky-500/10 text-sky-700",
  PREMIUM: "bg-accent/20 text-accent-foreground",
};

const CATEGORY_LABEL: Record<ServiceCategory, string> = {
  HVAC: "HVAC",
  PLUMBING: "Plumbing",
  EXTERIOR: "Exterior",
  SMART_HOME: "Smart home",
};

/**
 * Since #214 landed the admin catalog-editing endpoints, this page manages which services
 * are on each plan (add / change frequency / remove) and the service catalog itself
 * (create / edit / archive / restore). Plan tier pricing, visit counts, and pick
 * allowances stay read-only by design (see the `Lock` note on each plan card): Stripe is
 * what actually charges a customer, so this console never shows a number Stripe doesn't
 * also charge.
 *
 * There is no admin endpoint to list a plan's current composition — the public
 * `GET /api/catalog/plans` (`useCatalogPlans`) is still the only source for that, so every
 * plan-composition mutation invalidates its cache alongside the admin-only service list.
 */
function CatalogPage() {
  const plans = useCatalogPlans();
  const servicesQuery = useAdminServices();

  const isLoading = plans.isLoading || servicesQuery.isLoading;
  const isError = plans.isError || servicesQuery.isError;

  const orderedPlans = plans.data
    ? [...plans.data].sort((a, b) => PLAN_ORDER.indexOf(a.code) - PLAN_ORDER.indexOf(b.code))
    : undefined;
  const serviceList = servicesQuery.data;

  return (
    <div className="px-6 py-8">
      <div>
        <h1 className="font-display text-2xl font-extrabold tracking-tight">Service catalog</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage which services are on each plan and edit the service catalog itself.
        </p>
      </div>

      {isLoading && <PanelLoading label="Loading the catalog." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load the catalog."
          onRetry={() => {
            void plans.refetch();
            void servicesQuery.refetch();
          }}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {orderedPlans && serviceList && (
        <section className="mt-6">
          <h2 className="font-display text-lg font-bold">Plans</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Add, change the frequency of, or remove a service from a plan.
          </p>
          <div className="mt-3 grid gap-4 lg:grid-cols-3">
            {orderedPlans.map((plan) => (
              <PlanCard key={plan.code} plan={plan} services={serviceList} />
            ))}
          </div>
        </section>
      )}

      {serviceList && (
        <section className="mt-8">
          <ServicesSection services={serviceList} />
        </section>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Plans
// ---------------------------------------------------------------------------

interface EditFrequencyTarget {
  serviceId: number;
  serviceName: string;
  frequencyPerYear: number;
}

interface RemoveFromPlanTarget {
  serviceId: number;
  serviceName: string;
}

function PlanCard({ plan, services }: { plan: PlanTierResponse; services: AdminServiceItem[] }) {
  const [addOpen, setAddOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<EditFrequencyTarget | null>(null);
  const [removeTarget, setRemoveTarget] = useState<RemoveFromPlanTarget | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  const removeMutation = useRemovePlanTierService(plan.id);
  const servicesById = new Map(services.map((s) => [s.id, s]));
  const availableServices = services.filter(
    (s) => s.active && !plan.services.some((ps) => ps.id === s.id),
  );

  function confirmRemove() {
    if (!removeTarget) return;
    setRemoveError(null);
    removeMutation.mutate(removeTarget.serviceId, {
      onSuccess: () => {
        toast.success(`Removed ${removeTarget.serviceName} from ${plan.displayName}`);
        setRemoveTarget(null);
      },
      onError: (err) =>
        setRemoveError(err instanceof ApiError ? err.message : "That didn't go through."),
    });
  }

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-baseline justify-between gap-2">
        <h3 className="font-display text-lg font-bold">{plan.displayName}</h3>
        <span className="text-xs tabular-nums text-muted-foreground">
          {formatCentsCad(plan.monthlyPriceCents)}/mo
        </span>
      </div>
      <p className="mt-2 flex items-start gap-1.5 text-xs text-muted-foreground">
        <Lock className="mt-0.5 h-3 w-3 shrink-0" aria-hidden="true" />
        <span>
          {plan.visitsPerYear} visits/year · {plan.includedPicksPerYear} included picks (
          {plan.maxPremiumPicksPerYear} may be Premium). Price and these numbers are set in Stripe
          and by migration, not editable here.
        </span>
      </p>

      <ul className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
        {plan.services.map((s) => {
          const archived = servicesById.get(s.id)?.active === false;
          return (
            <li key={s.id} className="flex items-center justify-between gap-2">
              <span className="flex min-w-0 items-center gap-2">
                <Wrench className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className={cn("truncate", archived && "text-muted-foreground")}>
                  {s.name}
                </span>
                {archived && (
                  <span
                    className="shrink-0 rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground"
                    title="This service is archived but still included on this plan. Remove it here if it shouldn't be."
                  >
                    Archived
                  </span>
                )}
              </span>
              <span className="flex shrink-0 items-center gap-1">
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-xs font-medium",
                    TIER_CLASS_TONE[s.tierClass],
                  )}
                >
                  {TIER_CLASS_LABEL[s.tierClass]}
                </span>
                <span className="tabular-nums text-xs text-muted-foreground">
                  {s.frequencyPerYear}x/yr
                </span>
                <button
                  type="button"
                  onClick={() =>
                    setEditTarget({
                      serviceId: s.id,
                      serviceName: s.name,
                      frequencyPerYear: s.frequencyPerYear,
                    })
                  }
                  className="rounded p-1 text-muted-foreground transition-colors hover:bg-surface hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                  <span className="sr-only">
                    Change how often {s.name} runs on {plan.displayName}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setRemoveTarget({ serviceId: s.id, serviceName: s.name })}
                  className="rounded p-1 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  <span className="sr-only">
                    Remove {s.name} from {plan.displayName}
                  </span>
                </button>
              </span>
            </li>
          );
        })}
        {plan.services.length === 0 && (
          <li className="text-sm text-muted-foreground">No services on this plan yet.</li>
        )}
      </ul>

      <div className="mt-4 border-t border-border pt-4">
        <Button size="sm" variant="outline" onClick={() => setAddOpen(true)}>
          <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          Add service
        </Button>
      </div>

      <PlanTierServiceDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        planTierId={plan.id}
        planDisplayName={plan.displayName}
        mode="add"
        availableServices={availableServices}
      />

      {editTarget && (
        <PlanTierServiceDialog
          open
          onOpenChange={(open) => !open && setEditTarget(null)}
          planTierId={plan.id}
          planDisplayName={plan.displayName}
          mode="edit"
          existing={editTarget}
        />
      )}

      {removeTarget && (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) {
              setRemoveTarget(null);
              setRemoveError(null);
            }
          }}
          title="Remove this service from the plan?"
          description={`"${removeTarget.serviceName}" will no longer run on ${plan.displayName}. This only changes the plan; the service itself stays in the catalog.`}
          confirmLabel="Remove from plan"
          confirmVariant="destructive"
          onConfirm={confirmRemove}
          pending={removeMutation.isPending}
          error={removeError}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Services
// ---------------------------------------------------------------------------

function ServicesSection({ services }: { services: AdminServiceItem[] }) {
  const [createOpen, setCreateOpen] = useState(false);
  const [editingService, setEditingService] = useState<AdminServiceItem | null>(null);

  const sorted = [...services].sort((a, b) => {
    if (a.tierClass !== b.tierClass) {
      const order: TierClass[] = ["BASIC", "MEDIUM", "PREMIUM"];
      return order.indexOf(a.tierClass) - order.indexOf(b.tierClass);
    }
    return a.name.localeCompare(b.name);
  });

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="font-display text-lg font-bold">Services</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Every service in the catalog, including archived ones. Archiving isn't deleting: an
            archived service stays on past visits and can be restored anytime.
          </p>
        </div>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          New service
        </Button>
      </div>

      <div className="mt-4 overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th scope="col" className="px-4 py-3">
                Name
              </th>
              <th scope="col" className="px-2 py-3">
                Category
              </th>
              <th scope="col" className="px-2 py-3">
                Tier
              </th>
              <th scope="col" className="px-2 py-3 text-right">
                Duration
              </th>
              <th scope="col" className="px-2 py-3 text-right">
                À la carte price
              </th>
              <th scope="col" className="px-2 py-3">
                Status
              </th>
              <th scope="col" className="px-2 py-3 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((service) => (
              <ServiceRow
                key={service.id}
                service={service}
                onEdit={() => setEditingService(service)}
              />
            ))}
            {sorted.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-sm text-muted-foreground">
                  No services in the catalog yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <ServiceFormDialog open={createOpen} onOpenChange={setCreateOpen} />

      {editingService && (
        <ServiceFormDialog
          open
          onOpenChange={(open) => !open && setEditingService(null)}
          service={editingService}
        />
      )}
    </>
  );
}

function ServiceRow({ service, onEdit }: { service: AdminServiceItem; onEdit: () => void }) {
  const [confirmAction, setConfirmAction] = useState<"archive" | "restore" | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const archiveMutation = useArchiveService();
  const restoreMutation = useRestoreService();

  const pending = archiveMutation.isPending || restoreMutation.isPending;

  function confirmToggle() {
    setActionError(null);
    if (confirmAction === "archive") {
      archiveMutation.mutate(service.id, {
        onSuccess: () => {
          toast.success(`Archived ${service.name}`);
          setConfirmAction(null);
        },
        onError: (err) =>
          setActionError(err instanceof ApiError ? err.message : "That didn't go through."),
      });
    } else if (confirmAction === "restore") {
      restoreMutation.mutate(service.id, {
        onSuccess: () => {
          toast.success(`Restored ${service.name}`);
          setConfirmAction(null);
        },
        onError: (err) =>
          setActionError(err instanceof ApiError ? err.message : "That didn't go through."),
      });
    }
  }

  return (
    <tr className={cn("border-t border-border hover:bg-muted/30", !service.active && "opacity-70")}>
      <td className="px-4 py-3">
        <div className="font-medium text-foreground">{service.name}</div>
        {service.isFreeWithEveryVisit && (
          <div className="text-xs text-muted-foreground">Standing item, free with every visit</div>
        )}
      </td>
      <td className="px-2 py-3">{CATEGORY_LABEL[service.category]}</td>
      <td className="px-2 py-3">
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            TIER_CLASS_TONE[service.tierClass],
          )}
        >
          {TIER_CLASS_LABEL[service.tierClass]}
        </span>
      </td>
      <td className="px-2 py-3 text-right tabular-nums">{service.defaultDurationMinutes}m</td>
      <td className="px-2 py-3 text-right tabular-nums">
        {service.aLaCartePriceCents !== null ? formatCentsCad(service.aLaCartePriceCents) : "—"}
      </td>
      <td className="px-2 py-3">
        {service.active ? (
          <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700">
            Active
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
            <Archive className="h-3 w-3" aria-hidden="true" />
            Archived
          </span>
        )}
      </td>
      <td className="px-2 py-3">
        <div className="flex items-center justify-end gap-1">
          <Button size="sm" variant="outline" onClick={onEdit}>
            <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
            Edit
          </Button>
          {service.active ? (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setConfirmAction("archive")}
              disabled={pending}
            >
              <Archive className="h-3.5 w-3.5" aria-hidden="true" />
              Archive
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setConfirmAction("restore")}
              disabled={pending}
            >
              <ArchiveRestore className="h-3.5 w-3.5" aria-hidden="true" />
              Restore
            </Button>
          )}
        </div>
        {confirmAction && (
          <ConfirmDialog
            open
            onOpenChange={(open) => {
              if (!open) {
                setConfirmAction(null);
                setActionError(null);
              }
            }}
            title={confirmAction === "archive" ? "Archive this service?" : "Restore this service?"}
            description={
              confirmAction === "archive"
                ? `"${service.name}" won't be deleted. It stays on past visits and any plan it's already part of, and can be restored anytime. It just becomes unavailable to add to a plan${service.isFreeWithEveryVisit ? "" : " and stops appearing in the customer's picks menu"} for future bookings.`
                : `"${service.name}" becomes available to add to a plan again${service.isFreeWithEveryVisit ? "" : ", and reappears in the customer's picks menu"}.`
            }
            confirmLabel={confirmAction === "archive" ? "Archive" : "Restore"}
            confirmVariant={confirmAction === "archive" ? "destructive" : "default"}
            onConfirm={confirmToggle}
            pending={pending}
            error={actionError}
          />
        )}
      </td>
    </tr>
  );
}
