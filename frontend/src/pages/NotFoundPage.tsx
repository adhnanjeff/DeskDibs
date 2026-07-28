import { Link } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCompass } from '@fortawesome/free-solid-svg-icons';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <FontAwesomeIcon
        icon={faCompass}
        className="h-10 w-10 text-ink-soft"
        aria-hidden="true"
      />
      <div>
        <p className="text-lg font-semibold text-ink">
          There's no seat here
        </p>
        <p className="mt-1 text-sm text-ink-soft">
          The page you're looking for doesn't exist.
        </p>
      </div>
      <Link
        to="/"
        className="ui-card-sm ui-control ui-label inline-flex items-center border-action bg-action px-4 py-2 text-sm font-bold text-white"
      >
        Back to the seat map
      </Link>
    </div>
  );
}
