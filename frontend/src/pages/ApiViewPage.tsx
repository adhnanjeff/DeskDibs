import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBolt,
  faCircleExclamation,
  faGaugeHigh,
  faStopwatch,
  faTriangleExclamation,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import { useApiTelemetry } from '../hooks/useApiTelemetry';
import { ApiFlowDiagram } from '../components/apiview/ApiFlowDiagram';
import { ApiEventFeed } from '../components/apiview/ApiEventFeed';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

function ApiViewWorkspace() {
  const { events, stats, connected, error } = useApiTelemetry();

  return (
    <div className="flex flex-col gap-5">
      {error && (
        <p
          role="alert"
          className="flex items-start gap-2 ui-edge border-danger bg-danger-tint px-3 py-2 text-sm font-semibold text-danger"
        >
          <FontAwesomeIcon
            icon={faTriangleExclamation}
            className="mt-0.5 h-3.5 w-3.5 shrink-0"
            aria-hidden="true"
          />
          {error}
        </p>
      )}

      {/* Announced, unlike the feed: these four numbers are the summary a screen reader should
          hear, and they change slowly enough to be readable. */}
      <div
        role="status"
        aria-live="polite"
        aria-label="Live API summary"
        className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4"
      >
        <Stat
          icon={faBolt}
          label="Throughput"
          value={stats.ratePerSecond.toFixed(1)}
          unit="req/s"
          detail={`${stats.sampleSize} in the last 10s`}
        />
        <Stat
          icon={faStopwatch}
          label="Median"
          value={String(stats.medianMs)}
          unit="ms"
          detail="Server time, half are faster"
        />
        <Stat
          icon={faGaugeHigh}
          label="95th percentile"
          value={String(stats.p95Ms)}
          unit="ms"
          detail="The slow tail"
        />
        <Stat
          icon={faCircleExclamation}
          label="Errors"
          value={(stats.errorRate * 100).toFixed(0)}
          unit="%"
          detail="4xx and 5xx together"
          alarming={stats.errorRate > 0.1}
        />
      </div>

      <section aria-labelledby="flow-heading" className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h2 id="flow-heading" className="eyebrow text-xs text-ink-soft">
            Request flow
          </h2>
          <ConnectionBadge connected={connected} />
        </div>
        <ApiFlowDiagram events={events} connected={connected} />
      </section>

      {/* min-w-0: a flex child defaults to min-width:auto, which would let the table's
          min-width push the whole page wider instead of scrolling inside its own card. */}
      <section aria-labelledby="feed-heading" className="min-w-0">
        <h2 id="feed-heading" className="eyebrow mb-2 text-xs text-ink-soft">
          Recent calls
        </h2>
        <ApiEventFeed events={events} />
      </section>
    </div>
  );
}

function ConnectionBadge({ connected }: { connected: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 ui-edge px-2 py-0.5 text-[10px] font-bold ui-label ${
        connected ? 'border-success text-success' : 'border-line text-ink-soft'
      }`}
    >
      {/* Shape as well as colour: a filled ring when live, a hollow one when not. */}
      <span
        aria-hidden="true"
        className={`h-2 w-2 rounded-full ${
          connected ? 'bg-success' : 'border border-ink-soft bg-transparent'
        }`}
      />
      {connected ? 'Live' : 'Reconnecting'}
    </span>
  );
}

function Stat({
  icon,
  label,
  value,
  unit,
  detail,
  alarming = false,
}: {
  icon: IconDefinition;
  label: string;
  value: string;
  unit: string;
  detail: string;
  alarming?: boolean;
}) {
  return (
    <div
      className={`ui-edge px-3 py-2.5 ${
        alarming ? 'border-danger bg-danger-tint' : 'border-line bg-paper'
      }`}
    >
      <p className="eyebrow flex items-center gap-1.5 text-[10px] text-ink-soft">
        <FontAwesomeIcon icon={icon} className="h-3 w-3" aria-hidden="true" />
        {label}
      </p>
      <p className="font-mono text-2xl font-bold leading-tight tabular-nums text-ink">
        {value}
        <span className="ml-1 text-xs font-bold text-ink-soft">{unit}</span>
      </p>
      <p className="text-[11px] text-ink-soft">{detail}</p>
    </div>
  );
}

/**
 * A live window on what the server is actually doing.
 *
 * <p>Deliberately not a metrics product. It holds a ten-second rolling window and the last few
 * dozen calls, keeps no history, and stores nothing — reload it and it starts again. It answers
 * one question: what is happening right now, while people are booking?
 *
 * <p>The stream carries no bodies, no tokens and no concrete identifiers; see
 * {@code ApiCallEvent} on the server for what is deliberately excluded and why.
 */
export function ApiViewPage() {
  return (
    <div>
      <div className="mb-5">
        <p className="eyebrow text-xs text-ink-soft">Office administration</p>
        <h1 className="ui-title text-ink">API activity</h1>
        <p className="mt-1 max-w-3xl text-sm text-ink-soft">
          Every call the server finishes, as it happens. One dot per request, travelling the lane it
          belongs to. Useful when several people are booking at once — a seat race shows up here as
          a burst on the bookings lane with one success and the rest cleanly rejected.
        </p>
      </div>
      <SectionErrorBoundary
        title="The API view hit a snag"
        message="Something failed while rendering this page — try reloading this section."
      >
        <ApiViewWorkspace />
      </SectionErrorBoundary>
    </div>
  );
}
