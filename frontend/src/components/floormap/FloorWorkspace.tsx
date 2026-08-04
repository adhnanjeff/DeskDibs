import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMap, faList, type IconDefinition } from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import { useClaimSeat, useMoveSeat, ClaimError, type ClashingBooking } from '../../hooks/useClaimSeat';
import { useSeatMapLive } from '../../hooks/useSeatMapLive';
import type { SeatAnimation, SeatTileModel } from '../../lib/seatModel';
import { FloorMap } from './FloorMap';
import { FloorSidebar } from './FloorSidebar';
import { FloorLegend } from './FloorLegend';
import { SeatListFallback } from './SeatListFallback';
import { Floor3DOverlay } from '../floor3d/Floor3DOverlay';
import { preloadFloor3D } from '../floor3d/lazyScene';
import { OfficeOverview } from './OfficeOverview';
import { TodayCheckIn } from './TodayCheckIn';
import { FindColleague } from './FindColleague';
import { summariseOffice } from '../../lib/officeStats';
import { indexColleagues, type Colleague } from '../../lib/colleagueSearch';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type SeatMapSeat = components['schemas']['SeatMapSeat'];

interface FloorWorkspaceProps {
  seatMap: SeatMapResponse;
  currentUserId: number | null;
  /** Reports whether live broadcasts are arriving, so the page can fall back to polling. */
  onLiveStatusChange?: (connected: boolean) => void;
}

function countSeats(map: SeatMapResponse): number {
  let total = 0;
  for (const floor of map.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        total += (table.seats ?? []).length;
      }
    }
  }
  return total;
}

/**
 * The seat-map surface: the Bauhaus title + legend, the sidebar (floor, picked
 * seat, Book Now), and the floor map / list. Owns the selection, the claim, the
 * win/lose motion, and the live-update announcements.
 */
