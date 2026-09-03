import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { AuthPageShell } from "@/components/auth/AuthPageShell";
import { Button } from "@/components/ui/button";
import { fieldCls, FieldWrap, FieldError } from "@/components/forms/Field";
import { ApiError, post } from "@/lib/api";

const searchSchema = z.object({
  token: z.string().optional(),
});

export const Route = createFileRoute("/reset-password")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Reset your password: HomeKept" },
      {
        name: "description",
        content: "Choose a new password for your HomeKept account.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: ResetPasswordPage,
});

/* -------------------------------------------------------------------------- */
/* Page shell — matches mockups/v2/signin.html's card treatment: a single     */
/* centered card, no site nav, ambient glows.                                 */
/* -------------------------------------------------------------------------- */

function ResetPasswordPage() {
  const { token } = Route.useSearch();

  return (
    <AuthPageShell outer="grid" glow="wide" maxWidthClassName="max-w-[420px]">
      <ResetPasswordFlow token={token} />
    </AuthPageShell>
  );
}

/* -------------------------------------------------------------------------- */
/* Flow controller — no upfront token validation call exists for reset       */
/* (unlike /activate); the token is only ever checked by POST /api/auth/reset */
/* itself, so an absent token is treated as a dead end immediately and any    */
/* other failure surfaces once the customer submits the form.                */
/* -------------------------------------------------------------------------- */

function ResetPasswordFlow({ token }: { token: string | undefined }) {
  const [deadEnd, setDeadEnd] = useState(!token);

  if (deadEnd || !token) {
    return <DeadEndCard />;
  }

  return <ResetPasswordForm token={token} onDeadEnd={() => setDeadEnd(true)} />;
}

/* -------------------------------------------------------------------------- */
/* Happy path — new-password + confirm-password form                          */
/* -------------------------------------------------------------------------- */

interface FieldErrors {
  password?: string;
  confirmPassword?: string;
}

function ResetPasswordForm({ token, onDeadEnd }: { token: string; onDeadEnd: () => void }) {
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [bannerError, setBannerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    passwordRef.current?.focus();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const errs: FieldErrors = {};
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
      // The token is only ever held in this component's state and sent in
      // the request body — never written to localStorage, never logged,
      // never reflected into an attribute or href.
      await post<void>("/api/auth/reset", { token, password });
      // The backend has already set fresh auth cookies on this response —
      // the customer is signed in. A full-page navigation (rather than the
      // router's client-side navigate) is intentional: it's the simplest
      // way to let /app's session guard pick up the cookie this response
      // just set (see AppShell and signin.tsx's sanitizeNext comment).
      window.location.assign("/app");
    } catch (err) {
      setSubmitting(false);

      if (err instanceof ApiError && err.status === 429) {
        setBannerError(
          "You've reached the limit for password reset attempts. Please try again in about an hour.",
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
        onDeadEnd();
        return;
      }

      setBannerError("Something went wrong on our end. Please try again.");
    }
  }

  return (
    <div className="rounded-[30px] border border-border bg-card p-8 shadow-[0_28px_56px_-28px_rgba(9,45,33,0.35)] sm:p-9">
      <h1
        id="reset-heading"
        className="text-center font-display text-[27px] font-[560] leading-[1.15] tracking-[-0.015em] text-primary"
      >
        Choose a new <em className="font-[480] italic text-moss">password.</em>
      </h1>
      <p className="mt-2 text-center text-sm text-muted-foreground">
        This replaces your current password.
      </p>

      <form onSubmit={handleSubmit} noValidate aria-labelledby="reset-heading" className="mt-6">
        <FieldWrap error={errors.password}>
          <label htmlFor="reset-password" className="field-label">
            New password
          </label>
          <input
            ref={passwordRef}
            id="reset-password"
            type="password"
            autoComplete="new-password"
            placeholder="At least 8 characters"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              if (errors.password) setErrors((prev) => ({ ...prev, password: undefined }));
            }}
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? "reset-password-error" : "reset-password-hint"}
            className={fieldCls(!!errors.password)}
          />
          {errors.password ? (
            <FieldError id="reset-password-error" msg={errors.password} />
          ) : (
            <p id="reset-password-hint" className="text-[12px] text-muted-foreground">
              At least 8 characters.
            </p>
          )}
        </FieldWrap>

        <div className="mt-5">
          <FieldWrap error={errors.confirmPassword}>
            <label htmlFor="reset-confirm" className="field-label">
              Confirm new password
            </label>
            <input
              id="reset-confirm"
              type="password"
              autoComplete="new-password"
              placeholder="Re-enter your new password"
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value);
                if (errors.confirmPassword)
                  setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
              }}
              aria-invalid={!!errors.confirmPassword}
              aria-describedby={errors.confirmPassword ? "reset-confirm-error" : undefined}
              className={fieldCls(!!errors.confirmPassword)}
            />
            <FieldError id="reset-confirm-error" msg={errors.confirmPassword} />
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
          {submitting ? (
            <>
              Resetting <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            </>
          ) : (
            "Reset my password"
          )}
        </Button>
      </form>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Dead end — no auto-redirect, calm exit back to requesting a fresh link     */
/* -------------------------------------------------------------------------- */

function DeadEndCard() {
  return (
    <div className="rounded-[30px] border border-border bg-card p-8 text-center shadow-[0_28px_56px_-28px_rgba(9,45,33,0.35)] sm:p-9">
      <h1 className="font-display text-[24px] font-[560] leading-[1.2] tracking-[-0.01em] text-primary">
        This reset link has expired or was already used.
      </h1>
      <p className="mx-auto mt-3 max-w-[38ch] text-[14px] text-muted-foreground">
        Request a new one and we&rsquo;ll email you a fresh link.
      </p>

      <div className="mt-7 flex flex-col items-center gap-3">
        <Button asChild variant="accent" size="lg" className="w-full">
          <Link to="/forgot-password">Request a new link</Link>
        </Button>
        <Link
          to="/signin"
          className="text-[13px] font-semibold text-primary underline decoration-honey underline-offset-4"
        >
          Back to sign in
        </Link>
      </div>
    </div>
  );
}
