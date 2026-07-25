import type { components } from '../api/schema';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

/** One person sitting somewhere on today's floor. */
export interface Colleague {
  userId: number;
  name: string;
  seatId: number;
  seatLabel: string;
  /** The pod their seat belongs to, e.g. `R5` — useful context in a result row. */
  tableLabel: string;
  checkedIn: boolean;
}

/**
 * Index who is sitting where today.
 *
 * Built entirely from the seat map already on screen — the same occupant names the map reveals
 * on hover — so this exposes nothing the floor plan doesn't. It only ever knows about people
 * with a booking for the day being viewed: someone who works remotely, or who hasn't claimed a
 * desk, simply isn't in the office to find.
 */
export function indexColleagues(seatMap: SeatMapResponse): Colleague[] {
  const people: Colleague[] = [];

  for (const floor of seatMap.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        for (const seat of table.seats ?? []) {
          if (seat.occupantUserId == null || !seat.occupantDisplayName) continue;
          people.push({
            userId: seat.occupantUserId,
            name: seat.occupantDisplayName,
            seatId: seat.seatId ?? -1,
            seatLabel: seat.seatLabel ?? '',
            tableLabel: table.label ?? '',
            checkedIn: Boolean(seat.checkedIn),
          });
        }
      }
    }
  }

  return people.sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * Filter the index by name or seat code.
 *
 * Matching a seat label too means "who is in R5-A2?" works as well as "where is Priya?" — the
 * same box answers both directions of the question. Results that *start* with the query rank
 * above ones that merely contain it, so typing a couple of letters of a name surfaces that
 * person rather than everyone who happens to share the substring.
 */
export function searchColleagues(
  people: Colleague[],
  query: string,
  limit = 6,
): Colleague[] {
  const q = query.trim().toLowerCase();
  if (q === '') return [];

  const scored: { person: Colleague; rank: number }[] = [];

  for (const person of people) {
    const name = person.name.toLowerCase();
    const seat = person.seatLabel.toLowerCase();

    let rank: number;
    if (name.startsWith(q) || seat.startsWith(q)) rank = 0;
    else if (name.includes(q) || seat.includes(q)) rank = 1;
    else continue;

    scored.push({ person, rank });
  }

  return scored
    .sort((a, b) => a.rank - b.rank || a.person.name.localeCompare(b.person.name))
    .slice(0, limit)
    .map((s) => s.person);
}
