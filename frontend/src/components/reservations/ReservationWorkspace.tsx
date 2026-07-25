import { useCallback, useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChevronDown,
  faLockOpen,
  faTriangleExclamation,
  faUsers,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import type { SeatTileModel } from '../../lib/seatModel';
import { teamTint } from '../../lib/teamColors';
import {
  useCreateReservation,
  useReleaseReservation,
  useReservations,
  useReservationTeams,
} from '../../hooks/useReservations';
import { FloorMap } from '../floormap/FloorMap';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type ReservationReport = components['schemas']['ReservationReport'];

/**
 * Holding a block of desks for a team.
 *
 * Seats are picked on the same floor map people book on — the map is the product, so a manager
 * blocking out a row does it by pointing at the row, not by typing seat codes. Occupied desks
 * stay clickable on purpose: the API reports them back as unavailable with the name of whoever
 * holds them, which is more useful than a map that silently refuses the click.
 */
export function ReservationWorkspace({
  seatMap,
  currentUserId,
  onReport,
}: {
  seatMap: SeatMapResponse;
  currentUserId: number | null;
  onReport: (report: ReservationReport) => void;
}) {
  const today = seatMap.date ?? '';
  const teams = useReservationTeams();
  const holds = useReservations();
  const create = useCreateReservation();
  const release = useReleaseReservation();

  const [picked, setPicked] = useState<Map<number, string>>(new Map());
  const [teamId, setTeamId] = useState<number | null>(null);
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [formError, setFormError] = useState<string | null>(null);

  const pickedIds = useMemo(() => new Set(picked.keys()), [picked]);

  const togglePick = useCallback((seat: SeatTileModel) => {
    setFormError(null);
    setPicked((current) => {
      const next = new Map(current);
      if (next.has(seat.seatId)) next.delete(seat.seatId);
      else next.set(seat.seatId, seat.seatLabel);
      return next;
    });
  }, []);

  // Anything but a broken desk can be picked: a desk somebody has booked is exactly the case the
  // partial-success report exists to explain.
  const canSelect = useCallback((seat: SeatTileModel) => seat.displayState !== 'DISABLED', []);

  const effectiveTeamId = teamId ?? teams.data?.[0]?.id ?? null;
  const canSubmit =
    picked.size > 0 && effectiveTeamId != null && startDate !== '' && endDate !== '' && !create.isPending;

  const submit = () => {
    if (effectiveTeamId == null) {
      setFormError('Pick a team to hold these desks for.');
      return;
    }
    if (endDate < startDate) {
      setFormError('The end date cannot be before the start date.');
      return;
    }
    setFormError(null);
    create.mutate(
      { teamId: effectiveTeamId, seatIds: [...picked.keys()], startDate, endDate },
      {
        onSuccess: (report) => {
          onReport(report);
          setPicked(new Map());
        },
        onError: (e) =>
          setFormError(e instanceof Error ? e.message : 'Could not hold those seats.'),
      },
    );
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
      <div className="order-2 flex flex-col gap-4 lg:order-1">
        <div className="border-2 border-ink bg-paper p-4 shadow-brutal">
          <p className="eyebrow text-[11px] text-ink/60">Hold desks</p>

          <label className="mt-3 block text-xs font-bold uppercase tracking-wider text-ink">
            Team
            {/*
              `appearance-none` strips the OS control so the field matches every other bordered
              box on the page; the chevron is drawn back in by the wrapper, and `pr-8` keeps the
              team name from running underneath it.
            */}
            <span className="relative mt-1 block">
              <select
                value={effectiveTeamId ?? ''}
                onChange={(e) => setTeamId(Number(e.target.value))}
                disabled={teams.isPending || (teams.data?.length ?? 0) === 0}
                className="w-full appearance-none border-2 border-ink bg-white py-2 pl-2 pr-8 text-sm font-semibold text-ink disabled:opacity-50"
              >
                {(teams.data ?? []).map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
              <FontAwesomeIcon
                icon={faChevronDown}
                aria-hidden="true"
                className="pointer-events-none absolute right-2.5 top-1/2 h-3 w-3 -translate-y-1/2 text-ink"
              />
            </span>
          </label>

          {teams.data?.length === 0 && (
            <p className="mt-2 text-xs font-semibold text-ink/55">
              You don&rsquo;t manage any teams yet, so there is nobody to hold desks for.
            </p>
          )}

          <div className="mt-3 grid grid-cols-2 gap-2">
            <label className="block text-xs font-bold uppercase tracking-wider text-ink">
              From
              <input
                type="date"
                value={startDate}
                min={today}
                onChange={(e) => setStartDate(e.target.value)}
                className="mt-1 w-full border-2 border-ink bg-white px-2 py-1.5 font-mono text-xs font-semibold text-ink"
              />
            </label>
            <label className="block text-xs font-bold uppercase tracking-wider text-ink">
              To
              <input
                type="date"
                value={endDate}
                min={startDate || today}
                onChange={(e) => setEndDate(e.target.value)}
                className="mt-1 w-full border-2 border-ink bg-white px-2 py-1.5 font-mono text-xs font-semibold text-ink"
              />
            </label>
          </div>

          <div className="mt-3 border-2 border-dashed border-ink/40 px-3 py-2">
            <p className="eyebrow text-[10px] text-ink/50">Picked desks</p>
            {picked.size === 0 ? (
              <p className="font-mono text-sm font-bold uppercase tracking-wider text-ink/35">
                None — tap desks on the map
              </p>
            ) : (
              <ul className="mt-1 flex flex-wrap gap-1">
                {[...picked.entries()].map(([id, label]) => (
                  <li key={id}>
                    <button
                      type="button"
                      onClick={() => setPicked((c) => {
                        const next = new Map(c);
                        next.delete(id);
                        return next;
                      })}
                      aria-label={`Remove ${label} from the block`}
                      className="flex items-center gap-1 border-2 border-ink bg-bauhaus-yellow px-1.5 py-0.5 font-mono text-[11px] font-bold uppercase tracking-wider text-ink"
                    >
                      {label}
                      <FontAwesomeIcon icon={faXmark} className="h-2.5 w-2.5" aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <button
            type="button"
            onClick={submit}
            disabled={!canSubmit}
            className="mt-3 flex w-full items-center justify-center gap-2 border-2 border-ink bg-bauhaus-red px-4 py-3 text-sm font-bold uppercase tracking-wider text-white shadow-brutal transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
          >
            <FontAwesomeIcon icon={faUsers} className="h-4 w-4" aria-hidden="true" />
            {create.isPending ? 'Holding…' : `Hold ${picked.size || ''} desk${picked.size === 1 ? '' : 's'}`}
          </button>

          {formError && (
            <p role="alert" className="mt-2 flex items-start gap-1.5 text-xs font-semibold text-bauhaus-red">
              <FontAwesomeIcon
                icon={faTriangleExclamation}
                className="mt-0.5 h-3 w-3"
                aria-hidden="true"
              />
              {formError}
            </p>
          )}
        </div>

        <CurrentHolds
          holds={holds.data ?? []}
          isPending={holds.isPending}
          releasingId={release.isPending ? release.variables : null}
          onRelease={(id) => release.mutate(id)}
        />
      </div>

      <div className="order-1 lg:order-2">
        <FloorMap
          seatMap={seatMap}
          currentUserId={currentUserId}
          selectedSeatIds={pickedIds}
          onSelectSeat={togglePick}
          canSelect={canSelect}
          pendingSeatId={null}
          animatingSeat={null}
        />
      </div>
    </div>
  );
}

type ReservationView = components['schemas']['ReservationView'];

function CurrentHolds({
  holds,
  isPending,
  releasingId,
  onRelease,
}: {
  holds: ReservationView[];
  isPending: boolean;
  releasingId: number | null | undefined;
  onRelease: (id: number) => void;
}) {
  return (
    <div className="border-2 border-ink bg-paper p-4 shadow-brutal">
      <p className="eyebrow text-[11px] text-ink/60">Current holds</p>
      <p className="font-mono text-[10px] uppercase tracking-wider text-ink/40">
        {isPending ? 'Loading…' : `${holds.length} live or upcoming`}
      </p>
      <p className="mb-2 text-[11px] font-semibold text-ink/55">
        A block holds its desks until the morning cut-off, then releases so nobody is locked out of
        an empty office.
      </p>

      {!isPending && holds.length === 0 && (
        <p className="text-xs font-semibold text-ink/55">
          No desks are held right now.
        </p>
      )}

      <ul className="flex flex-col gap-1.5">
        {holds.map((hold) => (
          <li
            key={hold.id}
            className={`flex items-center gap-2 border-2 border-ink px-2.5 py-1.5 ${
              hold.enforcedNow ? '' : 'opacity-60'
            }`}
            style={{ background: teamTint(hold.teamId) }}
          >
            <span className="min-w-0 flex-1">
              <span className="block font-mono text-xs font-bold uppercase tracking-wider text-ink">
                {hold.seatLabel} · {hold.teamName}
              </span>
              <span className="block font-mono text-[10px] uppercase tracking-wider text-ink/65">
                {hold.startDate} → {hold.endDate}
              </span>
              {/*
                The answer to "I held these desks and the map shows nothing". A block releases
                softly at its cut-off — the row survives, the hold stops. Saying which of the two
                states it is in turns a apparent bug into the rule it actually is.
              */}
              {hold.enforcedNow ? (
                <span className="block font-mono text-[10px] font-bold uppercase tracking-wider text-ink">
                  Holding · frees {hold.releaseAtTime?.slice(0, 5)}
                </span>
              ) : (
                <span className="flex items-center gap-1 font-mono text-[10px] font-bold uppercase tracking-wider text-ink">
                  <FontAwesomeIcon icon={faLockOpen} className="h-2.5 w-2.5" aria-hidden="true" />
                  Released at {hold.releaseAtTime?.slice(0, 5)} — anyone may sit here
                </span>
              )}
            </span>
            <button
              type="button"
              onClick={() => onRelease(hold.id ?? -1)}
              disabled={releasingId === hold.id}
              aria-label={`Release ${hold.seatLabel} from ${hold.teamName}`}
              className="shrink-0 border-2 border-ink p-1 text-ink hover:bg-ink hover:text-paper disabled:opacity-40"
            >
              <FontAwesomeIcon icon={faXmark} className="h-3 w-3" aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
