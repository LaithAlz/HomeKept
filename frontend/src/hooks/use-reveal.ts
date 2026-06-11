import { useEffect, useRef } from "react";

/**
 * Scroll-triggered reveal. Pairs with the `reveal-init` utility in styles.css:
 * elements start hidden and get `.in` when they enter the viewport.
 *
 * Usage:
 *   const ref = useReveal<HTMLDivElement>();
 *   <div ref={ref} className="reveal-init" style={{ "--reveal-index": 1 }} />
 */
export function useReveal<T extends HTMLElement>(threshold = 0.16) {
  const ref = useRef<T>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (typeof IntersectionObserver === "undefined") {
      el.classList.add("in");
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add("in");
            io.unobserve(entry.target);
          }
        }
      },
      { threshold },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [threshold]);

  return ref;
}

/**
 * Same contract for a group: reveals every `[data-reveal]` descendant of the
 * container as it scrolls into view, staggered by `--reveal-index`.
 */
export function useRevealGroup<T extends HTMLElement>(threshold = 0.16) {
  const ref = useRef<T>(null);

  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const targets = root.querySelectorAll<HTMLElement>("[data-reveal]");
    if (typeof IntersectionObserver === "undefined") {
      targets.forEach((t) => t.classList.add("in"));
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add("in");
            io.unobserve(entry.target);
          }
        }
      },
      { threshold },
    );
    targets.forEach((t) => io.observe(t));
    return () => io.disconnect();
  }, [threshold]);

  return ref;
}
