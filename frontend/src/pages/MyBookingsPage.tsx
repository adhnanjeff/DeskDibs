import { useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Link } from 'react-router-dom';
import {
  faCalendarDay,
  faChair,
  faCircleCheck,
  faTriangleExclamation,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../api/schema';
import { useMyBookings } from '../hooks/useMyBookings';
import { useSeatMap } from '../hooks/useSeatMap';
import { useCancelBooking, useCheckIn } from '../hooks/useBookingActions';
import { bookingStatusMeta, holdsTheSeat, wasTakenAway } from '../lib/bookingStatus';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

type BookingResponse = components['schemas']['BookingResponse'];

function BookingsSkeleton() {
  return (
    <ul className="flex flex-col gap-2" aria-hidden="true" data-testid="bookings-skeleton">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="skeleton-shimmer relative flex items-center gap-3 overflow-hidden ui-edge border-line/20 bg-paper p-4"
        >
          <div className="h-4 w-4 bg-ink/15" />
          <div className="h-4 w-24 bg-ink/15" />
          <div className="h-4 w-40 bg-ink/10" />
        </li>
      ))}
    </ul>
  );
}

function BookingsList() {
  const { data, isPending, isError, error, refetch } = useMyBookings();
  // "Today" comes from the seat map's server-resolved date, never from the browser's clock —
  // the same rule the booking rules follow. This query is almost always already cached.
  const { data: seatMap } = useSeatMap();
  const checkIn = useCheckIn();
  const cancel = useCancelBooking();
  const [actionError, setActionError] = useState<string | null>(null);

  if (isPending) return <BookingsSkeleton />;

  if (isError) {
    return (
      <ErrorFallback
        title="Couldn't load your bookings"
        message={error instanceof Error ? error.message : undefined}
        onRetry={() => void refetch()}
      />
    );
  }

  if (data.length === 0) {
    return (
      <p className="ui-edge border-dashed border-ink/40 bg-paper p-6 text-center text-sm font-semibold text-ink/55">
        You don&rsquo;t have any upcoming bookings. Head to the seat map to claim a desk.
      </p>
    );
  }

  const today = seatMap?.date ?? null;
  const busyId = checkIn.isPending
    ? checkIn.variables
    : cancel.isPending
      ? cancel.variables
      : null;

  // Bookings somebody else ended — a withdrawn desk, or a closed account. This page is the only
  // place they are told, so the count is announced rather than left to be noticed.
  const takenAway = data.filter((booking) => wasTakenAway(booking.status));

  return (
    <>
      {takenAway.length > 0 && (
        <div
          role="status"
          className="mb-3 flex items-start gap-2 ui-edge border-line bg-selected px-3 py-2 text-sm font-semibold text-ink shadow-[var(--dd-shadow-sm)]"
        >
          <FontAwesomeIcon
            icon={faTriangleExclamation}
            className="mt-0.5 h-3.5 w-3.5 shrink-0"
            aria-hidden="true"
          />
          <span>
            {takenAway.length === 1
              ? 'One of your bookings was released for you. '
              : `${takenAway.length} of your bookings were released for you. `}
            <Link to="/" className="underline underline-offset-2 hover:no-underline">
              Pick another desk
            </Link>
            .
          </span>
        </div>
      )}

      {actionError && (
        <p
          role="alert"
          className="mb-3 flex items-start gap-2 ui-edge border-danger bg-paper px-3 py-2 text-sm font-semibold text-danger"
        >
          <FontAwesomeIcon
            icon={faTriangleExclamation}
            className="mt-0.5 h-3.5 w-3.5"
            aria-hidden="true"
          />
          {actionError}
        </p>
      )}

      <ul className="flex flex-col gap-2">
        {data.map((booking) => (
          <BookingRow
            key={booking.id}
            booking={booking}
            today={today}
            busy={busyId === booking.id}
            onCheckIn={() => {
              setActionError(null);
              checkIn.mutate(booking.id ?? -1, {
                onError: (e) => setActionError(e instanceof Error ? e.message : 'Check-in failed.'),
              });
            }}
            onCancel={() => {
              setActionError(null);
              cancel.mutate(booking.id ?? -1, {
                onError: (e) =>
                  setActionError(e instanceof Error ? e.message : 'Could not cancel that booking.'),
              });
            }}
          />
        ))}
      </ul>
    </>
  );
}