export function FloorWorkspace({
  seatMap,
  currentUserId,
  onLiveStatusChange,
}: FloorWorkspaceProps) {
  const date = seatMap.date ?? '';
  const floors = (seatMap.floors ?? []).map((floor) => floor.name ?? '').filter(Boolean);
  const activeFloor = floors[0] ?? null;
  const seatCount = useMemo(() => countSeats(seatMap), [seatMap]);
  const stats = useMemo(() => summariseOffice(seatMap), [seatMap]);
  const colleagues = useMemo(() => indexColleagues(seatMap), [seatMap]);

  const [selectedSeat, setSelectedSeat] = useState<SeatTileModel | null>(null);
  const [pendingSeatId, setPendingSeatId] = useState<number | null>(null);
  const [animatingSeat, setAnimatingSeat] = useState<{ seatId: number; kind: SeatAnimation } | null>(
    null,
  );
  const [view, setView] = useState<'map' | 'list'>('map');
  const [show3D, setShow3D] = useState(false);
  const [located, setLocated] = useState<Colleague | null>(null);

  // Booking picks exactly one seat; the map speaks in sets so a team hold can pick a block.
  const selectedSeatIds = useMemo(
    () => new Set(selectedSeat ? [selectedSeat.seatId] : []),
    [selectedSeat],
  );
  const [announcement, setAnnouncement] = useState('');
  const [bookError, setBookError] = useState<string | null>(null);

  const pendingRef = useRef<number | null>(null);
  const animationTimer = useRef<number | null>(null);
  const claim = useClaimSeat(date);
  const move = useMoveSeat(date);

  // The desk you already hold on this date, learned from a refused claim. Kept while the date
  // stays put, so a second pick can be offered as a move straight away rather than making you
  // trip over the same 409 again.
  //
  // Stored *with* the date it was learned on and derived back out, rather than cleared by an
  // effect when the date changes: an offer to give up Tuesday's desk is meaningless the moment
  // you are looking at Wednesday, and deriving it makes that impossible to get wrong.
  const [offerFor, setOfferFor] = useState<{ date: string; clash: ClashingBooking } | null>(null);
  const moveOffer = offerFor?.date === date ? offerFor.clash : null;

  const rememberMoveOffer = useCallback(
    (clash: ClashingBooking | null) => setOfferFor(clash ? { date, clash } : null),
    [date],
  );

  const flash = useCallback((seatId: number, kind: SeatAnimation) => {
    setAnimatingSeat({ seatId, kind });
    if (animationTimer.current) window.clearTimeout(animationTimer.current);
    animationTimer.current = window.setTimeout(() => {
      setAnimatingSeat((current) => (current?.seatId === seatId ? null : current));
    }, 700);
  }, []);

  const handleLiveChange = useCallback(
    (seat: SeatMapSeat) => {
      if (seat.seatId == null) return;
      const stateWord = (seat.state ?? 'updated').toLowerCase();
      setAnnouncement(`Seat ${seat.seatLabel ?? ''} is now ${stateWord}.`);
      if (seat.seatId !== pendingRef.current) flash(seat.seatId, 'updated');
    },
    [flash],
  );

  const live = useSeatMapLive(date, { onSeatChange: handleLiveChange });

  useEffect(() => {
    onLiveStatusChange?.(live.connected);
  }, [live.connected, onLiveStatusChange]);

  const handleSelect = useCallback((seat: SeatTileModel) => {
    if (!seat.actionable) return;
    setBookError(null);
    setSelectedSeat(seat);
    // Picking a seat is the only route to the 3D view, so start fetching its
    // chunk now — the download overlaps the pause before the user clicks.
    preloadFloor3D();
  }, []);

  const setPending = useCallback((seatId: number | null) => {
    pendingRef.current = seatId;
    setPendingSeatId(seatId);
  }, []);

  /** Shared by claiming and moving: the desk is now yours, so celebrate it the same way. */
  const onSeatWon = useCallback(
    (seatId: number, announcement: string) => {
      setPending(null);
      setSelectedSeat(null);
      rememberMoveOffer(null);
      flash(seatId, 'claimed');
      setAnnouncement(announcement);
    },
    [flash, setPending, rememberMoveOffer],
  );

  /**
   * Shared by claiming and moving. Not every 409 is a lost race: the one that says you already
   * hold a desk that day is an offer to swap, and telling somebody "taken by someone else" when
   * the seat is sitting there empty is worse than saying nothing.
   */
  const onSeatLost = useCallback(
    (err: unknown, seatId: number, label: string, fallback: string) => {
      setPending(null);
      const claimError = err instanceof ClaimError ? err : null;

      if (claimError?.clashesWith) {
        rememberMoveOffer(claimError.clashesWith);
        setAnnouncement(
          `You already have seat ${claimError.clashesWith.seatLabel} that day. You can move to ${label} instead.`,
        );
        return;
      }
      if (claimError?.conflict) {
        setSelectedSeat(null);
        flash(seatId, 'lost');
        setAnnouncement(`Seat ${label} was just taken by someone else.`);
        return;
      }
      const message = err instanceof Error ? err.message : fallback;
      setBookError(message);
      setAnnouncement(message);
    },
    [flash, setPending, rememberMoveOffer],
  );

  const handleBook = useCallback(() => {
    if (!selectedSeat?.actionable) return;
    if (!date) {
      setBookError('No booking date is available for this floor.');
      return;
    }
    const seatId = selectedSeat.seatId;
    const label = selectedSeat.seatLabel;
    setPending(seatId);
    setBookError(null);
    claim.mutate(seatId, {
      onSuccess: () => onSeatWon(seatId, `Seat ${label} booked. It's yours for the day.`),
      onError: (err) => onSeatLost(err, seatId, label, 'Could not book that seat.'),
    });
  }, [selectedSeat, date, claim, onSeatWon, onSeatLost, setPending]);

  /** PLAN.md §5 #3 — give up the desk you hold that day and take this one, atomically. */
  const handleMove = useCallback(() => {
    if (!selectedSeat?.actionable || !moveOffer) return;
    const seatId = selectedSeat.seatId;
    const label = selectedSeat.seatLabel;
    const from = moveOffer.seatLabel;
    setPending(seatId);
    setBookError(null);
    move.mutate(seatId, {
      onSuccess: () => onSeatWon(seatId, `Moved from seat ${from} to seat ${label}.`),
      onError: (err) => onSeatLost(err, seatId, label, 'Could not move you to that seat.'),
    });
  }, [selectedSeat, moveOffer, move, onSeatWon, onSeatLost, setPending]);

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow text-xs text-ink/50">Floor plan</p>
          <h1 className="ui-title text-ink">
            {activeFloor ?? 'Floor'}
          </h1>
        </div>
        <FloorLegend />
      </div>

      {/* Above the numbers, because it is the one thing on this page with a deadline. */}
      <TodayCheckIn officeToday={date} />

      <OfficeOverview stats={stats} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="ui-edge border-line bg-ink px-2.5 py-1 font-mono text-[10px] font-bold ui-label text-paper">
          Finalized layout
        </span>
        <span className="ui-edge border-line bg-selected px-2.5 py-1 font-mono text-[10px] font-bold ui-label text-ink">
          {seatCount} workstations
        </span>
        <div className="ml-auto flex ui-edge border-line shadow-[var(--dd-shadow-sm)]">
          <ViewToggleButton active={view === 'map'} onClick={() => setView('map')} icon={faMap} label="Map" />
          <ViewToggleButton active={view === 'list'} onClick={() => setView('list')} icon={faList} label="List" />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[248px_1fr]">
        <div className="order-2 flex flex-col gap-4 lg:order-1">
          <FindColleague
            people={colleagues}
            located={located}
            onLocate={(person) => {
              setLocated(person);
              setAnnouncement(`${person.name} is at seat ${person.seatLabel}. Showing it on the map.`);
            }}
            onClear={() => setLocated(null)}
          />
          <FloorSidebar
            floors={floors}
            activeFloor={activeFloor}
            selectedSeat={selectedSeat}
            onBook={handleBook}
            onView3D={() => setShow3D(true)}
            isBooking={claim.isPending}
            bookError={bookError}
            moveOffer={moveOffer}
            onMove={handleMove}
            isMoving={move.isPending}
          />
        </div>
        <div className="order-1 lg:order-2">
          {view === 'map' ? (
            <FloorMap
              seatMap={seatMap}
              currentUserId={currentUserId}
              selectedSeatIds={selectedSeatIds}
              onSelectSeat={handleSelect}
              pendingSeatId={pendingSeatId}
              animatingSeat={animatingSeat}
              locatedSeatId={located?.seatId ?? null}
            />
          ) : (
            <SeatListFallback
              seatMap={seatMap}
              currentUserId={currentUserId}
              selectedSeatId={selectedSeat?.seatId ?? null}
              onSelectSeat={handleSelect}
              pendingSeatId={pendingSeatId}
              animatingSeat={animatingSeat}
            />
          )}
        </div>
      </div>

      {show3D && (
        <Floor3DOverlay
          seatMap={seatMap}
          currentUserId={currentUserId}
          pendingSeatId={pendingSeatId}
          selectedSeat={selectedSeat}
          onSelectSeat={handleSelect}
          onBook={handleBook}
          isBooking={claim.isPending || move.isPending}
          bookError={bookError}
          onClose={() => setShow3D(false)}
        />
      )}

      {/* `role="status"` as well as aria-live: the role is what actually gives this an accessible
          identity, and without it assistive tech (and anything else querying by role) sees an
          anonymous div that merely happens to announce. */}
      <div role="status" aria-live="polite" className="sr-only">
        {announcement}
      </div>
    </motion.div>
  );
}

function ViewToggleButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: IconDefinition;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold ui-label ${
        active ? 'bg-ink text-paper' : 'bg-paper text-ink'
      }`}
    >
      <FontAwesomeIcon icon={icon} className="h-3 w-3" aria-hidden="true" />
      {label}
    </button>
  );
}
