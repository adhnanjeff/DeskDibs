import { describe, expect, it } from 'vitest';
import type { components } from '../api/schema';
import { summariseOffice } from './officeStats';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type SeatMapSeat = components['schemas']['SeatMapSeat'];

let nextSeatId = 1;

function seat(partial: Partial<SeatMapSeat>): SeatMapSeat {
  const id = nextSeatId++;
  return {
    seatId: id,
    seatLabel: `S-${id}`,
    side: 'A',
    seatIndex: 1,
    accessible: false,
    state: 'AVAILABLE',
    checkedIn: false,
    ...partial,
  };
}

function floorOf(seats: SeatMapSeat[]): SeatMapResponse {
  return {
    date: '2026-07-25',
    floors: [
      {
        floorId: 1,
        name: 'Main Floor',
        zones: [
          {
            zoneId: 1,
            name: 'Open',
            tables: [
              { tableId: 1, label: 'R1', capacity: seats.length, posX: 0, posY: 0, rotation: 0, seats },
            ],
          },
        ],
      },
    ],
  };
}

describe('summariseOffice', () => {
  it('counts who is actually in the office, not just who booked', () => {
    const stats = summariseOffice(
      floorOf([
        seat({ state: 'OCCUPIED', checkedIn: true }),
        seat({ state: 'OCCUPIED', checkedIn: true }),
        seat({ state: 'OCCUPIED', checkedIn: false }),
        seat({ state: 'AVAILABLE' }),
      ]),
    );

    expect(stats.bookedSeats).toBe(3);
    expect(stats.peopleInOffice).toBe(2);
    expect(stats.awaitingCheckIn).toBe(1);
    expect(stats.availableSeats).toBe(1);
  });

  it('excludes out-of-service desks from capacity, so occupancy is not understated', () => {
    const stats = summariseOffice(
      floorOf([
        seat({ state: 'OCCUPIED', checkedIn: true }),
        seat({ state: 'AVAILABLE' }),
        seat({ state: 'DISABLED' }),
        seat({ state: 'DISABLED' }),
      ]),
    );

    expect(stats.totalSeats).toBe(4);
    expect(stats.disabledSeats).toBe(2);
    expect(stats.bookableSeats).toBe(2);
    // 1 of 2 usable desks, not 1 of 4.
    expect(stats.occupancyPercent).toBe(50);
  });

  it('counts a team hold as neither free nor occupied', () => {
    const stats = summariseOffice(
      floorOf([seat({ state: 'TEAM_RESERVED' }), seat({ state: 'AVAILABLE' })]),
    );

    expect(stats.teamHeldSeats).toBe(1);
    expect(stats.availableSeats).toBe(1);
    expect(stats.bookedSeats).toBe(0);
  });

  it('reports zero occupancy rather than dividing by zero on an empty floor', () => {
    expect(summariseOffice(floorOf([])).occupancyPercent).toBe(0);
    expect(summariseOffice({ date: '2026-07-25', floors: [] }).totalSeats).toBe(0);
  });
});
