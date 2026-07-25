import { describe, expect, it } from 'vitest';
import type { components } from '../api/schema';
import { indexColleagues, searchColleagues } from './colleagueSearch';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

/** A floor holding one pod whose seats are described as [label, occupant name | null]. */
function floorWith(seats: [string, string | null][]): SeatMapResponse {
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
              {
                tableId: 1,
                label: 'R5',
                capacity: seats.length,
                posX: 0,
                posY: 0,
                rotation: 0,
                seats: seats.map(([label, occupant], i) => ({
                  seatId: i + 1,
                  seatLabel: label,
                  side: 'A' as const,
                  seatIndex: i + 1,
                  accessible: false,
                  state: occupant ? ('OCCUPIED' as const) : ('AVAILABLE' as const),
                  occupantUserId: occupant ? i + 100 : undefined,
                  occupantDisplayName: occupant ?? undefined,
                  checkedIn: false,
                })),
              },
            ],
          },
        ],
      },
    ],
  };
}

describe('indexColleagues', () => {
  it('indexes only seats that somebody actually holds', () => {
    const people = indexColleagues(
      floorWith([
        ['R5-A1', 'Priya N.'],
        ['R5-A2', null],
        ['R5-A3', 'Sam K.'],
      ]),
    );

    expect(people.map((p) => p.name)).toEqual(['Priya N.', 'Sam K.']);
    expect(people[0]).toMatchObject({ seatLabel: 'R5-A1', tableLabel: 'R5' });
  });
});

describe('searchColleagues', () => {
  const people = indexColleagues(
    floorWith([
      ['R5-A1', 'Priya N.'],
      ['R5-A2', 'Sam Kapoor'],
      ['R5-A3', 'Rosam Lee'],
    ]),
  );

  it('finds a person by the start of their name', () => {
    expect(searchColleagues(people, 'pri').map((p) => p.name)).toEqual(['Priya N.']);
  });

  it('answers the mirror question — who is sitting in this seat', () => {
    expect(searchColleagues(people, 'R5-A3').map((p) => p.name)).toEqual(['Rosam Lee']);
  });

  it('ranks a name that starts with the query above one that merely contains it', () => {
    // "sam" starts Sam Kapoor and sits inside Rosam Lee; the person you meant comes first.
    expect(searchColleagues(people, 'sam').map((p) => p.name)).toEqual([
      'Sam Kapoor',
      'Rosam Lee',
    ]);
  });

  it('is case-insensitive', () => {
    expect(searchColleagues(people, 'PRIYA')).toHaveLength(1);
  });

  it('returns nothing for an empty query rather than the whole office', () => {
    expect(searchColleagues(people, '   ')).toEqual([]);
  });

  it('caps how many results it returns', () => {
    expect(searchColleagues(people, 'a', 2)).toHaveLength(2);
  });
});