function BookingRow({
  booking,
  today,
  busy,
  onCheckIn,
  onCancel,
}: {
  booking: BookingResponse;
  today: string | null;
  busy: boolean;
  onCheckIn: () => void;
  onCancel: () => void;
}) {
  const active = holdsTheSeat(booking.status);
  const checkedIn = Boolean(booking.checkedInAt);
  const isToday = today != null && booking.bookingDate === today;
  // Only today's booking can be checked into — the server enforces it, and offering the button
  // on a future date would just be a refusal waiting to happen.
  const canCheckIn = active && isToday && !checkedIn;
  const meta = bookingStatusMeta(booking.status);
  const notice = meta.tone === 'notice';

  return (
    <li
      className={`flex flex-wrap items-center gap-3 ui-edge p-4 ${
        notice
          ? // Not decoration: this row is the message. It has to survive being skim-read.
            'border-danger bg-paper shadow-[var(--dd-shadow-sm)]'
          : 'border-ink bg-paper shadow-[var(--dd-shadow-sm)]'
      } ${active ? '' : 'opacity-90'}`}
    >
      <FontAwesomeIcon icon={faCalendarDay} className="h-4 w-4 text-ink/45" aria-hidden="true" />
      <span className="font-semibold text-ink">
        {booking.bookingDate}
        {isToday && (
          <span className="ml-2 ui-edge border-line bg-selected px-1.5 py-0.5 font-mono text-[10px] ui-label">
            Today
          </span>
        )}
      </span>

      <span className="inline-flex items-center gap-1.5 font-mono text-sm font-bold ui-label text-ink">
        <FontAwesomeIcon icon={faChair} className="h-3.5 w-3.5 text-ink/45" aria-hidden="true" />
        {booking.seatLabel}
      </span>

      <span className="ml-auto flex flex-wrap items-center gap-2">
        {checkedIn ? (
          <span className="inline-flex items-center gap-1.5 ui-edge border-line bg-seat-checked-in px-2.5 py-1 text-xs font-bold ui-label text-white">
            <FontAwesomeIcon icon={faCircleCheck} className="h-3 w-3" aria-hidden="true" />
            Checked in
          </span>
        ) : (
          <span
            className={`inline-flex items-center gap-1.5 ui-edge px-2.5 py-1 text-xs font-bold ui-label ${
              notice
                ? 'border-danger bg-danger text-white'
                : 'border-ink/30 text-ink/60'
            }`}
          >
            <FontAwesomeIcon icon={meta.icon} className="h-3 w-3" aria-hidden="true" />
            {meta.label}
          </span>
        )}

        {canCheckIn && (
          <button
            type="button"
            onClick={onCheckIn}
            disabled={busy}
            className="ui-control ui-edge border-line bg-selected px-3 py-1.5 text-xs font-bold ui-label text-ink shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:opacity-40 disabled:hover:translate-y-0"
          >
            {busy ? 'Working…' : "I'm here"}
          </button>
        )}

        {active && (
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            aria-label={`Cancel your booking of ${booking.seatLabel} on ${booking.bookingDate}`}
            className="ui-control inline-flex items-center gap-1.5 ui-edge border-line px-3 py-1.5 text-xs font-bold ui-label text-ink hover:bg-danger hover:text-white disabled:opacity-40"
          >
            <FontAwesomeIcon icon={faXmark} className="h-3 w-3" aria-hidden="true" />
            Cancel
          </button>
        )}
      </span>

      {/* The whole point of PLAN.md §5 #12 and #13's "users notified": the desk is gone and the
          person did not do it, so the row says why in words rather than leaving them to decode a
          status name. */}
      {meta.explanation && (
        <p
          className={`basis-full text-xs font-semibold leading-snug ${
            notice ? 'text-ink' : 'text-ink/55'
          }`}
        >
          {meta.explanation}
        </p>
      )}
    </li>
  );
}

export function MyBookingsPage() {
  return (
    <div>
      <div className="mb-6">
        <p className="eyebrow text-xs text-ink/50">Your desks</p>
        <h1 className="ui-title text-ink">
          My bookings
        </h1>
        <p className="mt-1 text-sm font-semibold text-ink/60">
          Check in on the day you come in — an un-checked-in desk goes back to the pool at the
          morning cut-off.
        </p>
      </div>
      <SectionErrorBoundary
        title="Your bookings hit a snag"
        message="Something failed while rendering this list — try reloading this section."
      >
        <BookingsList />
      </SectionErrorBoundary>
    </div>
  );
}
