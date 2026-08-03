import { useCallback, useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
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
import { FloorLegend } from '../floormap/FloorLegend';
import { DateField } from '../common/DateField';
import { SelectField } from '../common/SelectField';

/** An ISO date as a person would say it. Parsed at local midnight so the day never slips back one. */
function formatMapDate(iso: string): string {
  if (!iso) return '—';
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
}

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
  officeToday,
  startDate,
  endDate,
  onChangeRange,
  onReport,
}: {
  seatMap: SeatMapResponse;
  currentUserId: number | null;
  /** The office's own today, for the earliest date a block may start. Never the client's clock. */
  officeToday: string;
  startDate: string;
  endDate: string;
  /** Lifted to the page, which re-fetches the floor for the new start date. */
  onChangeRange: (from: string, to: string) => void;
  onReport: (report: ReservationReport) => void;
}) {
  // The day the floor below is actually showing, straight from the response rather than from the
  // requested date — while a new day is loading the previous map is still on screen, and labelling
  // it with the day the manager just typed would caption one floor with another floor's date.
  const mapDate = seatMap.date ?? '';
  const teams = useReservationTeams();
  const holds = useReservations();
  const create = useCreateReservation();
  const release = useReleaseReservation();

  const [picked, setPicked] = useState<Map<number, string>>(new Map());
  const [teamId, setTeamId] = useState<number | null>(null);
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

  const teamOptions = useMemo(
    () => (teams.data ?? []).map((team) => ({ value: String(team.id), label: team.name ?? '' })),
    [teams.data],
  );

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
    <div className="flex flex-col gap-3">
      {/*
        The caption and legend span the page rather than sitting inside the map column. Kept in
        that column they were a 25px band that only the right half had, so the form card started
        a full row above the floor beside it and the two columns never shared a top edge — which
        is what made the screen read as misaligned rather than as two panels side by side.
      */}
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <p className="eyebrow text-[11px] text-ink/60">
          Availability on{' '}
          <span className="font-mono font-bold text-ink">{formatMapDate(mapDate)}</span>
          {endDate > startDate && (
            <span className="font-semibold normal-case tracking-normal text-ink/50">
              {' — first day of the block; later days can differ'}
            </span>
          )}
        </p>
        <FloorLegend />
      </div>

      {/*
        The form and the map are the working pair, so they share the two-column row. The list of
        current holds does not belong in a 300px rail: at eighteen holds it became a column of
        near-identical cards taller than the map beside it, while the whole width under the map
        sat empty. It is a full-width band below instead.
      */}
      {/*
        380px rather than 300: the rail carries two date fields side by side, and at 300 they were
        squeezed to a size that made picking a day feel like an afterthought next to the map. This
        is the panel where the decision is actually made, so it gets room to look like one.
      */}
      <div className="grid gap-4 lg:grid-cols-[380px_1fr]">
        <div className="order-2 flex flex-col gap-4 lg:order-1">
        <div className="ui-edge border-line bg-paper p-5 shadow-[var(--dd-shadow)]">
          <p className="eyebrow text-xs text-ink/60">Hold desks</p>

          <div className="mt-4">
            <SelectField
              label="Team"
              value={effectiveTeamId == null ? '' : String(effectiveTeamId)}
              options={teamOptions}
              disabled={teams.isPending || teamOptions.length === 0}
              placeholder={teams.isPending ? 'Loading teams…' : 'No teams'}
              onChange={(next) => setTeamId(Number(next))}
            />
          </div>

          {teams.data?.length === 0 && (
            <p className="mt-2 text-xs font-semibold text-ink/55">
              You don&rsquo;t manage any teams yet, so there is nobody to hold desks for.
            </p>
          )}

          {/*
            Stacked, not side by side. Two dates sharing one rail can only ever be half a rail
            wide, and a date is the longest value on this form.
          */}
          <div className="mt-4 flex flex-col gap-3">
            <DateField
              label="From"
              value={startDate}
              min={officeToday}
              // A range that starts after it ends is never what anybody meant, so moving the
              // start past the end carries the end with it rather than leaving a state the
              // submit button then has to refuse.
              onChange={(iso) => onChangeRange(iso, endDate < iso ? iso : endDate)}
            />
            <DateField
              label="To"
              value={endDate}
              min={startDate || officeToday}
              onChange={(iso) => onChangeRange(startDate, iso)}
            />
          </div>

          <div className="mt-4 ui-edge border-dashed border-ink/40 px-3.5 py-3">
            <p className="eyebrow text-[11px] text-ink/50">Picked desks</p>
            {picked.size === 0 ? (
              <p className="mt-0.5 font-mono text-sm font-bold ui-label text-ink/35">
                None — tap desks on the map
              </p>
            ) : (
              <ul className="mt-1.5 flex flex-wrap gap-1.5">
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
                      className="flex items-center gap-1.5 ui-edge border-line bg-selected px-2 py-1 font-mono text-xs font-bold ui-label text-ink"
                    >
                      {label}
                      <FontAwesomeIcon icon={faXmark} className="h-3 w-3" aria-hidden="true" />
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
            className="ui-control mt-4 flex w-full items-center justify-center gap-2 ui-edge border-action bg-action px-4 py-3.5 text-base font-bold ui-label text-white shadow-[var(--dd-shadow)] transition-transform hover:-translate-y-px disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:translate-y-0"
          >
            <FontAwesomeIcon icon={faUsers} className="h-4 w-4" aria-hidden="true" />
            {create.isPending ? 'Holding…' : `Hold ${picked.size || ''} desk${picked.size === 1 ? '' : 's'}`}
          </button>

          {formError && (
            <p role="alert" className="mt-2 flex items-start gap-1.5 text-xs font-semibold text-danger">
              <FontAwesomeIcon
                icon={faTriangleExclamation}
                className="mt-0.5 h-3 w-3"
                aria-hidden="true"
              />
              {formError}
            </p>
          )}
        </div>

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

      <CurrentHolds
        holds={holds.data ?? []}
        isPending={holds.isPending}
        releasingId={release.isPending ? release.variables : null}
        onRelease={(id) => release.mutate(id)}
      />
    </div>
  );
}

type ReservationView = components['schemas']['ReservationView'];

/**
 * One block as the manager actually made it: a team, a date range, and the desks it covers.
 *
 * <p>Holding nine desks writes nine {@code seat_reservation} rows, and listing them as nine
 * cards is a faithful rendering of the table rather than of the decision — the team, the dates
 * and the cut-off repeat verbatim on every one. Grouping puts that shared information in a
 * heading once and leaves the desks as chips, so eighteen holds read as the two blocks they are.
 */
interface HoldGroup {
  key: string;
  teamId: number;
  teamName: string;
  startDate: string;
  endDate: string;
  enforcedNow: boolean;
  releaseAtTime: string | undefined;
  seats: ReservationView[];
}

function groupHolds(holds: ReservationView[]): HoldGroup[] {
  const groups = new Map<string, HoldGroup>();

  for (const hold of holds) {
    // Enforcement is part of the key: the same team over the same dates can have one block still
    // holding and another already released, and merging those would state something untrue.
    const key = `${hold.teamId}|${hold.startDate}|${hold.endDate}|${hold.enforcedNow}`;
    const existing = groups.get(key);
    if (existing) {
      existing.seats.push(hold);
      continue;
    }
    groups.set(key, {
      key,
      teamId: hold.teamId ?? -1,
      teamName: hold.teamName ?? 'Team',
      startDate: hold.startDate ?? '',
      endDate: hold.endDate ?? '',
      enforcedNow: hold.enforcedNow ?? false,
      releaseAtTime: hold.releaseAtTime ?? undefined,
      seats: [hold],
    });
  }

  for (const group of groups.values()) {
    group.seats.sort((a, b) => (a.seatLabel ?? '').localeCompare(b.seatLabel ?? ''));
  }
  return [...groups.values()].sort((a, b) => a.startDate.localeCompare(b.startDate));
}

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
  const groups = groupHolds(holds);

  return (
    <section
      aria-labelledby="current-holds-heading"
      className="ui-edge border-line bg-paper p-4 shadow-[var(--dd-shadow)]"
    >
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <div>
          <p id="current-holds-heading" className="eyebrow text-[11px] text-ink/60">
            Current holds
          </p>
          <p className="text-[11px] font-semibold text-ink/55">
            A block holds its desks until the morning cut-off, then releases so nobody is locked
            out of an empty office.
          </p>
        </div>
        <p className="font-mono text-[10px] ui-label text-ink/40">
          {isPending
            ? 'Loading…'
            : `${holds.length} desk${holds.length === 1 ? '' : 's'} in ${groups.length} block${
                groups.length === 1 ? '' : 's'
              }`}
        </p>
      </div>

      {!isPending && holds.length === 0 && (
        <p className="ui-edge border-dashed border-line p-6 text-center text-xs font-semibold text-ink/55">
          No desks are held right now.
        </p>
      )}

      <ul className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
        {groups.map((group) => (
          <li
            key={group.key}
            className={`ui-edge border-line p-3 ${group.enforcedNow ? '' : 'opacity-70'}`}
            style={{ background: teamTint(group.teamId) }}
          >
            <p className="font-mono text-xs font-bold ui-label text-ink">
              {group.teamName} · {group.seats.length} desk{group.seats.length === 1 ? '' : 's'}
            </p>
            <p className="font-mono text-[10px] ui-label text-ink/65">
              {group.startDate}
              {group.endDate !== group.startDate && ` → ${group.endDate}`}
            </p>
            {/*
              The answer to "I held these desks and the map shows nothing". A block releases
              softly at its cut-off — the rows survive, the hold stops. Saying which of the two
              states it is in turns an apparent bug into the rule it actually is.
            */}
            {group.enforcedNow ? (
              <p className="font-mono text-[10px] font-bold ui-label text-ink">
                Holding · frees {group.releaseAtTime?.slice(0, 5)}
              </p>
            ) : (
              <p className="flex items-center gap-1 font-mono text-[10px] font-bold ui-label text-ink">
                <FontAwesomeIcon icon={faLockOpen} className="h-2.5 w-2.5" aria-hidden="true" />
                Released at {group.releaseAtTime?.slice(0, 5)} — anyone may sit here
              </p>
            )}

            <ul className="mt-2 flex flex-wrap gap-1">
              {group.seats.map((hold) => (
                <li key={hold.id}>
                  <span className="flex items-center gap-1 ui-edge border-ink/30 bg-paper/70 py-0.5 pl-1.5 pr-0.5">
                    <span className="font-mono text-[11px] font-bold text-ink">
                      {hold.seatLabel}
                    </span>
                    <button
                      type="button"
                      onClick={() => onRelease(hold.id ?? -1)}
                      disabled={releasingId === hold.id}
                      aria-label={`Release ${hold.seatLabel} from ${hold.teamName}`}
                      title={`Release ${hold.seatLabel}`}
                      className="ui-control-icon flex items-center justify-center px-1 text-ink hover:bg-ink hover:text-paper disabled:opacity-40"
                    >
                      <FontAwesomeIcon icon={faXmark} className="h-2.5 w-2.5" aria-hidden="true" />
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}
