export function AdminStub({ title, body }: { title: string; body: string }) {
  return (
    <div className="px-6 py-10">
      <h1 className="font-display text-2xl font-extrabold tracking-tight">
        {title}
      </h1>
      <p className="mt-2 max-w-2xl text-sm text-muted-foreground">{body}</p>
      <div className="mt-6 rounded-2xl border border-dashed border-border bg-card p-8 text-sm text-muted-foreground">
        This section is part of the internal admin console. Detailed UI lands
        in a follow-up.
      </div>
    </div>
  );
}
