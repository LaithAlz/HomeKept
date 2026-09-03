/** A rounded card for a loading/error/empty message in the customer app shell. */
export function StatusPanel({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex items-center gap-3 rounded-3xl border border-border bg-card p-6 text-sm text-muted-foreground"
      role="status"
      aria-live="polite"
    >
      {children}
    </div>
  );
}
