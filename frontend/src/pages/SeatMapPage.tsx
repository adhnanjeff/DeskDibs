import { useState } from 'react';
import { useSeatMap } from '../hooks/useSeatMap';
import { useSeatMapHorizon } from '../hooks/useSeatMapHorizon';
import { currentOfficeWeek } from '../lib/officeWeek';
import { useOfficeDateRollover } from '../hooks/useOfficeDateRollover';
import { useAuth } from '../auth/AuthContext';
import { SeatMapSkeleton } from '../components/seatmap/SeatMapSkeleton';
import { FloorWorkspace } from '../components/floormap/FloorWorkspace';
import { DateStrip } from '../components/floormap/DateStrip';
import { ErrorFallback } from '../components/ErrorFallback';
import { SectionErrorBoundary } from '../components/SectionErrorBoundary';

function SeatMapContent() {
  const { user } = useAuth();

  // `null` means "whatever the office calls today" — the server decides, and only once the user
  // picks a different day does the client name a date at all.
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [socketLive, setSocketLive] = useState(true);

  const horizon = useSeatMapHorizon();

  // The strip shows one week of the fortnight the server returns, and its first day is what the
  // map opens on. Not the browser's idea of today, and not the server's either: past the same-day
  // cut-off the office stops offering today at all, so the first bookable day is tomorrow and the
  // map has to agree with the strip about that.
  const week = currentOfficeWeek(horizon.data ?? []);
  const defaultDate = week[0]?.date;
  const shownDate = selectedDate ?? defaultDate;

  const { data, isPending, isError, error, refetch } = useSeatMap(shownDate, {
    live: socketLive,
  });

  useOfficeDateRollover(horizon.data?.[0]?.date);

  if (isPending) {
    return <SeatMapSkeleton />;
  }

  if (isError) {
    return (
      <ErrorFallback
        title="Couldn't load the seat map"
        message={error instanceof Error ? error.message : undefined}
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <>
      {week.length > 0 && (
        <DateStrip
          days={week}
          selectedDate={shownDate ?? data.date ?? null}
          onSelect={(date) => setSelectedDate(date)}
        />
      )}
      <FloorWorkspace
        seatMap={data}
        currentUserId={user?.id ?? null}
        onLiveStatusChange={setSocketLive}
      />
    </>
  );
}

export function SeatMapPage() {
  return (
    <SectionErrorBoundary
      title="The seat map hit a snag"
      message="Something failed while rendering the floor. Your booking data is safe — try reloading this section."
    >
      <SeatMapContent />
    </SectionErrorBoundary>
  );
}
