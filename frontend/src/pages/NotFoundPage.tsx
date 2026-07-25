import { Link } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCompass } from '@fortawesome/free-solid-svg-icons';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <FontAwesomeIcon
        icon={faCompass}
        className="h-10 w-10 text-slate-300"
        aria-hidden="true"
      />
      <div>
        <p className="text-lg font-semibold text-slate-800">
          There's no seat here
        </p>
        <p className="mt-1 text-sm text-slate-500">
          The page you're looking for doesn't exist.
        </p>
      </div>
      <Link
        to="/"
        className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand-700"
      >
        Back to the seat map
      </Link>
    </div>
  );
}
