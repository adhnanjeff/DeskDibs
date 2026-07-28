import { useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBan,
  faChair,
  faChevronDown,
  faChevronRight,
  faCircleCheck,
  faMagnifyingGlass,
  faTriangleExclamation,
  faUserCheck,
  faUserSlash,
  faUsers,
  faWrench,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import { useAuth } from '../auth/AuthContext';
import { useSeatMap } from '../hooks/useSeatMap';
import {
  useAdminUsers,
  useSetSeatStatus,
  useSetUserActive,
  type AdminUser,
  type SeatStatus,
} from '../hooks/useAdmin';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';
import type { components } from '../api/schema';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

interface AdminSeat {
  seatId: number;
  seatLabel: string;
  bookable: boolean;
}

type Tab = 'people' | 'desks';

/** Every seat on the floor, flattened out of the map the rest of the app already caches. */
function flattenSeats(map: SeatMapResponse | undefined): AdminSeat[] {
  const seats: AdminSeat[] = [];
  for (const floor of map?.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        for (const seat of table.seats ?? []) {
          if (seat.seatId == null) continue;
          seats.push({
            seatId: seat.seatId,
            seatLabel: seat.seatLabel ?? String(seat.seatId),
            // The map reports DISABLED for anything out of the pool, whether it is disabled or
            // broken — it does not distinguish, because the floor does not need to.
            bookable: seat.state !== 'DISABLED',
          });
        }
      }
    }
  }
  return seats;
}

/**
 * Desk labels read `R3-A2` — the table is everything before the dash. Grouping by it turns one
 * flat list of a hundred desks into eighteen short rows that match how the floor is actually
 * described out loud.
 */
function tableOf(seatLabel: string): string {
  const dash = seatLabel.indexOf('-');
  return dash > 0 ? seatLabel.slice(0, dash) : seatLabel;
}

function groupByTable(seats: AdminSeat[]): [string, AdminSeat[]][] {
  const groups = new Map<string, AdminSeat[]>();
  for (const seat of seats) {
    const key = tableOf(seat.seatLabel);
    const bucket = groups.get(key);
    if (bucket) bucket.push(seat);
    else groups.set(key, [seat]);
  }
  return [...groups.entries()];
}

/**
 * Administration: closing accounts and taking desks out of service.
 *
 * <p>Both actions cost other people their bookings, so both report what they took rather than
 * flashing a confirmation. The report is the point of the screen — an administrator who withdraws
 * a desk should find out immediately that four people were sitting at it this week.
 *
 * <h2>Why this is not a list of every desk</h2>
 * There are a hundred desks and an administrator arrives knowing which one they came for: a desk
 * somebody reported broken, or one that has been out of service and is fixed. Rendering all
 * hundred as equal-weight cards buried those two jobs in noise. So the screen leads with what
 * needs attention — the desks currently withdrawn — puts search next, and keeps the full floor
 * behind a disclosure, grouped by table.
 */
