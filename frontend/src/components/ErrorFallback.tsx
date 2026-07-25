import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';

interface ErrorFallbackProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

/**
 * The friendly fallback that stands between a render failure and a blank
 * page (CLAUDE.md: "Error boundaries around the seat map — a render
 * failure must never blank the page"). Always offers a way back in.
 */
export function ErrorFallback({
  title = 'Something went wrong',
  message = 'This part of the page failed to load. You can try again.',
  onRetry,
}: ErrorFallbackProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-8 text-center"
    >
      <FontAwesomeIcon
        icon={faTriangleExclamation}
        className="h-8 w-8 text-rose-500"
        aria-hidden="true"
      />
      <div>
        <p className="font-semibold text-slate-800">{title}</p>
        <p className="mt-1 text-sm text-slate-600">{message}</p>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand-700"
        >
          Try again
        </button>
      )}
    </div>
  );
}
