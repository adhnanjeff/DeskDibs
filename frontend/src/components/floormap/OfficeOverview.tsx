import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faChair,
  faChartPie,
  faHourglassHalf,
  faUsers,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import type { OfficeStats } from '../../lib/officeStats';

interface OfficeOverviewProps {
  stats: OfficeStats;
  /** Office-local time the no-show release runs, e.g. `11:00`. */
  noShowReleaseTime?: string;
}

/**
 * Today at a glance, above the map.
 *
 * Four numbers, all counted from the same seat map drawn below — so the strip can never
 * disagree with the floor. "Awaiting check-in" is the one that earns its place: those are
 * precisely the seats the no-show release will hand back, which turns an invisible scheduled
 * job into something a person can see coming.
 */
export function OfficeOverview({ stats, noShowReleaseTime = '11:00' }: OfficeOverviewProps) {
  return (
    <section aria-label="Today's office overview" className="mb-4">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Stat
          icon={faUsers}
          label="In office"
          value={stats.peopleInOffice}
          detail="checked in"
          tone="ink"
        />
        <Stat
          icon={faChair}
          label="Available"
          value={stats.availableSeats}
          detail={`of ${stats.bookableSeats} desks`}
          tone="paper"
        />
        <Stat
          icon={faChartPie}
          label="Occupancy"
          value={`${stats.occupancyPercent}%`}
          detail={`${stats.bookedSeats} booked`}
          tone="yellow"
        />
        <Stat
          icon={faHourglassHalf}
          label="Not checked in"
          value={stats.awaitingCheckIn}
          detail={
            stats.awaitingCheckIn > 0 ? `release at ${noShowReleaseTime}` : 'everyone is in'
          }
          tone={stats.awaitingCheckIn > 0 ? 'red' : 'paper'}
        />
      </div>
    </section>
  );
}

type Tone = 'ink' | 'paper' | 'yellow' | 'red';

const TONE_CLASSES: Record<Tone, string> = {
  ink: 'bg-ink text-paper',
  paper: 'bg-paper text-ink',
  yellow: 'bg-bauhaus-yellow text-ink',
  red: 'bg-bauhaus-red text-white',
};

function Stat({
  icon,
  label,
  value,
  detail,
  tone,
}: {
  icon: IconDefinition;
  label: string;
  value: number | string;
  detail: string;
  tone: Tone;
}) {
  return (
    <div className={`border-2 border-ink px-3 py-2.5 shadow-brutal-sm ${TONE_CLASSES[tone]}`}>
      <p className="eyebrow flex items-center gap-1.5 text-[10px] opacity-75">
        <FontAwesomeIcon icon={icon} className="h-3 w-3" aria-hidden="true" />
        {label}
      </p>
      <p className="font-mono text-2xl font-bold leading-tight">{value}</p>
      <p className="text-[11px] font-semibold uppercase tracking-wide opacity-70">{detail}</p>
    </div>
  );
}
