import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faUsers } from '@fortawesome/free-solid-svg-icons';

/**
 * Proves role-gated routing works end to end (nav link + route only reach
 * MANAGER/ADMIN — see RequireRole). The actual reservation-management UI
 * is Phase 7's job.
 */
export function ReservationsPage() {
  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">Reservations</h1>
        <p className="text-sm text-slate-500">
          Hold seats for your team ahead of a busy day.
        </p>
      </div>
      <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
        <FontAwesomeIcon
          icon={faUsers}
          className="h-8 w-8 text-slate-400"
          aria-hidden="true"
        />
        <p className="font-medium text-slate-700">Coming in Phase 7</p>
        <p className="max-w-md text-sm text-slate-500">
          Team reservation management — holding blocks of seats, seeing
          partial-success reports, and releasing holds early — lands in a later
          phase.
        </p>
      </div>
    </div>
  );
}
