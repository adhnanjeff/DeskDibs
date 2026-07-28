import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { motion } from 'motion/react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChair,
  faCircleNotch,
  faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { useAuth } from '../auth/AuthContext';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface LocationState {
  from?: { pathname: string };
}

export function LoginPage() {
  const { login, status } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
  }>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (status === 'authenticated') {
    const redirectTo =
      (location.state as LocationState | null)?.from?.pathname ?? '/';
    return <Navigate to={redirectTo} replace />;
  }

  function validate(): boolean {
    const errors: { email?: string; password?: string } = {};
    if (!email.trim()) {
      errors.email = 'Enter your email address.';
    } else if (!EMAIL_PATTERN.test(email)) {
      errors.email = 'Enter a valid email address.';
    }
    if (!password) {
      errors.password = 'Enter your password.';
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    if (!validate()) return;

    setIsSubmitting(true);
    try {
      await login(email, password);
    } catch (error) {
      setSubmitError(
        error instanceof Error ? error.message : 'Could not sign in.',
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-canvas px-4">
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="w-full max-w-sm"
      >
        <div className="mb-8 flex flex-col items-center gap-2 text-center">
          <span className="flex h-12 w-12 items-center justify-center bg-selected text-ink">
            <FontAwesomeIcon
              icon={faChair}
              className="h-6 w-6"
              aria-hidden="true"
            />
          </span>
          <h1 className="ui-wordmark text-lg font-bold text-ink">
            DeskDibs
          </h1>
          <p className="text-sm text-ink-soft">
            Sign in to claim today's seat.
          </p>
        </div>

        <form
          onSubmit={(event) => void handleSubmit(event)}
          noValidate
          className="ui-card flex flex-col gap-4 border-ink bg-paper p-6"
        >
          {submitError && (
            <div
              role="alert"
              className="ui-edge flex items-start gap-2 border-danger bg-danger-tint p-3 text-sm font-semibold text-danger"
            >
              <FontAwesomeIcon
                icon={faTriangleExclamation}
                className="mt-0.5 h-4 w-4 shrink-0"
                aria-hidden="true"
              />
              <span>{submitError}</span>
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="email"
              className="ui-label text-sm font-semibold text-ink"
            >
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              className="ui-edge ui-control border-ink bg-white px-3 py-2 text-sm text-ink placeholder:text-ink-soft"
              placeholder="you@deskdibs.local"
            />
            {fieldErrors.email && (
              <p id="email-error" className="text-xs font-semibold text-danger">
                {fieldErrors.email}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="password"
              className="ui-label text-sm font-semibold text-ink"
            >
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={
                fieldErrors.password ? 'password-error' : undefined
              }
              className="ui-edge ui-control border-ink bg-white px-3 py-2 text-sm text-ink"
            />
            {fieldErrors.password && (
              <p id="password-error" className="text-xs font-semibold text-danger">
                {fieldErrors.password}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="ui-card-sm ui-control ui-label mt-2 flex items-center justify-center gap-2 border-action bg-action px-4 py-2.5 text-sm font-bold text-white transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
          >
            {isSubmitting && (
              <FontAwesomeIcon
                icon={faCircleNotch}
                className="h-4 w-4 animate-spin motion-reduce:animate-none"
                aria-hidden="true"
              />
            )}
            Sign in
          </button>
        </form>
      </motion.div>
    </div>
  );
}
