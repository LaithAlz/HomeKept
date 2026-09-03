import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator } from "@tanstack/zod-adapter";
import { z } from "zod";
import { useRef, useState } from "react";
import { ArrowRight, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AuthPageShell } from "@/components/auth/AuthPageShell";
import { fieldCls, FieldWrap, FieldError } from "@/components/forms/Field";
import { cn } from "@/lib/utils";
import { ApiError, post } from "@/lib/api";
import { getSession, homeFor } from "@/lib/auth";

const searchSchema = z.object({
  next: z.string().optional(),
});

export const Route = createFileRoute("/signin")({
  validateSearch: zodValidator(searchSchema),
  head: () => ({
    meta: [
      { title: "Sign in: HomeKept" },
      {
        name: "description",
        content: "Sign in to your HomeKept account to see your visits, reports, and list.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
  component: SignInPage,
});

const DEFAULT_REDIRECT = "/app";

// A `next` that points back into the auth flow itself is never a useful
// destination: it either bounces the visitor right back to sign-in (the
// `/signin?next=/signin` loop a guard redirect can produce mid-transition,
// see AppShell/AdminShell/TechGuard) or drops them on a page that expects a
// one-time token, not a signed-in session. Treated the same as "no next" so
// the caller falls through to the role-based landing.
const AUTH_FLOW_PATHS = new Set(["/signin", "/forgot-password", "/reset-password", "/activate"]);

/**
 * A "next" destination is only honoured when the WHATWG URL parser resolves
 * it to the current origin. A prefix check like `startsWith("/")` is not
 * sufficient: the URL parser strips ASCII tab/LF/CR from anywhere in the
 * input before parsing, so a string a naive check accepts (e.g.
 * `"/\t/evil.com"`, which "starts with a single /") still resolves to an
 * off-site origin once parsed. Parsing with `URL` and comparing origins
 * closes that gap, and also rejects absolute URLs (`https://evil.com`) and
 * non-http(s) schemes (`javascript:...`), whose origin can never match.
 * A same-origin `next` that lands back in the auth flow itself
 * (`AUTH_FLOW_PATHS`) is rejected the same way.
 *
 * Deliberately called client-side only (from the submit handler, after a
 * user gesture) rather than at render time: this component is
 * server-rendered too, and `window` does not exist during SSR.
 */
function sanitizeNext(raw: string | undefined): string {
  if (!raw) return DEFAULT_REDIRECT;
  try {
    const url = new URL(raw, window.location.origin);
    if (url.origin !== window.location.origin) return DEFAULT_REDIRECT;
    if (AUTH_FLOW_PATHS.has(url.pathname)) return DEFAULT_REDIRECT;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return DEFAULT_REDIRECT;
  }
}

interface FieldErrors {
  email?: string;
  password?: string;
}

function SignInPage() {
  const { next } = Route.useSearch();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [shaking, setShaking] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  function shake() {
    setShaking(false);
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        setShaking(true);
        setTimeout(() => setShaking(false), 500);
      }),
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();

    const errs: FieldErrors = {};
    if (!email.trim()) errs.email = "Email is required";
    if (!password) errs.password = "Password is required";
    if (Object.keys(errs).length) {
      setFieldErrors(errs);
      shake();
      return;
    }

    setFieldErrors({});
    setFormError(null);
    setSubmitting(true);

    try {
      await post<void>("/api/auth/login", { email: email.trim(), password });
      const target = sanitizeNext(next);
      // No explicit (or valid) `next` was given: send the freshly-signed-in
      // user to the shell for their role, not always the customer app — an
      // admin or technician landing on `/app` has no subscriber row there
      // and every card fails.
      const destination =
        target === DEFAULT_REDIRECT
          ? await getSession()
              .then((session) => (session ? homeFor(session.role) : DEFAULT_REDIRECT))
              .catch(() => DEFAULT_REDIRECT)
          : target;
      // Full navigation: the redirect target may be an arbitrary same-origin
      // path (see sanitizeNext), and a fresh load is the simplest way to let
      // the app layout's session guard pick up the cookie the login response
      // just set.
      window.location.assign(destination);
    } catch (err) {
      setSubmitting(false);

      if (
        err instanceof ApiError &&
        err.status === 400 &&
        err.code === "VALIDATION_FAILED" &&
        err.fields
      ) {
        const mapped: FieldErrors = {};
        if (err.fields.email) mapped.email = err.fields.email;
        if (err.fields.password) mapped.password = err.fields.password;
        setFieldErrors(mapped);
        if (!mapped.email && !mapped.password) setFormError(err.message);
        shake();
        return;
      }

      if (err instanceof ApiError) {
        // Covers 401 INVALID_CREDENTIALS ("Invalid email or password" — the
        // same message whether the email exists or not) and 429 RATE_LIMITED.
        setFormError(err.message);
        shake();
        return;
      }

      setFormError("Something went wrong. Please try again.");
      shake();
    }
  }

  return (
    <AuthPageShell outer="flex" glow="compact" maxWidthClassName="max-w-[420px]">
      <div
        ref={cardRef}
        className={cn(
          "rounded-[30px] border border-border bg-card p-8 shadow-[0_28px_56px_-28px_rgba(9,45,33,0.35)]",
          shaking && "animate-shake",
        )}
      >
        <h1
          id="signin-heading"
          className="text-center font-display text-[27px] font-[560] leading-[1.15] tracking-[-0.015em] text-primary"
        >
          Welcome <em className="font-[480] italic text-moss">home.</em>
        </h1>
        <p className="mt-2 text-center text-sm text-muted-foreground">
          Your visits, reports, and list live here.
        </p>

        <form onSubmit={onSubmit} noValidate aria-labelledby="signin-heading" className="mt-6">
          <FieldWrap error={fieldErrors.email}>
            <label htmlFor="signin-email" className="field-label">
              Email
            </label>
            <input
              id="signin-email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setFieldErrors((f) => ({ ...f, email: undefined }));
              }}
              aria-invalid={!!fieldErrors.email}
              aria-describedby={fieldErrors.email ? "signin-email-error" : undefined}
              className={fieldCls(!!fieldErrors.email)}
            />
            <FieldError id="signin-email-error" msg={fieldErrors.email} />
          </FieldWrap>

          <div className="mt-4">
            <FieldWrap error={fieldErrors.password}>
              <label htmlFor="signin-password" className="field-label">
                Password
              </label>
              <input
                id="signin-password"
                type="password"
                autoComplete="current-password"
                placeholder="Your password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setFieldErrors((f) => ({ ...f, password: undefined }));
                }}
                aria-invalid={!!fieldErrors.password}
                aria-describedby={fieldErrors.password ? "signin-password-error" : undefined}
                className={fieldCls(!!fieldErrors.password)}
              />
              <FieldError id="signin-password-error" msg={fieldErrors.password} />
            </FieldWrap>
          </div>

          <div className="mt-2.5 flex justify-end">
            <Link
              to="/forgot-password"
              className="text-[13px] font-semibold text-muted-foreground hover:text-primary"
            >
              Forgot your password?
            </Link>
          </div>

          {formError && (
            <p role="alert" className="mt-4 text-sm font-semibold text-destructive">
              {formError}
            </p>
          )}

          <Button type="submit" size="lg" className="mt-6 w-full" disabled={submitting}>
            {submitting ? (
              <>
                Signing in <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              </>
            ) : (
              <>
                Sign in <ArrowRight className="size-4" aria-hidden="true" />
              </>
            )}
          </Button>
        </form>
      </div>

      <div className="mt-6 space-y-1.5 text-center text-sm text-muted-foreground">
        <p>
          New to HomeKept?{" "}
          <Link
            to="/book"
            className="font-semibold text-primary underline decoration-accent decoration-2 underline-offset-4 hover:decoration-primary"
          >
            Book a free walk-through
          </Link>{" "}
          to get started.
        </p>
        <p>Already got an activation email? Use the link in it, no password needed yet.</p>
      </div>
    </AuthPageShell>
  );
}
