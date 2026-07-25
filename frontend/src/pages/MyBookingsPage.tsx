import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCalendarDay,
  faChair,
  faCircleCheck,
} from '@fortawesome/free-solid-svg-icons';
import { useMyBookings } from '../hooks/useMyBookings';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

function BookingsSkeleton() {
  return (
    <ul
      className="flex flex-col gap-2"
      aria-hidden="true"
      data-testid="bookings-skeleton"
    >
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="skeleton-shimmer relative flex items-center gap-3 overflow-hidden rounded-lg border border-slate-200 bg-white p-4"
        >
          <div className="h-4 w-4 rounded-full bg-slate-200" />
          <div className="h-4 w-24 rounded bg-slate-200" />
          <div className="h-4 w-40 rounded bg-slate-100" />
        </li>
      ))}
    </ul>
  );
}

function BookingsList() {
  const { data, isPending, isError, error, refetch } = useMyBookings();

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
      <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-slate-500">
        You don't have any upcoming bookings. Head to the seat map to claim a
        desk.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {data.map((booking) => (
        <li
          key={booking.id}
          className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
        >
          <FontAwesomeIcon
            icon={faCalendarDay}
            className="h-4 w-4 text-slate-400"
            aria-hidden="true"
          />
          <span className="font-medium text-slate-800">
            {booking.bookingDate}
          </span>
          <span className="inline-flex items-center gap-1.5 font-mono text-sm text-slate-600">
            <FontAwesomeIcon
              icon={faChair}
              className="h-3.5 w-3.5 text-slate-400"
              aria-hidden="true"
            />
            {booking.seatLabel}
          </span>
          <span className="ml-auto inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
            {booking.status === 'ACTIVE' && booking.checkedInAt ? (
              <>
                <FontAwesomeIcon
                  icon={faCircleCheck}
                  className="h-3 w-3 text-teal-600"
                  aria-hidden="true"
                />
                Checked in
              </>
            ) : (
              booking.status
            )}
          </span>
        </li>
      ))}
    </ul>
  );
}

export function MyBookingsPage() {
  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">My bookings</h1>
        <p className="text-sm text-slate-500">
          Your claimed seats from today through the booking horizon.
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
