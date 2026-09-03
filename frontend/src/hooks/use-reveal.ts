import { useEffect, useRef } from "react";

/**
 * Same contract for a group: reveals every `[data-reveal]` descendant of the
 * container as it scrolls into view, staggered by `--reveal-index`.
 */
export function useRevealGroup<T extends HTMLElement>(threshold = 0.16) {
  const ref = useRef<T>(null);

  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    // querySelectorAll only matches descendants — include the root itself when tagged.
    const targets = [
      ...(root.matches("[data-reveal]") ? [root] : []),
      ...root.querySelectorAll<HTMLElement>("[data-reveal]"),
    ];
    if (targets.length === 0) return;
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
