/**
 * Shared field primitives for the hand-rolled (non-shadcn) forms: the
 * customer booking wizard and the auth flows (sign in, forgot/reset
 * password, activate). All five routes rendered byte-identical copies of
 * these three helpers; this is the one shared copy.
 */
import { cn } from "@/lib/utils";

export function fieldCls(invalid: boolean) {
  return cn(
    "w-full rounded-2xl border-[1.5px] bg-background px-4 py-3 text-[15px] text-foreground outline-none transition-all duration-200",
    "placeholder:text-muted-foreground/60",
    "focus:border-moss focus:bg-white focus:shadow-[0_0_0_4px_rgba(92,125,112,0.15)]",
    invalid && "border-destructive bg-destructive/5 focus:border-destructive",
    !invalid && "border-transparent",
  );
}

export function FieldWrap({ children, error }: { children: React.ReactNode; error?: string }) {
  return <div className={cn("space-y-1.5", error && "has-error")}>{children}</div>;
}

export function FieldError({ id, msg }: { id: string; msg?: string }) {
  if (!msg) return null;
  return (
    <p id={id} role="alert" className="text-[12.5px] font-semibold text-destructive">
      {msg}
    </p>
  );
}
