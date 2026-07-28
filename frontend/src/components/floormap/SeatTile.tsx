import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { SEAT_STATE_META, type SeatDisplayState } from '../../lib/seatState';
import { teamTint } from '../../lib/teamColors';
import type { SeatAnimation, SeatTileModel } from '../../lib/seatModel';
import { SEAT_TILE } from '../../lib/floorPlan';

interface SeatTileProps {
  seat: SeatTileModel;
  selected: boolean;
  animation: SeatAnimation | null;
  /** Pinned by a colleague search — the answer to "where are they sitting?". */
  located?: boolean;
  /**
   * Overrides which seats respond to a click. Booking only ever acts on a free seat, but a
   * manager holding desks for a team may pick an occupied one on purpose — the API answers with
   * a partial-success report naming who already holds it, rather than refusing the whole request.
   */
  canSelect?: (seat: SeatTileModel) => boolean;
  onSelect: (seat: SeatTileModel) => void;
  onHover: (seat: SeatTileModel | null, el: HTMLElement | null) => void;
}

/**
 * A single square seat — a Bauhaus tile. Colour + icon + accessible name always
 * travel together. Actionable (available) seats lift on hover/focus and can be
 * selected; every seat, actionable or not, reveals who's there on hover/focus.
 */
export function SeatTile({
  seat,
  selected,
  animation,
  located = false,
  canSelect,
  onSelect,
  onHover,
}: SeatTileProps) {
  const state: SeatDisplayState = selected ? 'SELECTED' : seat.displayState;
  const meta = SEAT_STATE_META[state];
  // A team hold takes its team's tint, so one team's block of desks reads as one block. Every
  // other signal for the state — icon, label, the team name on hover — is unchanged.
  const fill =
    state === 'TEAM_RESERVED' ? teamTint(seat.teamId) : meta.fill;
  const glyph = meta.glyph === 'paper' ? 'var(--color-paper)' : 'var(--color-ink)';
  const canAct = canSelect ? canSelect(seat) : meta.actionable;
  const occupant = seat.occupantName ? ` — ${seat.occupantName}` : '';
  // "Located" goes in the accessible name too: a ring around a tile is invisible to a screen
  // reader, so the search result has to be announced, not just drawn.
  const ariaLabel = `Seat ${seat.seatLabel}: ${meta.label}${occupant}${
    located ? '. Located by search.' : ''
  }${canAct ? '. Press to select.' : ''}`;
  const anim =
    animation === 'claimed'
      ? 'seat-claimed'
      : animation === 'lost'
        ? 'seat-lost'
        : animation === 'updated'
          ? 'seat-updated'
          : '';

  return (
    <button
      type="button"
      tabIndex={canAct || selected ? 0 : -1}
      onClick={() => (canAct ? onSelect(seat) : undefined)}
      onMouseEnter={(e) => onHover(seat, e.currentTarget)}
      onMouseLeave={() => onHover(null, null)}
      onFocus={(e) => onHover(seat, e.currentTarget)}
      onBlur={() => onHover(null, null)}
      aria-label={ariaLabel}
      title={ariaLabel}
      aria-pressed={canAct ? selected : undefined}
      className={`flex items-center justify-center ui-edge border-ink ${
        canAct || selected
          ? 'cursor-pointer hover:-translate-y-px hover:shadow-[var(--dd-shadow-sm)]'
          : 'cursor-default'
      } ${selected ? 'shadow-[var(--dd-shadow-sm)]' : ''} ${anim}`}
      style={{
        width: SEAT_TILE,
        height: SEAT_TILE,
        background: fill,
        transition: 'transform 120ms ease, box-shadow 120ms ease',
        // A located seat wears a heavy blue halo — distinct from the yellow of
        // selection, and drawn outside the tile so it never hides the state icon.
        ...(located
          ? { outline: '3px solid var(--color-info)', outlineOffset: 2, zIndex: 1 }
          : null),
      }}
    >
      <FontAwesomeIcon
        icon={meta.icon}
        aria-hidden="true"
        style={{ color: glyph, width: SEAT_TILE * 0.52, height: SEAT_TILE * 0.52 }}
      />
    </button>
  );
}
