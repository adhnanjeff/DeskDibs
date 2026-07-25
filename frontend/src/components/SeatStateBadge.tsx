import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { SEAT_STATE_META, type SeatDisplayState } from '../lib/seatState';

interface SeatStateBadgeProps {
  state: SeatDisplayState;
  /** Renders just the icon + colour, still with an accessible name. Use in
   * tight spaces (e.g. inside a seat glyph) where the text label appears
   * elsewhere (a legend, a tooltip). */
  compact?: boolean;
  className?: string;
}

/**
 * The one reusable unit for showing a seat's state anywhere in the app —
 * a legend entry today, a seat glyph's label in Phase 6 tomorrow. Colour,
 * icon, and text always travel together so the state reads correctly for
 * colourblind users and without relying on colour perception at all.
 */
export function SeatStateBadge({
  state,
  compact = false,
  className = '',
}: SeatStateBadgeProps) {
  const meta = SEAT_STATE_META[state];

  if (compact) {
    return (
      <span
        className={`inline-flex items-center justify-center rounded-full p-1.5 ${meta.tintClass} ${className}`}
        role="img"
        aria-label={meta.label}
        title={meta.label}
      >
        <FontAwesomeIcon
          icon={meta.icon}
          className={`h-3.5 w-3.5 ${meta.colorClass}`}
          aria-hidden="true"
        />
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full py-1 pl-1.5 pr-2.5 text-xs font-medium ${meta.tintClass} ${meta.textClass} ${className}`}
    >
      <FontAwesomeIcon
        icon={meta.icon}
        className={`h-3 w-3 ${meta.colorClass}`}
        aria-hidden="true"
      />
      {meta.label}
    </span>
  );
}
