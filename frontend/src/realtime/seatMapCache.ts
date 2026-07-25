import type { components } from '../api/schema';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type SeatMapSeat = components['schemas']['SeatMapSeat'];

/**
 * Replace one seat in a seat-map snapshot with a freshly-broadcast version,
 * immutably. A live `SeatStatusChanged` carries exactly one updated seat (the
 * same shape the initial snapshot uses), so applying it is a targeted swap —
 * every other seat, table and zone is returned untouched.
 *
 * Kept pure so it can be unit-tested without a socket, and so React Query only
 * re-renders the branch that actually changed.
 */
export function mergeSeatUpdate(
  map: SeatMapResponse,
  updated: SeatMapSeat,
): SeatMapResponse {
  if (updated.seatId == null) return map;

  return {
    ...map,
    floors: (map.floors ?? []).map((floor) => ({
      ...floor,
      zones: (floor.zones ?? []).map((zone) => ({
        ...zone,
        tables: (zone.tables ?? []).map((table) => {
          const seats = table.seats ?? [];
          if (!seats.some((seat) => seat.seatId === updated.seatId)) return table;
          return {
            ...table,
            seats: seats.map((seat) => (seat.seatId === updated.seatId ? updated : seat)),
          };
        }),
      })),
    })),
  };
}
