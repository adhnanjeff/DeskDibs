import { describe, expect, it } from 'vitest';
import { SEAT_STATE_META, toSeatDisplayState } from './seatState';

const ME = 7;
const SOMEONE_ELSE = 9;

describe('toSeatDisplayState', () => {
  it('passes through the states that do not depend on who is looking', () => {
    expect(toSeatDisplayState('AVAILABLE')).toBe('AVAILABLE');
    expect(toSeatDisplayState('TEAM_RESERVED')).toBe('TEAM_RESERVED');
    expect(toSeatDisplayState('DISABLED')).toBe('DISABLED');
  });

  it('marks a colleague who has checked in as checked in, not merely occupied', () => {
    // The bug this pins: a colleague who had turned up rendered identically to one who had not,
    // so the map looked like it was ignoring the no-show cut-off on a seat that was never a
    // no-show in the first place.
    expect(
      toSeatDisplayState('OCCUPIED', {
        occupantUserId: SOMEONE_ELSE,
        currentUserId: ME,
        checkedIn: true,
      }),
    ).toBe('CHECKED_IN');
  });

  it('leaves a colleague who has not checked in as occupied — the release will reclaim it', () => {
    expect(
      toSeatDisplayState('OCCUPIED', {
        occupantUserId: SOMEONE_ELSE,
        currentUserId: ME,
        checkedIn: false,
      }),
    ).toBe('OCCUPIED');
  });

  it('calls your own un-checked-in desk yours, so you can find it', () => {
    expect(
      toSeatDisplayState('OCCUPIED', { occupantUserId: ME, currentUserId: ME, checkedIn: false }),
    ).toBe('YOURS');
  });

  it('shows your own desk as checked in once you have arrived', () => {
    expect(
      toSeatDisplayState('OCCUPIED', { occupantUserId: ME, currentUserId: ME, checkedIn: true }),
    ).toBe('CHECKED_IN');
  });

  it('treats an anonymous occupied seat as occupied when nobody is signed in', () => {
    expect(toSeatDisplayState('OCCUPIED')).toBe('OCCUPIED');
  });

  it('never lets colour be the only signal — every state carries an icon and a label', () => {
    for (const [state, meta] of Object.entries(SEAT_STATE_META)) {
      expect(meta.label, state).toBeTruthy();
      expect(meta.icon, state).toBeTruthy();
      expect(meta.fill, state).toBeTruthy();
    }
  });

  it('only lets a click act on a seat that is actually free', () => {
    expect(SEAT_STATE_META.AVAILABLE.actionable).toBe(true);
    expect(SEAT_STATE_META.OCCUPIED.actionable).toBe(false);
    expect(SEAT_STATE_META.CHECKED_IN.actionable).toBe(false);
    expect(SEAT_STATE_META.TEAM_RESERVED.actionable).toBe(false);
    expect(SEAT_STATE_META.DISABLED.actionable).toBe(false);
  });
});
