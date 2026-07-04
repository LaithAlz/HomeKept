import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Wordmark } from "@/components/brand/Wordmark";
import { Button } from "@/components/ui/button";
import { ApiError, post } from "@/lib/api";
import { cn } from "@/lib/utils";
import { canonicalUrl } from "@/lib/seo";

const searchSchema = z.object({
  token: z.string().optional(),
});

export const Route = createFileRoute("/activate")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Activate your account: HomeKept" },
      {
        name: "description",
        content: "Set a password to activate your HomeKept account.",
      },
      { name: "robots", content: "noindex" },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/activate") }],
  }),
  component: ActivatePage,
});

/* -------------------------------------------------------------------------- */
/* Contract-accurate shapes — mirrors POST /api/activation/validate &         */
/* /api/activation/complete. See backend/api-contract.md.                     */
/* -------------------------------------------------------------------------- */

type ValidateReason = "EXPIRED" | "USED" | "INVALID";

interface ActivationValidateResponse {
  valid: boolean;
  bookingId?: number;
  firstName?: string;
  reason?: string;
}

interface ActivationCompleteResponse {
  userId: number;
  next: string;
}

function isKnownReason(reason: string | undefined): reason is ValidateReason {
  return reason === "EXPIRED" || reason === "USED" || reason === "INVALID";
}

/**
 * Screen state for the activation flow. "dead-end" covers the three reasons the
 * backend can report from /validate; "stale" is a synthetic case for when the
 * token becomes invalid *after* validation succeeded (race between the two
 * requests) — the complete endpoint reports this as a generic INVALID_TOKEN
 * error with no reason breakdown.
 */
type ScreenState =
  | { kind: "checking" }
  | { kind: "valid"; firstName: string }
  | { kind: "dead-end"; reason: ValidateReason }
  | { kind: "stale" }
  | { kind: "rate-limited" }
  | { kind: "error" };

/* -------------------------------------------------------------------------- */
/* Page shell — matches mockups/v2/activate.html: a single centered card,     */
/* no site nav, ambient glows.                                                 */
/* -------------------------------------------------------------------------- */

