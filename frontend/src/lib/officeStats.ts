import type { components } from '../api/schema';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type SeatMapSeat = components['schemas']['SeatMapSeat'];

/**
 * What today's floor adds up to.
 *
 * Every number here is counted from the seat map the office is already showing — nothing is
 * estimated, and nothing is reported that the system does not actually know. Meeting-room,
 * cafeteria and parking figures are deliberately absent: DeskDibs books desks, so it has no
 * source of truth for those, and a plausible-looking number nobody can verify is worse than
 * no number at all.
 */
export interface OfficeStats {
  /** Every seat on the floor, including ones out of service. */
  totalSeats: number;
  /** Seats that could be sat in today — total minus anything disabled. */
  bookableSeats: number;
  /** Claimed by somebody, checked in or not. */
  bookedSeats: number;
  /** Claimed *and* the person confirmed they turned up. */
  peopleInOffice: number;
  /** Claimed but not yet checked in — these are what the no-show release takes back. */
  awaitingCheckIn: number;
  /** Free to claim right now. */
  availableSeats: number;
  /** Held for a team until that hold releases. */
  teamHeldSeats: number;
  /** Out of service. */
  disabledSeats: number;
  /** Booked as a percentage of bookable seats, rounded to a whole number. */
  occupancyPercent: number;
}

function eachSeat(seatMap: SeatMapResponse): SeatMapSeat[] {
  const seats: SeatMapSeat[] = [];
  for (const floor of seatMap.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        seats.push(...(table.seats ?? []));
      }
    }
  }
  return seats;
}

export function summariseOffice(seatMap: SeatMapResponse): OfficeStats {
  const seats = eachSeat(seatMap);

  let bookedSeats = 0;
  let peopleInOffice = 0;
  let availableSeats = 0;
  let teamHeldSeats = 0;
  let disabledSeats = 0;

  for (const seat of seats) {
    switch (seat.state) {
      case 'OCCUPIED':
        bookedSeats += 1;
        if (seat.checkedIn) peopleInOffice += 1;
        break;
      case 'TEAM_RESERVED':
        teamHeldSeats += 1;
        break;
      case 'DISABLED':
        disabledSeats += 1;
        break;
      default:
        availableSeats += 1;
    }
  }

  // A seat that is out of service is not capacity, so counting it would understate how full
  // the office really is.
  const bookableSeats = seats.length - disabledSeats;

  return {
    totalSeats: seats.length,
    bookableSeats,
    bookedSeats,
    peopleInOffice,
    awaitingCheckIn: bookedSeats - peopleInOffice,
    availableSeats,
    teamHeldSeats,
    disabledSeats,
    occupancyPercent: bookableSeats === 0 ? 0 : Math.round((bookedSeats / bookableSeats) * 100),
  };
}
