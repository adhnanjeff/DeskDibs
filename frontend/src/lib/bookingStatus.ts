import {
  faCircleCheck,
  faCircleXmark,
  faClockRotateLeft,
  faUserSlash,
  faWrench,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';

/** Every state a booking row can be in, exactly as the API reports it. */
export type BookingStatusValue =
  | 'ACTIVE'
  | 'CANCELLED'
  | 'RELEASED_NO_SHOW'
  | 'RELEASED_USER_DEACTIVATED'
  | 'RELEASED_SEAT_REMOVED';

export interface BookingStatusMeta {
  label: string;
  icon: IconDefinition;
  /**
   * Why the booking is in this state, in the second person. Shown to the person it happened to.
   *
   * `null` for the two states that need no explanation — you know you cancelled, and an active
   * booking is just a booking.
   */
  explanation: string | null;
  /**
   * `notice` states are ones the person did not choose and may not have noticed. They are the
   * closest thing this application has to a notification (PLAN.md §5 #12 and #13), so they are
   * given a border and a live-region announcement rather than a quiet grey chip.
   */
  tone: 'active' | 'muted' | 'notice';
}

/**
 * How each booking state is presented on the bookings page.
 *
 * <p>The two administrative releases are the reason this registry exists. Rendering a raw
 * `RELEASED_SEAT_REMOVED` told somebody nothing about why the desk they had booked for Thursday is
 * no longer theirs, and there is no email or Teams channel in this system to tell them separately —
 * so this page has to carry the message itself, in words, the next time they look.
 *
 * <p>Every state pairs a distinct icon with its label, never colour alone.
 */
export const BOOKING_STATUS_META: Record<BookingStatusValue, BookingStatusMeta> = {
  ACTIVE: {
    label: 'Booked',
    icon: faCircleCheck,
    explanation: null,
    tone: 'active',
  },
  CANCELLED: {
    label: 'Cancelled',
    icon: faCircleXmark,
    explanation: null,
    tone: 'muted',
  },
  RELEASED_NO_SHOW: {
    label: 'Released — no check-in',
    icon: faClockRotateLeft,
    explanation:
      'Nobody checked in by the morning cut-off, so this desk went back into the pool for someone else.',
    tone: 'muted',
  },
  RELEASED_USER_DEACTIVATED: {
    label: 'Released — account closed',
    icon: faUserSlash,
    explanation: 'This account was deactivated, so the desks it was holding were returned to the pool.',
    tone: 'notice',
  },
  RELEASED_SEAT_REMOVED: {
    label: 'Released — desk withdrawn',
    icon: faWrench,
    explanation:
      'This desk was taken out of service, so your booking could not stand. Pick another one from the floor map.',
    tone: 'notice',
  },
};

/**
 * Presentation for one status, tolerating a value this build has never heard of.
 *
 * <p>A booking page that throws because the backend added a status is a worse outcome than one
 * that shows the raw word: the rest of somebody's bookings are still perfectly readable.
 */
export function bookingStatusMeta(status: string | undefined): BookingStatusMeta {
  const known = BOOKING_STATUS_META[status as BookingStatusValue];
  if (known) return known;
  return {
    label: status ?? 'Unknown',
    icon: faCircleXmark,
    explanation: null,
    tone: 'muted',
  };
}

/** True when the booking still holds its seat. Only `ACTIVE` does. */
export function holdsTheSeat(status: string | undefined): boolean {
  return status === 'ACTIVE';
}

/**
 * True when this booking was ended by somebody else and the person may not know yet — the set the
 * bookings page announces rather than merely lists.
 */
export function wasTakenAway(status: string | undefined): boolean {
  return bookingStatusMeta(status).tone === 'notice';
}
