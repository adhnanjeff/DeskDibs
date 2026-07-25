import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faLayerGroup, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import type { SeatTileModel } from '../../lib/seatModel';
import { SEAT_STATE_META } from '../../lib/seatState';

interface FloorSidebarProps {
  floors: string[];
  activeFloor: string | null;
  selectedSeat: SeatTileModel | null;
  onBook: () => void;
  isBooking: boolean;
  bookError: string | null;
}

/** The left rail: which floor, which seat is picked, and the one primary action. */
export function FloorSidebar({
  floors,
  activeFloor,
  selectedSeat,
  onBook,
  isBooking,
  bookError,
}: FloorSidebarProps) {
  const canBook = Boolean(selectedSeat?.actionable) && !isBooking;

  return (
    <aside className="flex flex-col gap-4">
      <div className="border-2 border-ink bg-paper p-4 shadow-brutal">
        <p className="eyebrow text-[11px] text-ink/60">Floor select</p>
        <p className="mb-3 font-mono text-[10px] uppercase tracking-wider text-ink/40">
          Select active level
        </p>
        <ul className="flex flex-col gap-1.5">
          {floors.map((floor) => {
            const active = floor === activeFloor;
            return (
              <li key={floor}>
                <div
                  aria-current={active ? 'true' : undefined}
                  className={`flex items-center gap-2 border-2 px-3 py-2 text-sm font-semibold uppercase tracking-wide ${
                    active
                      ? 'border-ink bg-bauhaus-yellow text-ink shadow-brutal-sm'
                      : 'border-transparent text-ink/55'
                  }`}
                >
                  <FontAwesomeIcon icon={faLayerGroup} className="h-3.5 w-3.5" aria-hidden="true" />
                  {floor}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      <div className="border-2 border-ink bg-paper p-4 shadow-brutal">
        <p className="eyebrow text-[11px] text-ink/60">Selected seat</p>
        <div className="mt-2 border-2 border-dashed border-ink/40 px-3 py-3">
          {selectedSeat ? (
            <>
              <p className="font-mono text-lg font-bold uppercase tracking-wider text-ink">
                {selectedSeat.seatLabel}
              </p>
              <p className="text-xs font-semibold uppercase tracking-wide text-ink/55">
                {SEAT_STATE_META[selectedSeat.actionable ? 'SELECTED' : selectedSeat.displayState].label}
              </p>
            </>
          ) : (
            <p className="font-mono text-lg font-bold uppercase tracking-wider text-ink/35">None</p>
          )}
        </div>

        <button
          type="button"
          disabled
          aria-disabled="true"
          className="mt-3 flex w-full items-center justify-center border-2 border-ink px-4 py-2.5 text-xs font-bold uppercase tracking-wider text-ink/45 opacity-80"
        >
          View in 3D
        </button>

        <button
          type="button"
          onClick={onBook}
          disabled={!canBook}
          className="mt-3 flex w-full items-center justify-center gap-2 border-2 border-ink bg-bauhaus-red px-4 py-3 text-sm font-bold uppercase tracking-wider text-white shadow-brutal transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
        >
          {isBooking ? 'Booking…' : 'Book now'}
        </button>

        {bookError && (
          <p role="alert" className="mt-2 flex items-start gap-1.5 text-xs font-semibold text-bauhaus-red">
            <FontAwesomeIcon icon={faTriangleExclamation} className="mt-0.5 h-3 w-3" aria-hidden="true" />
            {bookError}
          </p>
        )}
      </div>
    </aside>
  );
}
