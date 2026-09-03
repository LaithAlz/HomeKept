/**
 * Decorative ambient background light shared by the marketing pages
 * (landing + the three city pages). Purely visual — `aria-hidden`.
 */
export function AmbientGlows() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
      <div className="absolute -right-36 -top-44 size-[560px] animate-drift rounded-full bg-sage/40 blur-[90px]" />
      <div className="absolute -left-52 top-[45vh] size-[460px] animate-drift rounded-full bg-honey-soft/50 blur-[90px] [animation-direction:alternate-reverse]" />
    </div>
  );
}
