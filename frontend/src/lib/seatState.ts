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
    label: 'Occupied',
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

  if (options.checkedIn) return isMine ? 'CHECKED_IN' : 'OCCUPIED';
  if (isMine) return 'YOURS';
  return 'OCCUPIED';
}
