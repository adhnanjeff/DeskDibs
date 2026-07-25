import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowsUpDownLeftRight,
  faCube,
  faTriangleExclamation,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import { isWebGLAvailable } from '../../lib/floor3d';
import { SEAT_STATE_META } from '../../lib/seatState';
import type { SeatTileModel } from '../../lib/seatModel';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';
import { LazyFloor3DScene } from './lazyScene';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

interface Floor3DOverlayProps {
  seatMap: SeatMapResponse;
  currentUserId: number | null;
  pendingSeatId: number | null;
  selectedSeat: SeatTileModel | null;
  onSelectSeat: (seat: SeatTileModel) => void;
  onBook: () => void;
  isBooking: boolean;
  bookError: string | null;
  onClose: () => void;
}

/**
 * The 3D floor, full-screen. Opened from a picked seat: the camera flies to that
 * desk so you can see where it actually sits in the office, then you can orbit
 * the whole model, pick a different seat, and book without going back.
 */
export function Floor3DOverlay({
  seatMap,
  currentUserId,
  pendingSeatId,
  selectedSeat,
  onSelectSeat,
  onBook,
  isBooking,
  bookError,
  onClose,
}: Floor3DOverlayProps) {
  const reducedMotion = usePrefersReducedMotion();
  const [hovered, setHovered] = useState<SeatTileModel | null>(null);
  // Probed before mounting the canvas, and re-probed on retry: a GPU process
  // that failed once often recovers.
  const [webglOk, setWebglOk] = useState(isWebGLAvailable);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    // The scene owns the whole viewport; the page behind it must not scroll.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [onClose]);

  const handleSelect = useCallback(
    (seat: SeatTileModel) => {
      if (!seat.actionable) return;
      onSelectSeat(seat);
    },
    [onSelectSeat],
  );

  const shown = hovered ?? selectedSeat;
  const canBook = Boolean(selectedSeat?.actionable) && !isBooking;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Office floor plan in 3D"
      className="fixed inset-0 z-50 flex flex-col bg-[#171614]"
    >
      <header className="flex flex-wrap items-center gap-3 border-b-2 border-paper/25 px-4 py-3">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center bg-bauhaus-yellow text-ink">
          <FontAwesomeIcon icon={faCube} className="h-4 w-4" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="eyebrow text-[11px] text-paper/55">3D floor view</p>
          <h2 className="truncate text-lg font-bold uppercase tracking-tight text-paper">
            {seatMap.floors?.[0]?.name ?? 'Floor'}
          </h2>
        </div>

        <p className="hidden items-center gap-2 font-mono text-[11px] uppercase tracking-wider text-paper/55 sm:flex">
          <FontAwesomeIcon icon={faArrowsUpDownLeftRight} className="h-3 w-3" aria-hidden="true" />
          Drag to orbit · scroll to zoom
        </p>

        <button
          ref={closeRef}
          type="button"
          onClick={onClose}
          className="ml-auto flex items-center gap-2 border-2 border-paper/40 px-3 py-2 text-xs font-bold uppercase tracking-wider text-paper hover:border-bauhaus-red hover:text-white"
        >
          <FontAwesomeIcon icon={faXmark} className="h-4 w-4" aria-hidden="true" />
          Close
        </button>
      </header>

      <div className="relative min-h-0 flex-1">
        {webglOk ? (
          <ErrorBoundary fallback={<SceneFallback kind="error" />}>
            <Suspense fallback={<SceneFallback kind="loading" />}>
              <LazyFloor3DScene
                seatMap={seatMap}
                currentUserId={currentUserId}
                pendingSeatId={pendingSeatId}
                selectedSeatId={selectedSeat?.seatId ?? null}
                reducedMotion={reducedMotion}
                onSelectSeat={handleSelect}
                onHoverSeat={setHovered}
              />
            </Suspense>
          </ErrorBoundary>
        ) : (
          <NoWebGL onRetry={() => setWebglOk(isWebGLAvailable())} />
        )}
      </div>

      <footer className="flex flex-wrap items-center gap-3 border-t-2 border-paper/25 px-4 py-3">
        <div className="min-w-[9rem] border-2 border-dashed border-paper/35 px-3 py-2">
          <p className="eyebrow text-[10px] text-paper/50">
            {hovered ? 'Hovering' : 'Selected seat'}
          </p>
          {shown ? (
            <>
              <p className="font-mono text-base font-bold uppercase tracking-wider text-paper">
                {shown.seatLabel}
              </p>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-paper/60">
                {shown.occupantName ??
                  SEAT_STATE_META[
                    shown.seatId === selectedSeat?.seatId && shown.actionable
                      ? 'SELECTED'
                      : shown.displayState
                  ].label}
              </p>
            </>
          ) : (
            <p className="font-mono text-base font-bold uppercase tracking-wider text-paper/35">
              None
            </p>
          )}
        </div>

        {bookError && (
          <p
            role="alert"
            className="flex items-start gap-1.5 text-xs font-semibold text-bauhaus-red"
          >
            <FontAwesomeIcon
              icon={faTriangleExclamation}
              className="mt-0.5 h-3 w-3"
              aria-hidden="true"
            />
            {bookError}
          </p>
        )}

        <button
          type="button"
          onClick={onBook}
          disabled={!canBook}
          className="ml-auto flex items-center justify-center gap-2 border-2 border-paper bg-bauhaus-red px-6 py-3 text-sm font-bold uppercase tracking-wider text-white transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:translate-y-0"
        >
          {isBooking ? 'Booking…' : 'Book now'}
        </button>
      </footer>
    </div>
  );
}

/**
 * WebGL is off or the GPU process failed. Say what happened and what fixes it —
 * a blank pane teaches the user nothing, and the 2D map still does the job.
 */
function NoWebGL({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex h-full items-center justify-center px-6">
      <div className="max-w-md border-2 border-paper/40 bg-[#1f1d1a] p-5 text-left">
        <p className="eyebrow mb-2 text-[11px] text-bauhaus-yellow">3D unavailable</p>
        <p className="text-sm font-semibold text-paper">
          Your browser could not start a WebGL context, so the model can&rsquo;t be drawn.
        </p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-[13px] text-paper/70">
          <li>
            Turn on hardware acceleration in the browser&rsquo;s system settings, then restart it.
          </li>
          <li>Check the browser&rsquo;s GPU status page for a blocked or disabled renderer.</li>
          <li>Reload this page — too many open 3D views can exhaust available contexts.</li>
        </ul>
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={onRetry}
            className="border-2 border-paper px-4 py-2 text-xs font-bold uppercase tracking-wider text-paper hover:bg-paper hover:text-ink"
          >
            Try again
          </button>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="border-2 border-paper/40 px-4 py-2 text-xs font-bold uppercase tracking-wider text-paper/70 hover:border-paper hover:text-paper"
          >
            Reload page
          </button>
        </div>
        <p className="mt-3 text-[11px] text-paper/45">
          The 2D floor map is fully functional — close this to go back to it.
        </p>
      </div>
    </div>
  );
}

function SceneFallback({ kind }: { kind: 'loading' | 'error' }) {
  return (
    <div className="flex h-full items-center justify-center px-6 text-center">
      <p className="font-mono text-xs uppercase tracking-wider text-paper/60">
        {kind === 'loading'
          ? 'Building the model…'
          : '3D view unavailable on this device. Close to return to the floor map.'}
      </p>
    </div>
  );
}
