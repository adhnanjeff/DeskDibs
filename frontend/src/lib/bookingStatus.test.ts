import { describe, expect, it } from 'vitest';
import {
  BOOKING_STATUS_META,
  bookingStatusMeta,
  holdsTheSeat,
  wasTakenAway,
} from './bookingStatus';

describe('bookingStatusMeta', () => {
  it('never lets colour be the only signal — every state carries a label and an icon', () => {
    for (const [status, meta] of Object.entries(BOOKING_STATUS_META)) {
      expect(meta.label, status).toBeTruthy();
      expect(meta.icon, status).toBeTruthy();
    }
  });

  it('explains the two releases somebody else caused, in words', () => {
    // PLAN.md §5 #12 and #13 promise the affected person is told. With no mail or Teams channel
    // in this system, this text is the entire notification — if it is empty, nobody is told.
    expect(BOOKING_STATUS_META.RELEASED_SEAT_REMOVED.explanation).toMatch(/out of service/i);
    expect(BOOKING_STATUS_META.RELEASED_USER_DEACTIVATED.explanation).toMatch(/deactivated/i);
  });

  it('does not explain the states the person already understands', () => {
    expect(BOOKING_STATUS_META.ACTIVE.explanation).toBeNull();
    expect(BOOKING_STATUS_META.CANCELLED.explanation).toBeNull();
  });

  it('never shows a released desk the raw enum name', () => {
    expect(bookingStatusMeta('RELEASED_SEAT_REMOVED').label).not.toMatch(/_/);
    expect(bookingStatusMeta('RELEASED_NO_SHOW').label).not.toMatch(/_/);
  });

  it('falls back to the raw value rather than throwing on a status this build has not seen', () => {
    // A bookings page that crashes because the backend shipped a new status is worse than one
    // that shows an unfamiliar word next to four perfectly readable rows.
    const meta = bookingStatusMeta('RELEASED_SOMETHING_NEW');
    expect(meta.label).toBe('RELEASED_SOMETHING_NEW');
    expect(meta.icon).toBeTruthy();
    expect(meta.tone).toBe('muted');
  });

  it('survives a missing status', () => {
    expect(bookingStatusMeta(undefined).label).toBe('Unknown');
  });
});

describe('holdsTheSeat', () => {
  it('is true only for ACTIVE — every other state has left the partial index', () => {
    expect(holdsTheSeat('ACTIVE')).toBe(true);
    expect(holdsTheSeat('CANCELLED')).toBe(false);
    expect(holdsTheSeat('RELEASED_NO_SHOW')).toBe(false);
    expect(holdsTheSeat('RELEASED_USER_DEACTIVATED')).toBe(false);
    expect(holdsTheSeat('RELEASED_SEAT_REMOVED')).toBe(false);
  });
});

describe('wasTakenAway', () => {
  it('flags only the releases the person did not choose', () => {
    expect(wasTakenAway('RELEASED_SEAT_REMOVED')).toBe(true);
    expect(wasTakenAway('RELEASED_USER_DEACTIVATED')).toBe(true);
  });

  it('does not flag a cancellation or a no-show', () => {
    // You know you cancelled. And a no-show is the documented consequence of not checking in —
    // announcing it as something done *to* you would cry wolf on the one that matters.
    expect(wasTakenAway('CANCELLED')).toBe(false);
    expect(wasTakenAway('RELEASED_NO_SHOW')).toBe(false);
    expect(wasTakenAway('ACTIVE')).toBe(false);
  });
});
