import { X } from "lucide-react";
import { Overlay } from "@/components/tech/TechBits";
import type { Session } from "@/lib/auth";

export function MenuSheet({
  technician,
  onClose,
  onSignOut,
}: {
  technician: Session;
  onClose: () => void;
  onSignOut: () => void;
}) {
  return (
    <Overlay onClose={onClose}>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="font-display text-xl font-bold tracking-tight">
            {technician.firstName} {technician.lastName.charAt(0)}.
          </h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close menu"
            className="inline-flex size-10 items-center justify-center rounded-full hover:bg-surface"
          >
            <X className="size-5" />
          </button>
        </div>
        <p className="text-sm text-muted-foreground">HomeKept Technician · GTA route</p>
        <ul className="mt-2 divide-y divide-border rounded-2xl border border-border">
          {["My week", "Saved photos", "Help & contact"].map((l) => (
            <li key={l} className="px-4 py-3 text-sm font-medium text-foreground/90">
              {l}
            </li>
          ))}
          <li>
            <button
              type="button"
              onClick={onSignOut}
              className="w-full px-4 py-3 text-left text-sm font-medium text-foreground/90 hover:bg-surface"
            >
              Sign out
            </button>
          </li>
        </ul>
      </div>
    </Overlay>
  );
}
