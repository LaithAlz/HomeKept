import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PanelLoading, PanelError } from "@/components/admin/PanelStates";
import { AddTechnicianDialog } from "@/components/admin/AddTechnicianDialog";
import {
  useAdminTechnicians,
  useResendTechnicianInvite,
  type AdminTechnicianListItem,
} from "@/lib/admin";
import { ApiError } from "@/lib/api";
import { formatCentsCad, formatDateTime } from "@/lib/format";
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
          Invite technician
        </Button>
      </div>

      <AddTechnicianDialog open={addOpen} onOpenChange={setAddOpen} />

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
                <th className="px-2 py-3">Invite</th>
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
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-muted-foreground">
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
      <td className="px-2 py-3">
        <InviteCell technician={t} />
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

/**
 * Shows when the invite was last sent (or resent), and — only while the technician is
 * still pending — a Resend button. Mirrors the walk-through pipeline's
 * `invitedAt` + resend pattern (`routes/admin.walkthroughs.tsx`'s `PerformedActions`):
 * the mutation invalidates the roster query, so the refreshed `invitedAt` from the
 * server is the only source of truth here (no local "just resent" flag needed — the
 * button stays visible either way while pending).
 */
function InviteCell({ technician: t }: { technician: AdminTechnicianListItem }) {
  const resend = useResendTechnicianInvite();
  const [error, setError] = useState<string | null>(null);
  const isPending = t.userStatus === "PENDING_ACTIVATION";

  function handleResend() {
    setError(null);
    resend.mutate(t.id, {
      // A 409 (e.g. this technician already accepted, or was suspended, since this list
      // was loaded) carries the backend's specific reason; anything else gets the generic
      // line. Either way the roster query also refetches (see useResendTechnicianInvite),
      // so a 409 here corrects the row's status/button on screen.
      onError: (err) =>
        setError(
          err instanceof ApiError && err.status === 409
            ? err.message
            : "Couldn't resend the invite. Please try again.",
        ),
    });
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <span className="text-xs text-muted-foreground">
        {t.invitedAt ? `Invited ${formatDateTime(t.invitedAt)}` : "—"}
      </span>
      {isPending && (
        <Button
          size="sm"
          variant="outline"
          disabled={resend.isPending}
          aria-busy={resend.isPending}
          onClick={handleResend}
        >
          {resend.isPending && (
            <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" aria-hidden="true" />
          )}
          Resend invite
        </Button>
      )}
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
