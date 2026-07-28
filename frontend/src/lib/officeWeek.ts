/**
 * Which slice of the server's booking horizon the date strip shows.
 *
 * <p>The API returns the whole horizon — a fortnight — because the strip's job is to let somebody
 * see which day is quiet before committing to it. Rendering all fourteen makes a row so long it
 * scrolls on every screen and reads as a wall of near-identical tiles. One week is the unit people
 * actually plan in, so that is what gets drawn; the rest of the horizon is still there in the data
 * and still bookable through the map.
 *
 * <p>Everything here works on the server's own ISO date strings and never constructs "today" from
 * the browser clock — the office decides what day it is, and the first entry of the horizon is
 * already that answer.
 */

interface HasDate {
  date?: string;
}

/** `Date` → `yyyy-mm-dd`, in local time, matching how the day tiles parse. */
function toIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * The Monday-to-Sunday week the horizon opens on.
 *
 * <p>If the horizon opens on a Sunday, that week is over — everything left in it is the Sunday
 * itself, which is closed. So the strip skips to the week beginning the next morning rather than
 * showing a single greyed-out tile.
 *
 * <p>ISO date strings sort lexicographically, which is why the range test needs no parsing.
 */
export function currentOfficeWeek<T extends HasDate>(days: readonly T[]): T[] {
  const firstIso = days[0]?.date;
  if (!firstIso) return [...days];

  const first = new Date(`${firstIso}T00:00:00`);
  if (Number.isNaN(first.getTime())) return [...days];

  const start = new Date(first);
  const weekday = first.getDay(); // 0 = Sunday
  if (weekday === 0) {
    start.setDate(first.getDate() + 1); // that week is spent; show the next one
  } else {
    start.setDate(first.getDate() - (weekday - 1)); // back to this week's Monday
  }

  const end = new Date(start);
  end.setDate(start.getDate() + 6);

  const startIso = toIso(start);
  const endIso = toIso(end);
  const week = days.filter((day) => day.date && day.date >= startIso && day.date <= endIso);

  // A horizon that does not reach the computed week at all (a very short one, or a run of
  // closed days) must never leave the strip empty — fall back to showing what there is.
  return week.length > 0 ? week : [...days];
}
