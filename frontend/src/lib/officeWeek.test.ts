import { describe, expect, it } from 'vitest';
import { currentOfficeWeek } from './officeWeek';

/** A horizon of consecutive days starting at `from`, the shape the API returns. */
function horizon(from: string, count: number): { date: string }[] {
  const start = new Date(`${from}T00:00:00`);
  return Array.from({ length: count }, (_, index) => {
    const day = new Date(start);
    day.setDate(start.getDate() + index);
    const month = String(day.getMonth() + 1).padStart(2, '0');
    const date = String(day.getDate()).padStart(2, '0');
    return { date: `${day.getFullYear()}-${month}-${date}` };
  });
}

describe('currentOfficeWeek', () => {
  it('shows Monday to Sunday when the horizon opens mid-week', () => {
    // 2026-07-29 is a Wednesday.
    const week = currentOfficeWeek(horizon('2026-07-29', 15));

    expect(week.map((day) => day.date)).toEqual([
      '2026-07-29',
      '2026-07-30',
      '2026-07-31',
      '2026-08-01',
      '2026-08-02',
    ]);
  });

  it('shows the whole week when the horizon opens on a Monday', () => {
    // 2026-08-10 is a Monday.
    const week = currentOfficeWeek(horizon('2026-08-10', 15));

    expect(week).toHaveLength(7);
    expect(week[0].date).toBe('2026-08-10');
    expect(week[6].date).toBe('2026-08-16');
  });

  it('skips to the next week when the horizon opens on a Sunday', () => {
    // 2026-08-09 is a Sunday — that week is spent, so the strip starts the next morning.
    const week = currentOfficeWeek(horizon('2026-08-09', 15));

    expect(week[0].date).toBe('2026-08-10');
    expect(week).toHaveLength(7);
    expect(week.map((day) => day.date)).not.toContain('2026-08-09');
  });

  it('falls back to whatever it was given rather than rendering an empty strip', () => {
    expect(currentOfficeWeek([])).toEqual([]);
    // A Sunday with nothing after it: the next week is empty, so show the day there is.
    expect(currentOfficeWeek(horizon('2026-08-09', 1))).toEqual([{ date: '2026-08-09' }]);
  });
});
