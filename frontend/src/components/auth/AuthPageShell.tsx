/**
 * The centered-card page shell shared by the customer auth flows: sign in,
 * forgot password, reset password, and activate (`routes/signin.tsx`,
 * `forgot-password.tsx`, `reset-password.tsx`, `activate.tsx`). No site nav,
 * just ambient glows, the HomeKept wordmark linking home, and a max-width
 * column for the card content each route supplies as `children`.
 *
 * Two structural variants were already in use and are preserved exactly
 * (`outer`, `glow`): sign in / forgot-password wrap a single <main> landmark
 * that is itself the centering flex container, while reset-password /
 * activate center a plain <div> around the <main>. The two glow layers are
 * `fixed`, so this nesting difference has no visible effect — the variant
 * only exists so each page's original markup stays byte-for-byte.
 */
import { Link } from "@tanstack/react-router";
import { Wordmark } from "@/components/brand/Wordmark";
import { cn } from "@/lib/utils";

type Glow = "compact" | "wide";

const GLOW_CLASSES: Record<Glow, { top: string; bottom: string }> = {
  compact: {
    top: "absolute -right-36 -top-44 size-[460px] animate-drift rounded-full bg-sage/35 blur-[90px]",
    bottom:
      "absolute -left-52 bottom-[-120px] size-[400px] animate-drift rounded-full bg-honey-soft/45 blur-[90px] [animation-direction:alternate-reverse]",
  },
  wide: {
    top: "absolute -right-28 -top-36 size-[480px] animate-drift rounded-full bg-sage/35 blur-[90px]",
    bottom:
      "absolute -left-40 bottom-[-130px] size-[420px] animate-drift rounded-full bg-honey-soft/45 blur-[90px] [animation-direction:alternate-reverse]",
  },
};

export function AuthPageShell({
  outer,
  glow,
  maxWidthClassName,
  children,
}: {
  /** "flex": signin.tsx / forgot-password.tsx. "grid": reset-password.tsx / activate.tsx. */
  outer: "flex" | "grid";
  glow: Glow;
  maxWidthClassName: string;
  children: React.ReactNode;
}) {
  const glowLayer = (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
      <div className={GLOW_CLASSES[glow].top} />
      <div className={GLOW_CLASSES[glow].bottom} />
    </div>
  );

  const logo = (
    <Link to="/" className="mb-6 flex items-center justify-center" aria-label="HomeKept home">
      <Wordmark size="md" />
    </Link>
  );

  if (outer === "flex") {
    return (
      <main
        id="main"
        className="relative flex min-h-dvh items-center justify-center overflow-x-clip bg-background px-4 py-10"
      >
        {glowLayer}
        <div className={cn("animate-reveal relative z-10 w-full", maxWidthClassName)}>
          {logo}
          {children}
        </div>
      </main>
    );
  }

  return (
    <div className="grid min-h-dvh place-items-center overflow-x-clip bg-background px-4 py-10">
      {glowLayer}
      <main id="main" className={cn("relative z-10 w-full animate-reveal", maxWidthClassName)}>
        {logo}
        {children}
      </main>
    </div>
  );
}
