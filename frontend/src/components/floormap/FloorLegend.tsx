import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { SEAT_STATE_META, type SeatDisplayState } from '../../lib/seatState';

/** The states worth explaining on the map, most common first. */
const LEGEND_STATES: SeatDisplayState[] = [
  'AVAILABLE',
  'OCCUPIED',
  'SELECTED',
  'YOURS',
  'CHECKED_IN',
  'TEAM_RESERVED',
  'DISABLED',
];

function Swatch({ state }: { state: SeatDisplayState }) {
  const meta = SEAT_STATE_META[state];
  const glyph = meta.glyph === 'paper' ? 'var(--color-paper)' : 'var(--color-ink)';
  return (
    <span className="flex items-center gap-1.5">
      <span
        className="flex h-4 w-4 items-center justify-center border-2 border-ink"
        style={{ background: meta.fill }}
      >
        <FontAwesomeIcon
          icon={meta.icon}
          aria-hidden="true"
          style={{ color: glyph, width: 8, height: 8 }}
        />
      </span>
      <span className="font-mono text-[10px] font-semibold uppercase tracking-wider text-ink/80">
        {meta.label}
      </span>
    </span>
  );
}

export function FloorLegend() {
  return (
    <ul
      className="flex flex-wrap items-center gap-x-4 gap-y-2"
      aria-label="Seat state legend"
    >
      {LEGEND_STATES.map((state) => (
        <li key={state}>
          <Swatch state={state} />
        </li>
      ))}
    </ul>
  );
}