function ActivatePage() {
  const { token } = Route.useSearch();
  return (
    <div className="grid min-h-dvh place-items-center overflow-x-clip bg-background px-4 py-10">
      {/* Ambient glows — decorative */}
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute -right-28 -top-36 size-[480px] animate-drift rounded-full bg-sage/35 blur-[90px]" />
        <div className="absolute -left-40 bottom-[-130px] size-[420px] animate-drift rounded-full bg-honey-soft/45 blur-[90px] [animation-direction:alternate-reverse]" />
      </div>

      <main id="main" className="relative z-10 w-full max-w-[460px] animate-reveal">
        <Link to="/" className="mb-6 flex items-center justify-center" aria-label="HomeKept home">
          <Wordmark size="md" />
        </Link>

        <ActivateFlow token={token} />
      </main>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Flow controller — validates the token on mount, then renders the           */
/* matching screen.                                                            */
/* -------------------------------------------------------------------------- */

function ActivateFlow({ token }: { token: string | undefined }) {
  const [state, setState] = useState<ScreenState>({ kind: "checking" });

  useEffect(() => {
    if (!token) {
      setState({ kind: "dead-end", reason: "INVALID" });
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const res = await post<ActivationValidateResponse>("/api/activation/validate", { token });
        if (cancelled) return;
        if (res.valid && res.firstName) {
          setState({ kind: "valid", firstName: res.firstName });
        } else {
          setState({
            kind: "dead-end",
            reason: isKnownReason(res.reason) ? res.reason : "INVALID",
          });
        }
      } catch (err) {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 429) {
          setState({ kind: "rate-limited" });
        } else {
          setState({ kind: "error" });
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token]);

  if (state.kind === "checking") {
    return (
      <Card>
        <div
          role="status"
          aria-live="polite"
          className="flex flex-col items-center gap-3 py-6 text-center"
        >
          <Loader2 className="size-6 animate-spin text-moss" aria-hidden="true" />
          <p className="text-[14px] text-muted-foreground">Checking your invite&hellip;</p>
        </div>
      </Card>
    );
  }

  if (state.kind === "valid") {
    return <ActivationCard token={token as string} firstName={state.firstName} />;
  }

  if (state.kind === "dead-end") {
    return <DeadEndCard reason={state.reason} />;
  }

  if (state.kind === "stale") {
    return <StaleCard />;
  }

  if (state.kind === "rate-limited") {
    return (
      <MessageCard
        heading="Too many attempts."
        body="You've checked this link too many times. Please try again in about an hour."
      />
    );
  }

  return (
    <MessageCard
      heading="Something went wrong."
      body="We couldn't check your link just now. Please try again in a moment, or contact us if this keeps happening."
      showContact
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Happy path — welcome + set-password form                                   */
/* -------------------------------------------------------------------------- */

function ActivationCard({ token, firstName }: { token: string; firstName: string }) {
  const [stale, setStale] = useState(false);

  if (stale) return <StaleCard />;

  return (
    <Card>
      <StepIndicator />

      <h1 className="text-center font-display text-[28px] font-[560] leading-[1.15] tracking-[-0.015em] text-primary">
        Welcome, <em className="font-[480] italic text-moss">{firstName}</em>.
      </h1>
      <p className="mt-2 text-center text-[14px] text-muted-foreground">
        Set a password to activate your account. Next, you&rsquo;ll choose your plan.
      </p>

      <PasswordForm token={token} onStale={() => setStale(true)} />
    </Card>
  );
}

function PasswordForm({ token, onStale }: { token: string; onStale: () => void }) {
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<{ password?: string; confirmPassword?: string }>({});
  const [submitting, setSubmitting] = useState(false);
  const [bannerError, setBannerError] = useState<string | null>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    passwordRef.current?.focus();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const errs: typeof errors = {};
    if (password.length < 8) errs.password = "Password must be at least 8 characters.";
    if (password !== confirmPassword) errs.confirmPassword = "Passwords don't match.";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    setErrors({});
    setBannerError(null);
    setSubmitting(true);

    try {
      const res = await post<ActivationCompleteResponse>("/api/activation/complete", {
        token,
        password,
      });
      // The backend has already set auth cookies on this response — the
      // customer is signed in. Full-page navigation is intentional: /plans
      // is where they pick a tier and pay, wired separately. `next` is
      // documented as always "CHECKOUT" today; /plans is the only known
      // destination either way.
      void res.next;
      window.location.assign("/plans");
    } catch (err) {
      setSubmitting(false);

      if (err instanceof ApiError && err.status === 429) {
        setBannerError(
          "You've reached the limit for activation attempts. Please try again in about an hour.",
        );
        return;
      }

      if (
        err instanceof ApiError &&
        err.status === 400 &&
        err.code === "VALIDATION_FAILED" &&
        err.fields?.password
      ) {
        setErrors({ password: err.fields.password });
        return;
      }

      if (err instanceof ApiError && err.status === 400 && err.code === "INVALID_REQUEST") {
        setErrors({ password: err.message });
        return;
      }

      if (err instanceof ApiError && err.status === 400 && err.code === "INVALID_TOKEN") {
        // The token expired or was used while this form was open.
        onStale();
        return;
      }

      setBannerError("Something went wrong on our end. Please try again.");
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-6">
      <FieldWrap error={errors.password}>
        <label htmlFor="f-password" className="field-label">
          Password
        </label>
        <input
          ref={passwordRef}
          id="f-password"
          type="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (errors.password) setErrors((prev) => ({ ...prev, password: undefined }));
          }}
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? "err-password" : "hint-password"}
          className={fieldCls(!!errors.password)}
        />
        {errors.password ? (
          <FieldError id="err-password" msg={errors.password} />
        ) : (
          <p id="hint-password" className="text-[12px] text-muted-foreground">
            At least 8 characters.
          </p>
        )}
      </FieldWrap>

      <div className="mt-5">
        <FieldWrap error={errors.confirmPassword}>
          <label htmlFor="f-confirm" className="field-label">
            Confirm password
          </label>
          <input
            id="f-confirm"
            type="password"
            autoComplete="new-password"
            placeholder="Re-enter your password"
            value={confirmPassword}
            onChange={(e) => {
              setConfirmPassword(e.target.value);
              if (errors.confirmPassword)
                setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
            }}
            aria-invalid={!!errors.confirmPassword}
            aria-describedby={errors.confirmPassword ? "err-confirm" : undefined}
            className={fieldCls(!!errors.confirmPassword)}
          />
          <FieldError id="err-confirm" msg={errors.confirmPassword} />
        </FieldWrap>
      </div>

      {bannerError && (
        <p role="alert" className="mt-4 text-[13px] font-semibold text-destructive">
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
        {submitting ? "Activating…" : "Activate my account"}
      </Button>
    </form>
  );
}

/* -------------------------------------------------------------------------- */
/* Step indicator — matches the mockup's three-step wayfinding                */
/* -------------------------------------------------------------------------- */

function StepIndicator() {
  const steps = [
    { label: "Walk-through", state: "done" as const },
    { label: "Your account", state: "on" as const },
    { label: "Checkout", state: "upcoming" as const },
  ];

  return (
    <ol
      aria-label="Activation progress"
      className="mb-5 flex items-center justify-center gap-2 text-[11.5px] font-bold text-muted-foreground"
    >
      {steps.map((s, i) => (
        <li key={s.label} className="flex items-center gap-2">
          <span className="flex items-center gap-1.5">
            <span
              aria-hidden="true"
              className={cn(
                "grid size-6 place-items-center rounded-[50%_50%_50%_20%] text-[11px]",
                s.state === "done" && "bg-moss text-white",
                s.state === "on" && "bg-primary text-primary-foreground",
                s.state === "upcoming" && "bg-surface text-muted-foreground",
              )}
            >
              {s.state === "done" ? "✓" : i + 1}
            </span>
            <span className={cn(s.state === "on" && "text-primary")}>{s.label}</span>
          </span>
          {i < steps.length - 1 && (
            <span
              aria-hidden="true"
              className={cn(
                "h-[2px] w-[22px] rounded-full",
                s.state === "done" ? "bg-moss" : "bg-surface",
              )}
            />
          )}
        </li>
      ))}
    </ol>
  );
}

/* -------------------------------------------------------------------------- */
/* Dead-end screens — calm, no auto-redirects                                 */
/* -------------------------------------------------------------------------- */

function DeadEndCard({ reason }: { reason: ValidateReason }) {
  if (reason === "EXPIRED") {
    return (
      <MessageCard
        heading="This link has expired."
        body="Activation links are valid for 7 days. Contact us and we'll send a fresh one."
        showContact
      />
    );
  }

  if (reason === "USED") {
    return (
      <MessageCard
        heading="This link was already used."
        body="It looks like this account is already active."
        action={<SecondaryLink href="/signin" label="Sign in" />}
      />
    );
  }

  return (
    <MessageCard
      heading="This isn't a valid activation link."
      body="Double-check the link from your email, or contact us if you need a hand."
      showContact
    />
  );
}

function StaleCard() {
  return (
    <MessageCard
      heading="This link changed."
      body="It looks like this activation link expired or was already used while you were filling this in. Ask us for a fresh link, or sign in if you've already activated."
      showContact
      action={<SecondaryLink href="/signin" label="Sign in" />}
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Shared presentational bits                                                 */
/* -------------------------------------------------------------------------- */

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-[30px] border border-border bg-card p-8 shadow-[0_28px_56px_-28px_rgba(30,58,43,0.35)] sm:p-9">
      {children}
    </div>
  );
}

function MessageCard({
  heading,
  body,
  showContact,
  action,
}: {
  heading: string;
  body: string;
  showContact?: boolean;
  action?: React.ReactNode;
}) {
  return (
    <Card>
      <div className="text-center">
        <h1 className="font-display text-[24px] font-[560] leading-[1.2] tracking-[-0.01em] text-primary">
          {heading}
        </h1>
        <p className="mx-auto mt-3 max-w-[38ch] text-[14px] text-muted-foreground">{body}</p>

        <div className="mt-7 flex flex-col items-center gap-3">
          {showContact && (
            <Button asChild variant="accent" size="lg" className="w-full">
              <a href="mailto:hello@homekept.ca">Email us</a>
            </Button>
          )}
          {action}
          <Link
            to="/"
            className="text-[13px] font-semibold text-primary underline decoration-honey underline-offset-4"
          >
            Back to home
          </Link>
        </div>
      </div>
    </Card>
  );
}

function SecondaryLink({ href, label }: { href: string; label: string }) {
  return (
    <Button asChild variant="outline" size="lg" className="w-full">
      {/* /signin doesn't exist as a router-registered page yet, so this is a
          plain anchor rather than the typed <Link> (matches the fallback used
          in __root.tsx's ErrorComponent). */}
      <a href={href}>{label}</a>
    </Button>
  );
}

/* -------------------------------------------------------------------------- */
/* Field helpers — mirrors the convention in routes/book.tsx                  */
/* -------------------------------------------------------------------------- */

function fieldCls(invalid: boolean) {
  return cn(
    "w-full rounded-2xl border-[1.5px] bg-background px-4 py-3 text-[15px] text-foreground outline-none transition-all duration-200",
    "placeholder:text-muted-foreground/60",
    "focus:border-moss focus:bg-white focus:shadow-[0_0_0_4px_rgba(94,125,98,0.15)]",
    invalid && "border-destructive bg-[#FDF6F3] focus:border-destructive",
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
