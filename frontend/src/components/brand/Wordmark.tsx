import { cn } from "@/lib/utils";

interface WordmarkProps {
  className?: string;
  size?: "sm" | "md" | "lg" | "xl";
  as?: "span" | "div" | "h1";
  withMark?: boolean;
}

const sizeMap = {
  sm: "text-lg",
  md: "text-2xl",
  lg: "text-4xl",
  xl: "text-6xl md:text-7xl",
};

const markSizeMap = {
  sm: "size-5",
  md: "size-6",
  lg: "size-9",
  xl: "size-14",
};

/** Leaf brand mark: a circle with one clipped corner, honey core. */
export function LeafMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "relative inline-block shrink-0 bg-primary [border-radius:50%_50%_50%_22%]",
        className,
      )}
    >
      <span className="absolute inset-[30%] bg-accent [border-radius:50%_50%_50%_22%]" />
    </span>
  );
}

export function Wordmark({
  className,
  size = "md",
  as: As = "span",
  withMark = true,
}: WordmarkProps) {
  return (
    <As
      className={cn(
        "inline-flex select-none items-center gap-2 font-bold tracking-tight text-primary",
        sizeMap[size],
        className,
      )}
      aria-label="HomeKept"
    >
      {withMark && <LeafMark className={markSizeMap[size]} />}
      HomeKept
    </As>
  );
}
