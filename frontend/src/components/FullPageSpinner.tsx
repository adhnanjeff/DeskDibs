import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

export function FullPageSpinner() {
  return (
    <div
      className="flex min-h-[50vh] w-full items-center justify-center"
      role="status"
    >
      <FontAwesomeIcon
        icon={faCircleNotch}
        className="h-6 w-6 animate-spin text-action motion-reduce:animate-none"
        aria-hidden="true"
      />
      <span className="sr-only">Loading…</span>
    </div>
  );
}
