import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCube,
  faLayerGroup,
  faRightLeft,
  faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import type { SeatTileModel } from '../../lib/seatModel';
import { SEAT_STATE_META } from '../../lib/seatState';
import type { ClashingBooking } from '../../hooks/useClaimSeat';

interface FloorSidebarProps {
  floors: string[];
  activeFloor: string | null;
  selectedSeat: SeatTileModel | null;
  onBook: () => void;
  onView3D: () => void;
  isBooking: boolean;
  bookError: string | null;
  /** Set when the last claim was refused because you already hold a desk that day. */
  moveOffer: ClashingBooking | null;
  onMove: () => void;
  isMoving: boolean;
}

/** The left rail: which floor, which seat is picked, and the one primary action. */
export function FloorSidebar({
  floors,
  activeFloor,
  selectedSeat,
  onBook,
  onView3D,
  isBooking,
  bookError,
  moveOffer,
  onMove,
  isMoving,
}: FloorSidebarProps) {
  const busy = isBooking || isMoving;
  const canBook = Boolean(selectedSeat?.actionable) && !busy;

  return (
    <aside className="flex flex-col gap-4">
      <div className="ui-edge border-line bg-paper p-4 shadow-[var(--dd-shadow)]">
        <p className="eyebrow text-[11px] text-ink/60">Floor select</p>
        <p className="mb-3 font-mono text-[10px] ui-label text-ink/40">
          Select active level
        </p>
        <ul className="flex flex-col gap-1.5">
          {floors.map((floor) => {
            const active = floor === activeFloor;
            return (
              <li key={floor}>
                <div
                  aria-current={active ? 'true' : undefined}
                  className={`flex items-center gap-2 ui-edge px-3 py-2 text-sm font-semibold ui-label ${
                    active
                      ? 'border-ink bg-selected text-ink shadow-[var(--dd-shadow-sm)]'
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

      <div className="ui-edge border-line bg-paper p-4 shadow-[var(--dd-shadow)]">
        <p className="eyebrow text-[11px] text-ink/60">Selected seat</p>
        <div className="mt-2 ui-edge border-dashed border-ink/40 px-3 py-3">
          {selectedSeat ? (
            <>
              <p className="font-mono text-lg font-bold ui-label text-ink">
                {selectedSeat.seatLabel}
              </p>
              <p className="text-xs font-semibold ui-label text-ink/55">
                {SEAT_STATE_META[selectedSeat.actionable ? 'SELECTED' : selectedSeat.displayState].label}
              </p>
            </>
          ) : (
            <p className="font-mono text-lg font-bold ui-label text-ink/35">None</p>
          )}
        </div>

        <button
          type="button"
          onClick={onView3D}
          disabled={!selectedSeat}
          title={selectedSeat ? undefined : 'Pick a seat on the map first'}
          className="mt-3 flex w-full items-center justify-center gap-2 ui-edge border-line bg-paper px-4 py-2.5 text-xs font-bold ui-label text-ink shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
        >
          <FontAwesomeIcon icon={faCube} className="h-3.5 w-3.5" aria-hidden="true" />
          View in 3D
        </button>

        <button
          type="button"
          onClick={onBook}
          disabled={!canBook}
          className="mt-3 flex w-full items-center justify-center gap-2 ui-edge border-action bg-action px-4 py-3 text-sm font-bold ui-label text-white shadow-[var(--dd-shadow)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
        >
          {isBooking ? 'Booking…' : 'Book now'}
        </button>

        {/* PLAN.md §5 #3. The refusal already knows which desk is in the way, so the only useful
            thing to do with it is offer the swap rather than leave somebody to work out that they
            must cancel first — which is also the version that could lose them both desks. */}
        {moveOffer && selectedSeat && (
          <div className="mt-3 ui-edge border-line bg-seat-pending-tint p-3">
            <p className="text-xs font-semibold text-ink">
              You already have{' '}
              <span className="font-mono font-bold uppercase">{moveOffer.seatLabel}</span> that day.
            </p>
            <button
              type="button"
              onClick={onMove}
              disabled={busy}
              className="mt-2 flex w-full items-center justify-center gap-2 ui-edge border-line bg-selected px-3 py-2 text-xs font-bold ui-label text-ink shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
            >
              <FontAwesomeIcon icon={faRightLeft} className="h-3 w-3" aria-hidden="true" />
              {isMoving ? 'Moving…' : `Move to ${selectedSeat.seatLabel}`}
            </button>
            <p className="mt-1.5 text-[11px] font-semibold leading-snug text-ink/60">
              {moveOffer.seatLabel} goes back to the pool only if the move succeeds.
            </p>
          </div>
        )}

        {bookError && (
          <p role="alert" className="mt-2 flex items-start gap-1.5 text-xs font-semibold text-danger">
            <FontAwesomeIcon icon={faTriangleExclamation} className="mt-0.5 h-3 w-3" aria-hidden="true" />
            {bookError}
          </p>
        )}
      </div>
    </aside>
  );
}
