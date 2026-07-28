import { useEffect, useRef, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowRightLong,
  faDatabase,
  faServer,
  faTowerBroadcast,
  faUsers,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import type { ApiCallEvent } from '../../hooks/useApiTelemetry';

/** Must match the packet animation in index.css, or dots pile up or vanish early. */
const PACKET_LIFETIME_MS = 1100;

type Lane = ApiCallEvent['lane'];

interface LaneSpec {
  id: Lane;
  label: string;
  /** What this lane actually does, in the office's language rather than the router's. */
  hint: string;
}

/**
 * The lanes, in the order they appear. `OTHER` is last because it is the catch-all — anything
 * showing up there is a route this diagram has not been taught about yet.
 */
const LANES: LaneSpec[] = [
  { id: 'BOOKING', label: 'Bookings', hint: 'Claims, moves, cancels, check-ins' },
  { id: 'SEATMAP', label: 'Seat map', hint: 'Floor plans and availability' },
  { id: 'RESERVATION', label: 'Reservations', hint: 'Team holds' },
  { id: 'ADMIN', label: 'Admin', hint: 'Accounts and desk status' },
  { id: 'AUTH', label: 'Auth', hint: 'Sign-in and identity' },
  { id: 'OTHER', label: 'Other', hint: 'Everything else' },
];

/** A dot currently travelling a lane. */
interface Packet {
  key: string;
  lane: Lane;
  outcome: ApiCallEvent['outcome'];
}

const OUTCOME_DOT: Record<ApiCallEvent['outcome'], string> = {
  OK: 'bg-success',
  CLIENT_ERROR: 'bg-warning',
  SERVER_ERROR: 'bg-danger',
};

/**
 * A live picture of traffic: clients on the left, the API in the middle, its two sinks on the
 * right, and one dot per finished call travelling the lane it belongs to.
 *
 * <p>Every dot is a call that has *already completed* — the server broadcasts on
 * `afterCompletion`. So this shows shape and rate, not requests in flight, and a dot's colour is
 * the status the caller actually got.
 */
export function ApiFlowDiagram({
  events,
  connected,
}: {
  events: ApiCallEvent[];
  connected: boolean;
}) {
  const [packets, setPackets] = useState<Packet[]>([]);
  const [laneTotals, setLaneTotals] = useState<Record<string, number>>({});
  // Ids already turned into a dot. Without this, a re-render for any other reason would
  // re-launch every event still in the window.
  const seen = useRef<Set<string>>(new Set());

  useEffect(() => {
    const fresh = events.filter((event) => !seen.current.has(event.id));
    if (fresh.length === 0) return;

    for (const event of fresh) seen.current.add(event.id);
    // The seen-set is bounded by the same window the feed keeps, so it cannot grow unbounded
    // on a tab left open all day.
    if (seen.current.size > 400) {
      seen.current = new Set(events.map((event) => event.id));
    }

    setPackets((prev) => [
      ...prev,
      ...fresh.map((event) => ({ key: event.id, lane: event.lane, outcome: event.outcome })),
    ]);
    setLaneTotals((prev) => {
      const next = { ...prev };
      for (const event of fresh) next[event.lane] = (next[event.lane] ?? 0) + 1;
      return next;
    });

    const expiry = window.setTimeout(() => {
      const expired = new Set(fresh.map((event) => event.id));
      setPackets((prev) => prev.filter((packet) => !expired.has(packet.key)));
    }, PACKET_LIFETIME_MS);
    return () => window.clearTimeout(expiry);
  }, [events]);

  return (
    <div className="ui-edge border-line bg-paper p-4">
      <div className="grid gap-4 lg:grid-cols-[auto_1fr_auto] lg:items-center">
        <FlowNode
          icon={faUsers}
          label="Clients"
          detail={connected ? 'Streaming' : 'Not connected'}
        />

        <ol className="flex flex-col gap-1.5">
          {LANES.map((lane) => (
            <LaneRail
              key={lane.id}
              spec={lane}
              total={laneTotals[lane.id] ?? 0}
              packets={packets.filter((packet) => packet.lane === lane.id)}
            />
          ))}
        </ol>

        {/* The arrows land on the API; everything below it is what the API then fans out to, so
            the two sinks are indented behind a rule rather than sitting as peers. */}
        <div className="flex flex-col lg:w-48">
          <FlowNode icon={faServer} label="DeskDibs API" detail="Spring Boot" />
          <span aria-hidden="true" className="ml-4 h-3 w-px bg-line" />
          <div className="ml-4 flex flex-col gap-1.5 border-l border-line pl-3">
            <FlowNode icon={faDatabase} label="Postgres" detail="Unique index arbitrates" small />
            <FlowNode icon={faTowerBroadcast} label="Broadcast" detail="Seat map sockets" small />
          </div>
        </div>
      </div>
    </div>
  );
}

function LaneRail({
  spec,
  total,
  packets,
}: {
  spec: LaneSpec;
  total: number;
  packets: Packet[];
}) {
  return (
    <li className="flex items-center gap-2 sm:gap-3">
      {/* The hint is the first thing to go on a phone: the rail needs the room more than the
          prose does, and the lane name alone still says which flow this is. */}
      <span className="w-20 shrink-0 sm:w-36">
        <span className="block text-xs font-bold text-ink">{spec.label}</span>
        <span className="hidden truncate text-[10px] text-ink-soft sm:block" title={spec.hint}>
          {spec.hint}
        </span>
      </span>

      {/* The rail. Dots are positioned against this, so it must stay the positioning context. */}
      <span className="relative h-5 min-w-24 flex-1 overflow-hidden">
        <span
          aria-hidden="true"
          className="absolute left-0 right-0 top-1/2 h-px -translate-y-1/2 bg-line"
        />
        {packets.map((packet) => (
          <span
            key={packet.key}
            aria-hidden="true"
            className={`api-packet h-2 w-2 rounded-full ${OUTCOME_DOT[packet.outcome]}`}
          />
        ))}
      </span>

      <FontAwesomeIcon
        icon={faArrowRightLong}
        className="h-3 w-3 shrink-0 text-ink-soft"
        aria-hidden="true"
      />
      {/* The accessible channel: the count says what the dots say, for anyone who cannot see
          them or has reduced motion on. */}
      <span className="w-10 shrink-0 text-right font-mono text-xs font-bold tabular-nums text-ink sm:w-16">
        {total.toLocaleString()}
        <span className="sr-only"> {spec.label} calls since this view opened</span>
      </span>
    </li>
  );
}

function FlowNode({
  icon,
  label,
  detail,
  small = false,
}: {
  icon: IconDefinition;
  label: string;
  detail: string;
  small?: boolean;
}) {
  return (
    <div
      className={`ui-edge border-line bg-paper-dim ${small ? 'px-3 py-1.5' : 'px-3 py-2.5'}`}
    >
      <p className="flex items-center gap-2 text-xs font-bold text-ink">
        <FontAwesomeIcon icon={icon} className="h-3.5 w-3.5 text-ink-soft" aria-hidden="true" />
        {label}
      </p>
      <p className="mt-0.5 text-[10px] text-ink-soft">{detail}</p>
    </div>
  );
}
