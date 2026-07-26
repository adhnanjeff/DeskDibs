import {
  faBan,
  faChair,
  faCircleUser,
  faHourglassHalf,
  faSquareCheck,
  faUser,
  faUserCheck,
  faUsers,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';

/**
 * The seat states the map renders. Four (`AVAILABLE`, `OCCUPIED`,
 * `TEAM_RESERVED`, `DISABLED`) come from the API as `SeatMapSeat.state`.
 * `YOURS` and `CHECKED_IN` are derived from the viewer's identity; `PENDING`
 * is transient optimistic-UI state during a claim; `SELECTED` is pure UI —
 * the seat the viewer has clicked but not yet booked.
 *
 * This registry is the single source of truth for colour + icon + label per
 * state, shared by the legend badge and the interactive floor tiles. Colour is
 * never the only signal: every state pairs a distinct hue with a distinct icon
 * and a text label.
 */
export type SeatDisplayState =
  | 'AVAILABLE'
  | 'YOURS'
  | 'OCCUPIED'
  | 'TEAM_RESERVED'
  | 'CHECKED_IN'
  | 'DISABLED'
  | 'PENDING'
  | 'SELECTED';

export interface SeatStateMeta {
  label: string;
  icon: IconDefinition;
  /** CSS custom property for the seat tile's fill. */
  fill: string;
  /** Colour of the glyph drawn on the fill. */
  glyph: 'ink' | 'paper';
  /** True when a click can act on the seat (claim it). */
  actionable: boolean;
  /** Legend badge classes (SeatStateBadge). */
  colorClass: string;
  tintClass: string;
  textClass: string;
}

export const SEAT_STATE_META: Record<SeatDisplayState, SeatStateMeta> = {
  AVAILABLE: {
    label: 'Available',
    icon: faChair,
    fill: 'var(--color-seat-available)',
    glyph: 'ink',
    actionable: true,
    colorClass: 'text-seat-occupied',
    tintClass: 'bg-seat-available-tint',
    textClass: 'text-ink',
  },
  SELECTED: {
    label: 'Selected',
    icon: faSquareCheck,
    fill: 'var(--color-seat-selected)',
    glyph: 'ink',
    actionable: true,
    colorClass: 'text-ink',
    tintClass: 'bg-seat-pending-tint',
    textClass: 'text-ink',
  },
  YOURS: {
    label: 'Yours',
    icon: faCircleUser,
    fill: 'var(--color-seat-yours)',
    glyph: 'paper',
    actionable: false,
    colorClass: 'text-seat-yours',
    tintClass: 'bg-seat-yours-tint',
    textClass: 'text-ink',
  },
  OCCUPIED: {
    // Named for what it tells you to expect, not for the raw API state: these are exactly the
    // desks the no-show release will hand back at the cut-off.
    label: 'Booked, not in',
    icon: faUser,
    fill: 'var(--color-seat-occupied)',
    glyph: 'paper',
    actionable: false,
    colorClass: 'text-seat-occupied',
    tintClass: 'bg-seat-occupied-tint',
    textClass: 'text-ink',
  },
  TEAM_RESERVED: {
    label: 'Team hold',
    icon: faUsers,
    fill: 'var(--color-seat-team-reserved)',
    glyph: 'ink',
    actionable: false,
    colorClass: 'text-seat-team-reserved',
    tintClass: 'bg-seat-team-reserved-tint',
    textClass: 'text-ink',
  },
  CHECKED_IN: {
    label: 'Checked in',
    icon: faUserCheck,
    fill: 'var(--color-seat-checked-in)',
    glyph: 'paper',
    actionable: false,
    colorClass: 'text-seat-checked-in',
    tintClass: 'bg-seat-checked-in-tint',
    textClass: 'text-ink',
  },
  DISABLED: {
    label: 'Disabled',
    icon: faBan,
    fill: 'var(--color-seat-disabled)',
    glyph: 'ink',
    actionable: false,
    colorClass: 'text-seat-disabled',
    tintClass: 'bg-seat-disabled-tint',
    textClass: 'text-ink',
  },
  PENDING: {
    label: 'Booking…',
    icon: faHourglassHalf,
    fill: 'var(--color-seat-pending)',
    glyph: 'ink',
    actionable: false,
    colorClass: 'text-seat-pending',
    tintClass: 'bg-seat-pending-tint',
    textClass: 'text-ink',
  },
};

/**
 * Derives the richer display state from the raw API state plus the viewer's
 * own identity. `SELECTED` and `PENDING` are applied by the UI on top of this,
 * never returned here.
 *
 * <p><b>Check-in is a property of the seat, not of who is looking at it.</b> It used to render
 * green only on your own desk, so a colleague who had turned up looked exactly like one who had
 * not — and since the no-show release only takes back the ones who have not, the map appeared to
 * be ignoring its own cut-off. Now the two are told apart for everybody: green means somebody is
 * actually sitting there, plain occupied means booked-but-not-arrived, which is precisely the set
 * the 11:00 release will reclaim. `YOURS` keeps its meaning for the case it is useful in — your
 * desk, before you have checked into it.
 */
export function toSeatDisplayState(
  apiState: 'AVAILABLE' | 'OCCUPIED' | 'TEAM_RESERVED' | 'DISABLED',
  options: {
    occupantUserId?: number | null;
    currentUserId?: number | null;
    checkedIn?: boolean | null;
  } = {},
): SeatDisplayState {
  if (apiState !== 'OCCUPIED') return apiState;

  const isMine =
    options.currentUserId != null &&
    options.occupantUserId != null &&
    options.occupantUserId === options.currentUserId;

  if (options.checkedIn) return 'CHECKED_IN';
  if (isMine) return 'YOURS';
  return 'OCCUPIED';
}
