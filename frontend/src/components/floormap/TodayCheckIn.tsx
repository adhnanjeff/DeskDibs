import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCircleCheck,
  faLocationDot,
  faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import { useCheckIn } from '../../hooks/useBookingActions';
import { useMyBookings } from '../../hooks/useMyBookings';

type BookingResponse = components['schemas']['BookingResponse'];

/**
 * Check in to today's desk, from the page people already have open.
 *
 * <p>Check-in used to live only on My bookings, which is the wrong place for it: it is a thing you
 * do once, on arrival, on the one day it applies to — and the cost of not finding it is losing your
 * desk to the no-show release at the cut-off. So it sits on the floor plan, which is the screen
 * somebody opens when they walk in.
 *
 * <p>Renders nothing at all unless there is something to do. A permanent card saying "you have no
 * booking today" would push the floor down the page every day somebody works from home.
 */
export function TodayCheckIn({
  officeToday,
  releaseTime = '11:00',
}: {
  /** The office's own today, from the seat map. Never the browser's clock. */
  officeToday: string;
  /** Office-local time the no-show release runs, e.g. `11:00`. */
  releaseTime?: string;
}) {
  const bookings = useMyBookings();
  const checkIn = useCheckIn();

  const todays = (bookings.data ?? []).find(
    (booking: BookingResponse) => booking.bookingDate === officeToday && booking.status === 'ACTIVE',
  );

  if (!todays) {
    return null;
  }

  const arrived = todays.checkedInAt != null;
  const failure = checkIn.isError
    ? checkIn.error instanceof Error
      ? checkIn.error.message
      : 'Could not check you in.'
    : null;

  return (
    <section
      aria-label="Today's desk"
      className={`mb-4 ui-edge px-4 py-3 shadow-[var(--dd-shadow-sm)] ${
        arrived ? 'border-line bg-seat-checked-in-tint' : 'border-line bg-paper'
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="flex items-center gap-2.5 text-sm font-semibold text-ink">
          <FontAwesomeIcon
            icon={arrived ? faCircleCheck : faLocationDot}
            className={`h-4 w-4 ${arrived ? 'text-seat-checked-in' : 'text-ink/50'}`}
            aria-hidden="true"
          />
          <span>
            Today you have{' '}
            <span className="font-mono font-bold ui-label">{todays.seatLabel}</span>
            {arrived ? (
              <span className="text-ink/60"> — checked in</span>
            ) : (
              // The consequence, not just the instruction. "Check in" alone does not explain why
              // it matters, and the reason it matters is that the desk goes away without it.
              <span className="text-ink/60"> — check in by {releaseTime} or it is released</span>
            )}
          </span>
        </p>

        {!arrived && (
          <button
            type="button"
            onClick={() => todays.id != null && checkIn.mutate(todays.id)}
            disabled={checkIn.isPending || todays.id == null}
            className="ui-control flex items-center justify-center gap-2 ui-edge border-action bg-action px-4 py-2.5 text-sm font-bold ui-label text-white shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
          >
            <FontAwesomeIcon icon={faCircleCheck} className="h-4 w-4" aria-hidden="true" />
            {checkIn.isPending ? 'Checking in…' : "I'm here"}
          </button>
        )}
      </div>

      {failure && (
        <p role="alert" className="mt-2 flex items-start gap-1.5 text-xs font-semibold text-danger">
          <FontAwesomeIcon icon={faTriangleExclamation} className="mt-0.5 h-3 w-3" aria-hidden="true" />
          {failure}
        </p>
      )}
    </section>
  );
}
