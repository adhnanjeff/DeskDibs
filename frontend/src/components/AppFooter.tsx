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
    <footer className="border-t-4 border-bauhaus-red bg-ink text-paper">
      <div className="mx-auto flex max-w-[1500px] flex-col gap-2 px-4 py-5 text-xs sm:flex-row sm:items-center sm:justify-between">
        <span className="flex items-center gap-2 font-mono uppercase tracking-[0.14em]">
          <FontAwesomeIcon
            icon={faChair}
            className="h-3.5 w-3.5 text-bauhaus-yellow"
            aria-hidden="true"
          />
          DeskDibs
        </span>
        <p className="text-paper/60">
          Hot-desk booking for the days you come in.
        </p>
        <p className="font-mono uppercase tracking-wider text-paper/50">
          © {year} DeskDibs
        </p>
      </div>
    </footer>
  );
}
