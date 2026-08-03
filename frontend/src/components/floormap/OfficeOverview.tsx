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
          tone="selected"
        />
        <Stat
          icon={faHourglassHalf}
          label="Not checked in"
          value={stats.awaitingCheckIn}
          detail={
            stats.awaitingCheckIn > 0 ? `release at ${noShowReleaseTime}` : 'everyone is in'
          }
          tone={stats.awaitingCheckIn > 0 ? 'danger' : 'paper'}
        />
      </div>
    </section>
  );
}

type Tone = 'ink' | 'paper' | 'selected' | 'danger';

const TONE_CLASSES: Record<Tone, string> = {
  ink: 'bg-ink text-paper',
  paper: 'bg-paper text-ink',
  selected: 'bg-selected text-ink',
  danger: 'bg-danger text-white',
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
    /*
      Sized against the proposal's own type scale, which runs 14px for body and
      12–13px for labels — the 10px and 11px here were below anything in it, and
      these four numbers are the first thing anybody reads on the page.
    */
    <div className={`ui-edge border-line px-3.5 py-3 shadow-[var(--dd-shadow-sm)] ${TONE_CLASSES[tone]}`}>
      <p className="eyebrow flex items-center gap-1.5 text-xs opacity-75">
        <FontAwesomeIcon icon={icon} className="h-3.5 w-3.5" aria-hidden="true" />
        {label}
      </p>
      <p className="font-mono text-3xl font-bold leading-tight">{value}</p>
      <p className="text-[13px] font-semibold ui-label opacity-70">{detail}</p>
    </div>
  );
}
