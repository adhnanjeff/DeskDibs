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
  const { data, isPending, isError, error, refetch } = useSeatMap();
  const [report, setReport] = useState<ReservationReport | null>(null);

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
