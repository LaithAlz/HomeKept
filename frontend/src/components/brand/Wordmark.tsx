import { cn } from "@/lib/utils";

interface WordmarkProps {
  className?: string;
  size?: "sm" | "md" | "lg" | "xl";
  as?: "span" | "div" | "h1";
}

const sizeMap = {
  sm: "text-lg",
  md: "text-2xl",
  lg: "text-4xl",
  xl: "text-6xl md:text-7xl",
};

export function Wordmark({ className, size = "md", as: As = "span" }: WordmarkProps) {
  return (
    <As
      className={cn(
        "font-display font-extrabold tracking-tight text-foreground select-none",
        sizeMap[size],
        className,
      )}
      aria-label="HomeKept."
    >
      HomeKept<span className="text-accent">.</span>
    </As>
  );
}
