import { useState } from 'react';
import type { components } from '../api/schema';
import { useSeatMap } from '../hooks/useSeatMap';
import { useAuth } from '../auth/AuthContext';
import { SeatMapSkeleton } from '../components/seatmap/SeatMapSkeleton';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';
import { ReservationWorkspace } from '../components/reservations/ReservationWorkspace';
import { ReservationReportCard } from '../components/reservations/ReservationReportCard';

type ReservationReport = components['schemas']['ReservationReport'];

function ReservationsContent() {
  const { user } = useAuth();
  const [report, setReport] = useState<ReservationReport | null>(null);
  // Null until the manager picks a range, which is also what makes the first fetch ask for no
  // date at all — the server answers with its own today, and the client never names one itself.
  const [range, setRange] = useState<{ from: string; to: string } | null>(null);

  // The office's own today: whatever date the server fills in when asked for none. Read from its
  // own query rather than remembered from the first render, so there is no stale copy to drift.
  const officeToday = useSeatMap().data?.date ?? '';

  // The map has to be the map of the day being held, not of today. Rendering today's floor while
  // the form says next Tuesday shows every desk free and hides the ones already gone, so a
  // manager picks a block against availability that is not the availability they are booking.
  //
  // Undefined while the range is today's, so this shares the cache entry above instead of asking
  // for the same floor a second time under a spelled-out date.
  const dateParam = range && range.from !== officeToday ? range.from : undefined;
  const { data, isPending, isError, error, refetch } = useSeatMap(dateParam);

  if (isPending) return <SeatMapSkeleton />;

  if (isError) {
    return (
      <ErrorFallback
        title="Couldn't load the floor"
        message={error instanceof Error ? error.message : undefined}
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {report && <ReservationReportCard report={report} />}
      <ReservationWorkspace
        seatMap={data}
        currentUserId={user?.id ?? null}
        officeToday={officeToday || (data.date ?? '')}
        startDate={range?.from ?? data.date ?? ''}
        endDate={range?.to ?? data.date ?? ''}
        onChangeRange={(from, to) => setRange({ from, to })}
        onReport={setReport}
      />
    </div>
  );
}

export function ReservationsPage() {
  return (
    <div>
      <div className="mb-4">
        <p className="eyebrow text-xs text-ink/50">Team blocks</p>
        <h1 className="ui-title text-ink">
          Reservations
        </h1>
        <p className="mt-1 max-w-2xl text-sm font-semibold text-ink/60">
          Hold desks for your team ahead of a busy day. Anything already booked is left exactly as
          it is — you&rsquo;ll be told who has it, never have it taken from them.
        </p>
      </div>
      <SectionErrorBoundary
        title="Reservations hit a snag"
        message="Something failed while rendering this page — try reloading this section."
      >
        <ReservationsContent />
      </SectionErrorBoundary>
    </div>
  );
}