function AdminWorkspace() {
  const { user } = useAuth();
  const people = useAdminUsers();
  const { data: seatMap } = useSeatMap();
  const setActive = useSetUserActive();
  const setSeatStatus = useSetSeatStatus();

  const [tab, setTab] = useState<Tab>('people');
  const [message, setMessage] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [seatFilter, setSeatFilter] = useState('');
  const [browseAll, setBrowseAll] = useState(false);

  const seats = useMemo(() => flattenSeats(seatMap), [seatMap]);
  const withdrawn = useMemo(() => seats.filter((seat) => !seat.bookable), [seats]);
  const matches = useMemo(() => {
    const needle = seatFilter.trim().toLowerCase();
    if (!needle) return null;
    return seats.filter((seat) => seat.seatLabel.toLowerCase().includes(needle));
  }, [seats, seatFilter]);

  const deactivated = useMemo(
    () => (people.data ?? []).filter((person) => !person.active).length,
    [people.data],
  );

  const busy = setActive.isPending || setSeatStatus.isPending;

  function report(released: number, what: string) {
    setFailure(null);
    setMessage(
      released === 0
        ? `${what} No bookings were affected.`
        : `${what} ${released} booking${released === 1 ? '' : 's'} released — those people will see why on their bookings page.`,
    );
  }

  function onToggleAccount(person: AdminUser) {
    if (person.id == null) return;
    const nowActive = !person.active;
    setActive.mutate(
      { userId: person.id, active: nowActive },
      {
        onSuccess: (result) =>
          report(
            result.bookingsReleased ?? 0,
            `${result.displayName ?? 'That account'} is now ${nowActive ? 'active' : 'deactivated'}.`,
          ),
        onError: (e) => {
          setMessage(null);
          setFailure(e instanceof Error ? e.message : 'Could not change that account.');
        },
      },
    );
  }

  function onSetSeat(seat: AdminSeat, status: SeatStatus) {
    setSeatStatus.mutate(
      { seatId: seat.seatId, status },
      {
        onSuccess: (result) =>
          report(
            result.bookingsReleased ?? 0,
            `Desk ${result.seatLabel ?? seat.seatLabel} is now ${(result.status ?? status).toLowerCase()}.`,
          ),
        onError: (e) => {
          setMessage(null);
          setFailure(e instanceof Error ? e.message : 'Could not change that desk.');
        },
      },
    );
  }

  if (people.isPending) {
    return <p className="text-sm font-semibold text-ink-soft">Loading…</p>;
  }
  if (people.isError) {
    return (
      <ErrorFallback
        title="Couldn't load the people list"
        message={people.error instanceof Error ? people.error.message : undefined}
        onRetry={() => void people.refetch()}
      />
    );
  }

  const seatAction = (seat: AdminSeat) => (
    <SeatActionButton seat={seat} busy={busy} onSetSeat={onSetSeat} />
  );

  return (
    <div className="flex flex-col gap-5">
      {/* Both outcomes go through a live region: an administrator who has just withdrawn a desk
          needs to hear how many bookings it cost, not read it only if they happen to look. */}
      <div role="status" aria-live="polite">
        {message && (
          <p className="flex items-start gap-2 ui-edge border-line bg-selected px-3 py-2 text-sm font-semibold text-ink">
            <FontAwesomeIcon icon={faCircleCheck} className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            {message}
          </p>
        )}
      </div>
      {failure && (
        <p
          role="alert"
          className="flex items-start gap-2 ui-edge border-danger bg-danger-tint px-3 py-2 text-sm font-semibold text-danger"
        >
          <FontAwesomeIcon icon={faTriangleExclamation} className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {failure}
        </p>
      )}

      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={faUsers} label="People" value={people.data.length} detail="accounts on the system" />
        <Stat
          icon={faUserSlash}
          label="Deactivated"
          value={deactivated}
          detail={deactivated === 0 ? 'everyone has access' : 'refused at login'}
          alarming={deactivated > 0}
        />
        <Stat icon={faChair} label="Desks" value={seats.length} detail="on the floor plan" />
        <Stat
          icon={faWrench}
          label="Out of service"
          value={withdrawn.length}
          detail={withdrawn.length === 0 ? 'the whole floor is bookable' : 'not bookable'}
          alarming={withdrawn.length > 0}
        />
      </div>

      {/* One job at a time. Accounts and desks share a page but never a task. */}
      <div className="flex gap-1 ui-edge border-line bg-paper-dim p-1" role="tablist" aria-label="Administration area">
        <TabButton id="people" active={tab} onSelect={setTab} icon={faUsers}>
          People
        </TabButton>
        <TabButton id="desks" active={tab} onSelect={setTab} icon={faChair}>
          Desks
        </TabButton>
      </div>

      {tab === 'people' ? (
        <section id="panel-people" role="tabpanel" aria-labelledby="tab-people">
          <p className="mb-3 text-sm text-ink-soft">
            Deactivating an account refuses it at login and returns every desk it is still holding.
          </p>
          <ul className="ui-edge divide-y divide-line border-line bg-paper">
            {people.data.map((person) => {
              const isSelf = person.id === user?.id;
              return (
                <li key={person.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5">
                  <span className="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
                    <span className="font-semibold text-ink">{person.displayName}</span>
                    <span className="truncate font-mono text-xs text-ink-soft">{person.email}</span>
                  </span>

                  <span className="font-mono text-[10px] font-bold ui-label text-ink-soft">
                    {person.role}
                  </span>

                  <span
                    className={`inline-flex items-center gap-1.5 text-xs font-bold ${
                      person.active ? 'text-ink-soft' : 'text-danger'
                    }`}
                  >
                    <FontAwesomeIcon
                      icon={person.active ? faUserCheck : faUserSlash}
                      className="h-3 w-3"
                      aria-hidden="true"
                    />
                    {person.active ? 'Active' : 'Deactivated'}
                  </span>

                  <button
                    type="button"
                    onClick={() => onToggleAccount(person)}
                    disabled={busy || isSelf}
                    // The server refuses this too; disabling it here just avoids offering a
                    // button whose only outcome is a 409.
                    title={isSelf ? 'You cannot deactivate your own account' : undefined}
                    aria-label={`${person.active ? 'Deactivate' : 'Reactivate'} ${person.displayName}`}
                    className="ui-control ui-edge border-line px-3 py-1.5 text-xs font-bold ui-label text-ink hover:bg-ink hover:text-paper disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-ink"
                  >
                    {person.active ? 'Deactivate' : 'Reactivate'}
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ) : (
        <section id="panel-desks" role="tabpanel" aria-labelledby="tab-desks" className="flex flex-col gap-4">
          <p className="text-sm text-ink-soft">
            A withdrawn desk is never deleted — booking history survives a refit. Anyone booked onto
            it from today onward is released and told why.
          </p>

          <label className="flex max-w-md items-center gap-2 ui-edge ui-control border-line bg-paper px-3 py-2">
            <FontAwesomeIcon icon={faMagnifyingGlass} className="h-3.5 w-3.5 text-ink-soft" aria-hidden="true" />
            <span className="sr-only">Search desks by label</span>
            <input
              type="search"
              value={seatFilter}
              onChange={(event) => setSeatFilter(event.target.value)}
              placeholder="Search by label, e.g. R4-A1"
              className="w-full bg-transparent text-sm font-semibold text-ink outline-none placeholder:text-ink-soft"
            />
          </label>

          {matches ? (
            <DeskGroup
              heading={`${matches.length} desk${matches.length === 1 ? '' : 's'} matching “${seatFilter.trim()}”`}
              seats={matches}
              renderAction={seatAction}
              emptyMessage={`No desk matches “${seatFilter.trim()}”.`}
            />
          ) : (
            <>
              <DeskGroup
                heading={
                  withdrawn.length === 0
                    ? 'Out of service'
                    : `Out of service (${withdrawn.length})`
                }
                seats={withdrawn}
                renderAction={seatAction}
                emptyMessage="Every desk on the floor is bookable. Nothing needs attention here."
              />

              <div>
                <button
                  type="button"
                  onClick={() => setBrowseAll((open) => !open)}
                  aria-expanded={browseAll}
                  className="ui-control ui-edge flex w-full items-center gap-2 border-line bg-paper px-3 py-2 text-sm font-bold text-ink"
                >
                  <FontAwesomeIcon
                    icon={browseAll ? faChevronDown : faChevronRight}
                    className="h-3 w-3 text-ink-soft"
                    aria-hidden="true"
                  />
                  Browse the whole floor
                  <span className="ml-auto font-mono text-xs font-bold text-ink-soft">
                    {seats.length} desks
                  </span>
                </button>

                {browseAll && (
                  <div className="mt-3 flex flex-col gap-3">
                    {groupByTable(seats).map(([table, group]) => (
                      <DeskGroup
                        key={table}
                        heading={`Table ${table}`}
                        seats={group}
                        renderAction={seatAction}
                        emptyMessage=""
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}

/** A labelled block of desks. Compact rows, not cards — a desk is one line of information. */
function DeskGroup({
  heading,
  seats,
  renderAction,
  emptyMessage,
}: {
  heading: string;
  seats: AdminSeat[];
  renderAction: (seat: AdminSeat) => React.ReactNode;
  emptyMessage: string;
}) {
  return (
    <section>
      <h3 className="eyebrow mb-1.5 text-[11px] text-ink-soft">{heading}</h3>
      {seats.length === 0 ? (
        emptyMessage ? (
          <p className="ui-edge border-dashed border-line bg-paper p-4 text-center text-sm text-ink-soft">
            {emptyMessage}
          </p>
        ) : null
      ) : (
        // Columns rather than one very wide list: a desk row is four short things, and stretching
        // it across the full shell puts the label and its button a screen apart.
        <ul className="grid gap-1.5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {seats.map((seat) => (
            <li
              key={seat.seatId}
              className="ui-edge flex items-center gap-2.5 border-line bg-paper px-3 py-1.5"
            >
              <FontAwesomeIcon
                icon={seat.bookable ? faChair : faBan}
                className={`h-3.5 w-3.5 shrink-0 ${seat.bookable ? 'text-ink-soft' : 'text-danger'}`}
                aria-hidden="true"
              />
              <span className="font-mono text-sm font-bold text-ink">{seat.seatLabel}</span>
              {/* State is never colour alone — the word is always present. */}
              <span
                className={`truncate text-xs font-semibold ${seat.bookable ? 'text-ink-soft' : 'text-danger'}`}
              >
                {seat.bookable ? 'In service' : 'Out of service'}
              </span>
              <span className="ml-auto shrink-0">{renderAction(seat)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SeatActionButton({
  seat,
  busy,
  onSetSeat,
}: {
  seat: AdminSeat;
  busy: boolean;
  onSetSeat: (seat: AdminSeat, status: SeatStatus) => void;
}) {
  return seat.bookable ? (
    <button
      type="button"
      onClick={() => onSetSeat(seat, 'BROKEN')}
      disabled={busy}
      aria-label={`Take desk ${seat.seatLabel} out of service`}
      className="ui-control inline-flex items-center gap-1.5 ui-edge border-line px-2.5 py-1 text-[11px] font-bold ui-label text-ink hover:border-danger hover:bg-danger hover:text-white disabled:opacity-40"
    >
      <FontAwesomeIcon icon={faWrench} className="h-3 w-3" aria-hidden="true" />
      Withdraw
    </button>
  ) : (
    <button
      type="button"
      onClick={() => onSetSeat(seat, 'ACTIVE')}
      disabled={busy}
      aria-label={`Return desk ${seat.seatLabel} to service`}
      className="ui-control inline-flex items-center gap-1.5 ui-edge border-line bg-selected px-2.5 py-1 text-[11px] font-bold ui-label text-ink disabled:opacity-40"
    >
      <FontAwesomeIcon icon={faCircleCheck} className="h-3 w-3" aria-hidden="true" />
      Return
    </button>
  );
}

function TabButton({
  id,
  active,
  onSelect,
  icon,
  children,
}: {
  id: Tab;
  active: Tab;
  onSelect: (tab: Tab) => void;
  icon: IconDefinition;
  children: React.ReactNode;
}) {
  const selected = active === id;
  return (
    <button
      type="button"
      role="tab"
      id={`tab-${id}`}
      aria-selected={selected}
      aria-controls={`panel-${id}`}
      onClick={() => onSelect(id)}
      className={`ui-control ui-edge flex flex-1 items-center justify-center gap-2 px-3 py-1.5 text-sm font-bold transition-colors ${
        selected ? 'border-line bg-paper text-ink' : 'border-transparent text-ink-soft hover:text-ink'
      }`}
    >
      <FontAwesomeIcon icon={icon} className="h-3.5 w-3.5" aria-hidden="true" />
      {children}
    </button>
  );
}

function Stat({
  icon,
  label,
  value,
  detail,
  alarming = false,
}: {
  icon: IconDefinition;
  label: string;
  value: number;
  detail: string;
  alarming?: boolean;
}) {
  return (
    <div className={`ui-edge px-3 py-2.5 ${alarming ? 'border-danger bg-danger-tint' : 'border-line bg-paper'}`}>
      <p className="eyebrow flex items-center gap-1.5 text-[10px] text-ink-soft">
        <FontAwesomeIcon icon={icon} className="h-3 w-3" aria-hidden="true" />
        {label}
      </p>
      <p className="font-mono text-2xl font-bold leading-tight tabular-nums text-ink">{value}</p>
      <p className="text-[11px] text-ink-soft">{detail}</p>
    </div>
  );
}

export function AdminPage() {
  return (
    <div>
      <div className="mb-5">
        <p className="eyebrow text-xs text-ink-soft">Office administration</p>
        <h1 className="ui-title text-ink">Admin</h1>
        <p className="mt-1 max-w-3xl text-sm text-ink-soft">
          Closing an account or withdrawing a desk releases the bookings involved. Both are
          announced here, and the people affected see the reason on their own bookings page.
        </p>
      </div>
      <SectionErrorBoundary
        title="Administration hit a snag"
        message="Something failed while rendering this page — try reloading this section."
      >
        <AdminWorkspace />
      </SectionErrorBoundary>
    </div>
  );
}
