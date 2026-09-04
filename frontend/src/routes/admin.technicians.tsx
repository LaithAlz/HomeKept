import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { AddTechnicianSheet } from "@/components/admin/AddTechnicianSheet";
import { useAdminTechnicians, type AdminTechnicianListItem } from "@/lib/admin";
import { formatCentsCad } from "@/lib/format";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/technicians")({
  head: () => ({
    meta: [{ title: "Technicians — HomeKept Admin" }, { name: "robots", content: "noindex" }],
  }),
  component: TechniciansPage,
});

const USER_STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  PENDING_ACTIVATION: "Pending activation",
  SUSPENDED: "Suspended",
};

const USER_STATUS_TONE: Record<string, string> = {
  ACTIVE: "bg-emerald-500/10 text-emerald-700",
  PENDING_ACTIVATION: "bg-sky-500/10 text-sky-700",
  SUSPENDED: "bg-rose-500/10 text-rose-700",
};

/**
 * `hireDate` is a LocalDate ("YYYY-MM-DD") — a calendar date with no time-of-day
 * meaning. Anchoring to UTC noon before formatting avoids the off-by-one day a
 * viewer west of UTC would otherwise see (same fix as `formatWeekOf` on the
 * walk-throughs page, which faces the same LocalDate-vs-Instant issue).
 */
function formatHireDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "UTC",
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(new Date(`${dateStr}T12:00:00Z`));
}

function humanize(value: string | null): string {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function TechniciansPage() {
  const { data: technicians, isLoading, isError, refetch } = useAdminTechnicians();
  const [addOpen, setAddOpen] = useState(false);

  return (
    <div className="px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-extrabold tracking-tight">Technicians</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {technicians ? `${technicians.length} on the roster` : "Loading the roster…"}
          </p>
        </div>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          Add technician
        </Button>
      </div>

      <AddTechnicianSheet open={addOpen} onOpenChange={setAddOpen} />

      {isLoading && <PanelLoading label="Loading technicians." className="mt-6" />}

      {isError && !isLoading && (
        <PanelError
          label="We couldn't load the technician roster."
          onRetry={() => void refetch()}
          className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3"
        />
      )}

      {technicians && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-2 py-3">Role</th>
                <th className="px-2 py-3">Status</th>
                <th className="px-2 py-3">Employee status</th>
                <th className="px-2 py-3">Hire date</th>
                <th className="px-2 py-3 text-right">Hourly cost</th>
              </tr>
            </thead>
            <tbody>
              {technicians.map((t) => (
                <TechnicianRow key={t.id} technician={t} />
              ))}
              {technicians.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No technicians on the roster yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function TechnicianRow({ technician: t }: { technician: AdminTechnicianListItem }) {
  const name = [t.firstName, t.lastName].filter(Boolean).join(" ");
  return (
    <tr className="border-t border-border hover:bg-muted/30">
      <td className="px-4 py-3">
        <div className="font-medium text-foreground">{name || "—"}</div>
        <div className="text-xs text-muted-foreground">{t.email ?? "—"}</div>
      </td>
      <td className="px-2 py-3">{humanize(t.role)}</td>
      <td className="px-2 py-3">
        {t.userStatus ? (
          <span
            className={cn(
              "rounded-full px-2 py-0.5 text-xs font-medium",
              USER_STATUS_TONE[t.userStatus] ?? "bg-muted text-muted-foreground",
            )}
          >
            {USER_STATUS_LABEL[t.userStatus] ?? t.userStatus}
          </span>
        ) : (
          "—"
        )}
      </td>
      <td className="px-2 py-3">{humanize(t.employeeStatus)}</td>
      <td className="px-2 py-3">{formatHireDate(t.hireDate)}</td>
      <td className="px-2 py-3 text-right tabular-nums">
        {t.fullyLoadedHourlyCostCents != null
          ? `${formatCentsCad(t.fullyLoadedHourlyCostCents)}/hr`
          : "—"}
      </td>
    </tr>
  );
}
