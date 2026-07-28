import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCircleCheck,
  faCircleExclamation,
  faTriangleExclamation,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import type { ApiCallEvent } from '../../hooks/useApiTelemetry';

/**
 * Outcome is carried by icon + word + colour together, never colour alone — the same rule the
 * seat map follows.
 */
const OUTCOME: Record<ApiCallEvent['outcome'], { icon: IconDefinition; tone: string; word: string }> =
  {
    OK: { icon: faCircleCheck, tone: 'text-success', word: 'OK' },
    CLIENT_ERROR: { icon: faTriangleExclamation, tone: 'text-warning', word: 'Rejected' },
    SERVER_ERROR: { icon: faCircleExclamation, tone: 'text-danger', word: 'Failed' },
  };

function clockOf(iso: string): string {
  const at = new Date(iso);
  return Number.isNaN(at.getTime())
    ? '--:--:--'
    : at.toLocaleTimeString(undefined, { hour12: false });
}

/**
 * The last few dozen calls, newest first.
 *
 * <p>Not a live region: at office load this updates many times a second, and announcing each one
 * would make a screen reader unusable. The summary figures above it are the announced channel.
 */
export function ApiEventFeed({ events }: { events: ApiCallEvent[] }) {
  if (events.length === 0) {
    return (
      <p className="ui-edge border-dashed border-line bg-paper p-6 text-center text-sm font-semibold text-ink-soft">
        Nothing yet. Any request anyone makes — a seat map opening, a desk being claimed — shows up
        here the moment the server finishes it.
      </p>
    );
  }

  return (
    // Six columns do not fit a phone. The table scrolls inside its own container rather than
    // squashing — and critically, rather than spilling past the card where the page's
    // overflow-x:hidden would clip the status and duration columns out of reach entirely.
    <div className="ui-edge overflow-x-auto border-line bg-paper">
      <table className="w-full min-w-[34rem] border-collapse text-left">
        <caption className="sr-only">
          The most recent API calls handled by the server, newest first.
        </caption>
        <thead>
          <tr className="border-b border-line bg-paper-dim">
            <Th>Time</Th>
            <Th>Method</Th>
            <Th>Route</Th>
            <Th>Who</Th>
            <Th align="right">Status</Th>
            <Th align="right">Took</Th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => {
            const outcome = OUTCOME[event.outcome] ?? OUTCOME.OK;
            return (
              <tr key={event.id} className="border-b border-line last:border-b-0 even:bg-paper-dim/40">
                <Td>
                  <span className="font-mono text-ink-soft">{clockOf(event.at)}</span>
                </Td>
                <Td>
                  <span className="font-mono font-bold text-ink">{event.method}</span>
                </Td>
                <Td>
                  <span className="font-mono text-ink">{event.route}</span>
                </Td>
                <Td>
                  <span className="text-ink-soft">{event.actor}</span>
                </Td>
                <Td align="right">
                  <span className={`inline-flex items-center gap-1.5 font-mono font-bold ${outcome.tone}`}>
                    <FontAwesomeIcon icon={outcome.icon} className="h-3 w-3" aria-hidden="true" />
                    {event.status}
                    <span className="sr-only"> {outcome.word}</span>
                  </span>
                </Td>
                <Td align="right">
                  <span className="font-mono tabular-nums text-ink-soft">{event.durationMs} ms</span>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Th({ children, align = 'left' }: { children: React.ReactNode; align?: 'left' | 'right' }) {
  return (
    <th
      scope="col"
      className={`px-3 py-2 text-[10px] font-bold ui-label text-ink-soft ${
        align === 'right' ? 'text-right' : 'text-left'
      }`}
    >
      {children}
    </th>
  );
}

function Td({ children, align = 'left' }: { children: React.ReactNode; align?: 'left' | 'right' }) {
  return (
    <td className={`px-3 py-1.5 text-xs ${align === 'right' ? 'text-right' : 'text-left'}`}>
      {children}
    </td>
  );
}
