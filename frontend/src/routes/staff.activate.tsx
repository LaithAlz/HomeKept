import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { AuthPageShell } from "@/components/auth/AuthPageShell";
import { Button } from "@/components/ui/button";
import { fieldCls, FieldWrap, FieldError } from "@/components/forms/Field";
import { ApiError, post } from "@/lib/api";
import { homeFor } from "@/lib/auth";
import { canonicalUrl } from "@/lib/seo";

const searchSchema = z.object({
  token: z.string().optional(),
});

export const Route = createFileRoute("/staff/activate")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Activate your staff account: HomeKept" },
      {
        name: "description",
        content: "Set a password to activate your HomeKept staff account.",
      },
      { name: "robots", content: "noindex" },
    ],
    links: [{ rel: "canonical", href: canonicalUrl("/staff/activate") }],
  }),
  component: StaffActivatePage,
});

/* -------------------------------------------------------------------------- */
/* Contract-accurate shapes — mirrors POST /api/staff/invite/validate &       */
/* /api/staff/invite/accept. See backend/api-contract.md.                     */
/* -------------------------------------------------------------------------- */

type ValidateReason = "EXPIRED" | "USED" | "INVALID";

interface StaffInviteValidateResponse {
  valid: boolean;
  firstName?: string;
  reason?: string;
}

interface StaffInviteAcceptResponse {
  userId: number;
}

function isKnownReason(reason: string | undefined): reason is ValidateReason {
  return reason === "EXPIRED" || reason === "USED" || reason === "INVALID";
}

/**
 * Screen state for the staff-activation flow. "dead-end" covers the reasons the
 * backend can report from /validate; "stale" is a synthetic case for when the
 * token becomes invalid *after* validation succeeded (race between the two
 * requests) — the accept endpoint reports this as a generic INVALID_TOKEN error
 * with no reason breakdown.
 */
type ScreenState =
  | { kind: "checking" }
  | { kind: "valid"; firstName: string }
  | { kind: "dead-end"; reason: ValidateReason }
  | { kind: "stale" }
  | { kind: "rate-limited" }
  | { kind: "error" };

/* -------------------------------------------------------------------------- */
/* Page shell — mirrors routes/activate.tsx: a single centered card, no site  */
/* nav, ambient glows.                                                         */
/* -------------------------------------------------------------------------- */

function StaffActivatePage() {
  const { token } = Route.useSearch();
  return (
    <AuthPageShell outer="grid" glow="wide" maxWidthClassName="max-w-[460px]">
      <StaffActivateFlow token={token} />
    </AuthPageShell>
  );
}

/* -------------------------------------------------------------------------- */
/* Flow controller — validates the token on mount, then renders the           */
/* matching screen.                                                            */
/* -------------------------------------------------------------------------- */

function StaffActivateFlow({ token }: { token: string | undefined }) {
  const [state, setState] = useState<ScreenState>({ kind: "checking" });

  useEffect(() => {
    if (!token) {
      setState({ kind: "dead-end", reason: "INVALID" });
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const res = await post<StaffInviteValidateResponse>("/api/staff/invite/validate", {
          token,
        });
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
      body="We couldn't check your invite just now. Please try again in a moment, or ask your admin for a hand."
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
      <h1 className="text-center font-display text-[28px] font-[560] leading-[1.15] tracking-[-0.015em] text-primary">
        Welcome, <em className="font-[480] italic text-moss">{firstName}</em>.
      </h1>
      <p className="mt-2 text-center text-[14px] text-muted-foreground">
        Set a password to activate your HomeKept staff account.
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
      await post<StaffInviteAcceptResponse>("/api/staff/invite/accept", {
        token,
        password,
      });
      // The backend has already set auth cookies on this response — the technician
      // is signed in. The role is always TECHNICIAN for this flow, so their home is
      // always /tech; still routed through homeFor for a single source of truth on
      // role -> home mapping. Full-page navigation, same as the customer activation
      // flow, so a fresh session cookie is definitely picked up.
      window.location.assign(homeFor("TECHNICIAN"));
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
        // The invite expired or was used while this form was open.
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
        {submitting ? "Setting up…" : "Set my password"}
      </Button>
    </form>
  );
}

/* -------------------------------------------------------------------------- */
/* Dead-end screens — calm, no auto-redirects                                 */
/* -------------------------------------------------------------------------- */

function DeadEndCard({ reason }: { reason: ValidateReason }) {
  if (reason === "EXPIRED" || reason === "USED") {
    return (
      <MessageCard heading="This invite has expired." body="Ask your admin to send a new one." />
    );
  }

  return (
    <MessageCard
      heading="This isn't a valid invite link."
      body="Double-check the link from your email, or ask your admin to send a new one."
    />
  );
}

function StaleCard() {
  return (
    <MessageCard
      heading="This invite has expired."
      body="It looks like this invite expired or was already used while you were filling this in. Ask your admin to send a new one."
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Shared presentational bits                                                 */
/* -------------------------------------------------------------------------- */

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-[30px] border border-border bg-card p-8 shadow-[0_28px_56px_-28px_rgba(9,45,33,0.35)] sm:p-9">
      {children}
    </div>
  );
}

function MessageCard({ heading, body }: { heading: string; body: string }) {
  return (
    <Card>
      <div className="text-center">
        <h1 className="font-display text-[24px] font-[560] leading-[1.2] tracking-[-0.01em] text-primary">
          {heading}
        </h1>
        <p className="mx-auto mt-3 max-w-[38ch] text-[14px] text-muted-foreground">{body}</p>

        <div className="mt-7 flex flex-col items-center gap-3">
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
