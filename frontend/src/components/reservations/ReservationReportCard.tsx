import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleCheck, faUserLock } from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';

type ReservationReport = components['schemas']['ReservationReport'];

/**
 * The outcome of a hold, told honestly.
 *
 * A block where some desks were already taken is a *normal* result, not a failure — the system
 * never cancels somebody's booking to make room for a manager. So this reads as an outcome
 * report, not an error: what was held, and for each desk that wasn't, who already has it and on
 * which day, so the manager can go and ask them rather than guess.
 */
export function ReservationReportCard({ report }: { report: ReservationReport }) {
  const held = report.held ?? [];
  const unavailable = report.unavailable ?? [];
  const total = held.length + unavailable.length;

  return (
    <section
      aria-label="Result of the last hold"
      className="ui-edge border-line bg-paper p-4 shadow-[var(--dd-shadow)]"
    >
      <p className="eyebrow text-[11px] text-ink/60">Result</p>
      <h2 className="text-lg font-bold ui-label text-ink">
        {held.length} of {total} held for {report.teamName}
      </h2>
      <p className="mb-3 font-mono text-[11px] ui-label text-ink/50">
        {report.startDate} → {report.endDate}
      </p>

      {held.length > 0 && (
        <div className="mb-3">
          <p className="mb-1.5 flex items-center gap-1.5 text-xs font-bold ui-label text-ink">
            <FontAwesomeIcon
              icon={faCircleCheck}
              className="h-3.5 w-3.5 text-seat-checked-in"
              aria-hidden="true"
            />
            Held
          </p>
          <ul className="flex flex-wrap gap-1.5">
            {held.map((seat) => (
              <li
                key={seat.reservationId}
                className="ui-edge border-line bg-seat-team-reserved px-2 py-1 font-mono text-xs font-bold ui-label text-ink"
              >
                {seat.seatLabel}
              </li>
            ))}
          </ul>
        </div>
      )}

      {unavailable.length > 0 && (
        <div>
          <p className="mb-1.5 flex items-center gap-1.5 text-xs font-bold ui-label text-ink">
            <FontAwesomeIcon
              icon={faUserLock}
              className="h-3.5 w-3.5 text-danger"
              aria-hidden="true"
            />
            Left alone — already taken
          </p>
          <ul className="flex flex-col gap-1">
            {unavailable.map((seat) => (
              <li
                key={seat.seatId}
                className="flex flex-wrap items-baseline gap-x-2 ui-edge border-dashed border-ink/40 px-2.5 py-1.5 text-sm"
              >
                <span className="font-mono text-xs font-bold ui-label text-ink">
                  {seat.seatLabel}
                </span>
                <span className="font-semibold text-ink/75">
                  {seat.conflictingUserDisplayName}
                </span>
                <span className="font-mono text-[11px] ui-label text-ink/50">
                  from {seat.conflictingDate}
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-[11px] font-semibold text-ink/55">
            Their bookings were not touched. Ask them to move if you need these desks.
          </p>
        </div>
      )}
    </section>
  );
}
