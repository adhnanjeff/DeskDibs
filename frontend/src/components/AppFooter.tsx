import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faChair } from '@fortawesome/free-solid-svg-icons';

/**
 * Bauhaus footer: solid ink, a red top-accent hairline, and only true
 * information — a wordmark, the copyright line, and what the app is. No
 * decorative or dead links.
 */
export function AppFooter() {
  const year = new Date().getFullYear();

  return (
    <footer className="ui-accent-bar border-action bg-ink text-paper">
      <div className="mx-auto flex max-w-[var(--dd-shell-max)] flex-col gap-2 px-4 py-5 text-xs sm:flex-row sm:items-center sm:justify-between">
        <span className="ui-wordmark flex items-center gap-2">
          <FontAwesomeIcon
            icon={faChair}
            className="h-3.5 w-3.5 text-selected"
            aria-hidden="true"
          />
          DeskDibs
        </span>
        <p className="text-paper/60">
          Hot-desk booking for the days you come in.
        </p>
        <p className="font-mono ui-label text-paper/50">
          © {year} DeskDibs
        </p>
      </div>
    </footer>
  );
}
