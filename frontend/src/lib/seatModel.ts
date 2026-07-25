import type { components } from '../api/schema';
import { SEAT_STATE_META, toSeatDisplayState, type SeatDisplayState } from './seatState';

type SeatMapSeat = components['schemas']['SeatMapSeat'];

/** Everything a seat tile needs to render, place, and announce one seat. */
export interface SeatTileModel {
  seatId: number;
  seatLabel: string;
  displayState: SeatDisplayState;
  occupantName?: string | null;
  actionable: boolean;
  /** Which side of the desk (A = one column, B = the facing column). */
  side: 'A' | 'B';
  /** Position along the side, 1-based — the row within the rotated pod. */
  seatIndex: number;
}

/** The three transient motions a tile can play, all state-explaining. */
export type SeatAnimation = 'claimed' | 'lost' | 'updated';

export function occupantLabel(seat: SeatMapSeat): string | null {
  if (seat.occupantDisplayName) return seat.occupantDisplayName;
  if (seat.reservedForTeamName) return `${seat.reservedForTeamName} (team)`;
  return null;
}

/**
 * Fold the raw API seat + the viewer's identity + any in-flight claim into the
 * single display model the map and list both render — the one place seat state
 * is decided.
 */
export function buildSeatModel(
  seat: SeatMapSeat,
  currentUserId: number | null | undefined,
  pendingSeatId: number | null,
): SeatTileModel {
  let displayState: SeatDisplayState = toSeatDisplayState(seat.state ?? 'AVAILABLE', {
    occupantUserId: seat.occupantUserId,
    currentUserId,
    checkedIn: seat.checkedIn,
  });
  if (seat.seatId != null && seat.seatId === pendingSeatId) displayState = 'PENDING';

  return {
    seatId: seat.seatId ?? -1,
    seatLabel: seat.seatLabel ?? '',
    displayState,
    occupantName: occupantLabel(seat),
    actionable: SEAT_STATE_META[displayState].actionable,
    side: seat.side === 'B' ? 'B' : 'A',
    seatIndex: seat.seatIndex ?? 1,
  };
}
