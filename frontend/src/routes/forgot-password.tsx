import { useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Loader2, Mail } from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";
import { ApiError, post } from "@/lib/api";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/forgot-password")({
  head: () => ({
    meta: [
      { title: "Reset your password: HomeKept" },
      {
        name: "description",
        content: "Request a link to reset your HomeKept account password.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: ForgotPasswordPage,
});

/* -------------------------------------------------------------------------- */
/* Page shell — matches mockups/v2/signin.html's forgot-password panel: a     */
/* single centered card, no site nav, ambient glows.                          */
/* -------------------------------------------------------------------------- */

function ForgotPasswordPage() {
  return (
    <main
      id="main"
      className="relative flex min-h-dvh items-center justify-center overflow-x-clip bg-background px-4 py-10"
    >
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute -right-36 -top-44 size-[460px] animate-drift rounded-full bg-sage/35 blur-[90px]" />
        <div className="absolute -left-52 bottom-[-120px] size-[400px] animate-drift rounded-full bg-honey-soft/45 blur-[90px] [animation-direction:alternate-reverse]" />
      </div>

      <div className="animate-reveal relative z-10 w-full max-w-[420px]">
        <Link to="/" className="mb-6 flex items-center justify-center" aria-label="HomeKept home">
          <Wordmark size="md" />
        </Link>

        <div className="rounded-[30px] border border-border bg-card p-8 shadow-[0_28px_56px_-28px_rgba(9,45,33,0.35)]">
          <ForgotPasswordFlow />
        </div>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Remembered it?{" "}
          <Link
            to="/signin"
            className="font-semibold text-primary underline decoration-accent decoration-2 underline-offset-4 hover:decoration-primary"
          >
            Back to sign in
          </Link>
        </p>
      </div>
    </main>
  );
}

/* -------------------------------------------------------------------------- */
/* Flow controller — form, then a non-enumerating confirmation                */
/* -------------------------------------------------------------------------- */

function ForgotPasswordFlow() {
  const [sent, setSent] = useState(false);

  if (sent) return <SentCard />;

  return <ForgotPasswordForm onSent={() => setSent(true)} />;
}

interface FieldErrors {
  email?: string;
}

function ForgotPasswordForm({ onSent }: { onSent: () => void }) {
  const [email, setEmail] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [bannerError, setBannerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!email.trim()) {
      setFieldErrors({ email: "Email is required" });
      emailRef.current?.focus();
      return;
    }

    setFieldErrors({});
    setBannerError(null);
    setSubmitting(true);

    try {
      // The backend always answers 202 here, whether or not the address
      // has an account — the confirmation screen must never say otherwise.
      await post<void>("/api/auth/forgot", { email: email.trim() });
      onSent();
    } catch (err) {
      setSubmitting(false);

      if (err instanceof ApiError && err.status === 429) {
        setBannerError(
          "You've reached the limit for password reset requests. Please try again in about an hour.",
        );
        return;
      }

      if (
        err instanceof ApiError &&
        err.status === 400 &&
        err.code === "VALIDATION_FAILED" &&
        err.fields?.email
      ) {
        setFieldErrors({ email: err.fields.email });
        return;
      }

      setBannerError("Something went wrong on our end. Please try again.");
    }
  }

  return (
    <>
      <h1
        id="forgot-heading"
        className="text-center font-display text-[27px] font-[560] leading-[1.15] tracking-[-0.015em] text-primary"
      >
        Reset your <em className="font-[480] italic text-moss">password.</em>
      </h1>
      <p className="mt-2 text-center text-sm text-muted-foreground">
        We&rsquo;ll email you a single-use reset link. It works for 30 minutes.
      </p>

      <form onSubmit={handleSubmit} noValidate aria-labelledby="forgot-heading" className="mt-6">
        <FieldWrap error={fieldErrors.email}>
          <label htmlFor="forgot-email" className="field-label">
            Email
          </label>
          <input
            ref={emailRef}
            id="forgot-email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setFieldErrors((f) => ({ ...f, email: undefined }));
            }}
            aria-invalid={!!fieldErrors.email}
            aria-describedby={fieldErrors.email ? "forgot-email-error" : undefined}
            className={fieldCls(!!fieldErrors.email)}
          />
          <FieldError id="forgot-email-error" msg={fieldErrors.email} />
        </FieldWrap>

        {bannerError && (
          <p role="alert" className="mt-4 text-sm font-semibold text-destructive">
            {bannerError}
          </p>
        )}

        <Button
          type="submit"
          variant="accent"
          size="lg"
          className="mt-6 w-full"
          disabled={submitting}
          aria-busy={submitting}
        >
          {submitting ? (
            <>
              Sending <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            </>
          ) : (
            "Email me the link"
          )}
        </Button>
      </form>
    </>
  );
}

/* -------------------------------------------------------------------------- */
/* Confirmation — deliberately identical regardless of whether the address    */
/* has an account (no enumeration).                                          */
/* -------------------------------------------------------------------------- */

function SentCard() {
  return (
    <div role="status" aria-live="polite" className="text-center">
      <span
        aria-hidden="true"
        className="mx-auto grid size-16 place-items-center rounded-[50%_50%_50%_18%] bg-sage/25"
      >
        <Mail className="size-7 text-moss" aria-hidden="true" />
      </span>
      <h1 className="mt-4 font-display text-[22px] font-[560] leading-[1.2] tracking-[-0.01em] text-primary">
        Check your inbox.
      </h1>
      <p className="mx-auto mt-2 max-w-[34ch] text-sm text-muted-foreground">
        If an account exists for that email, we&rsquo;ve sent a link to reset your password. It
        works for 30 minutes.
      </p>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Field helpers — mirrors the convention in routes/signin.tsx                */
/* -------------------------------------------------------------------------- */

function fieldCls(invalid: boolean) {
  return cn(
    "w-full rounded-2xl border-[1.5px] bg-background px-4 py-3 text-[15px] text-foreground outline-none transition-all duration-200",
    "placeholder:text-muted-foreground/60",
    "focus:border-moss focus:bg-white focus:shadow-[0_0_0_4px_rgba(92,125,112,0.15)]",
    invalid && "border-destructive bg-destructive/5 focus:border-destructive",
    !invalid && "border-transparent",
  );
}

function FieldWrap({ children, error }: { children: React.ReactNode; error?: string }) {
  return <div className={cn("space-y-1.5", error && "has-error")}>{children}</div>;
}

function FieldError({ id, msg }: { id: string; msg?: string }) {
  if (!msg) return null;
  return (
    <p id={id} role="alert" className="text-[12.5px] font-semibold text-destructive">
      {msg}
    </p>
  );
}
